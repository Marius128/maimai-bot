package per.kokabot.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OneBotClient implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(OneBotClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ACTION_TIMEOUT = Duration.ofSeconds(15);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final URI websocketUri;
    private final String accessToken;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingActions =
            new ConcurrentHashMap<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final StringBuilder incomingText = new StringBuilder();

    private volatile WebSocket webSocket;
    private volatile HttpClient httpClient;
    private volatile boolean shuttingDown;

    public OneBotClient(
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            @Value("${onebot.websocket-url}") String websocketUrl,
            @Value("${onebot.access-token:}") String accessToken
    ) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.websocketUri = URI.create(websocketUrl);
        this.accessToken = accessToken.trim();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "onebot-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (accessToken.isBlank()) {
            log.warn("ONEBOT_ACCESS_TOKEN 未配置，暂不连接 SnowLuma");
            return;
        }
        connect();
    }

    public boolean isConnected() {
        WebSocket current = webSocket;
        return current != null && !current.isInputClosed() && !current.isOutputClosed();
    }

    public CompletableFuture<JsonNode> callAction(String action, Map<String, ?> params) {
        if (action == null || action.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("OneBot action 不能为空")
            );
        }

        WebSocket current = webSocket;
        if (current == null || current.isOutputClosed()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("OneBot WebSocket 尚未连接")
            );
        }

        String echo = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
        pendingActions.put(echo, responseFuture);

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "action", action,
                    "params", params == null ? Map.of() : params,
                    "echo", echo
            ));
        } catch (Exception error) {
            pendingActions.remove(echo);
            return CompletableFuture.failedFuture(error);
        }

        scheduler.schedule(() -> {
            CompletableFuture<JsonNode> pending = pendingActions.remove(echo);
            if (pending != null) {
                pending.completeExceptionally(
                        new TimeoutException("OneBot action 超时: " + action)
                );
            }
        }, ACTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        current.sendText(payload, true).whenComplete((ignored, error) -> {
            if (error != null) {
                CompletableFuture<JsonNode> pending = pendingActions.remove(echo);
                if (pending != null) {
                    pending.completeExceptionally(error);
                }
            }
        });

        return responseFuture;
    }

    private void connect() {
        if (shuttingDown || !connecting.compareAndSet(false, true)) {
            return;
        }

        log.info("正在连接 OneBot WebSocket: {}", websocketUri);
        httpClient().newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .buildAsync(authenticatedUri(), this)
                .whenComplete((socket, error) -> {
                    connecting.set(false);
                    if (error != null) {
                        logConnectionFailure(error);
                        scheduleReconnect();
                    }
                });
    }

    private URI authenticatedUri() {
        String separator = websocketUri.getQuery() == null ? "?" : "&";
        String encodedToken = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        return URI.create(websocketUri + separator + "access_token=" + encodedToken);
    }

    private HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build();
            }
            return httpClient;
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        reconnectScheduled.set(false);
        log.info("OneBot WebSocket 已连接");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String payload = null;
        synchronized (incomingText) {
            incomingText.append(data);
            if (last) {
                payload = incomingText.toString();
                incomingText.setLength(0);
            }
        }

        try {
            if (payload != null) {
                routePayload(payload);
            }
        } catch (Exception error) {
            log.warn("无法处理 OneBot 数据包: {}", error.getMessage());
        } finally {
            webSocket.request(1);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void routePayload(String payload) throws Exception {
        JsonNode json = objectMapper.readTree(payload);
        JsonNode echoNode = json.get("echo");

        if (echoNode != null && !echoNode.isNull()) {
            CompletableFuture<JsonNode> pending = pendingActions.remove(echoNode.asText());
            if (pending != null) {
                pending.complete(json);
            }
            return;
        }

        eventPublisher.publishEvent(new OneBotEvent(json));
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return webSocket.sendPong(message);
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (this.webSocket == webSocket) {
            this.webSocket = null;
        }
        failPendingActions(new IllegalStateException("OneBot WebSocket 已断开"));
        log.warn("OneBot WebSocket 已断开: status={}, reason={}", statusCode, reason);
        scheduleReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (this.webSocket == webSocket) {
            this.webSocket = null;
        }
        failPendingActions(error);
        log.warn("OneBot WebSocket 异常: {}", error.toString());
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (shuttingDown || accessToken.isBlank()
                || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void failPendingActions(Throwable error) {
        pendingActions.forEach((echo, future) -> {
            if (pendingActions.remove(echo, future)) {
                future.completeExceptionally(error);
            }
        });
    }

    private void logConnectionFailure(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof WebSocketHandshakeException handshakeException) {
                log.warn(
                        "OneBot WebSocket 握手失败: HTTP {}",
                        handshakeException.getResponse().statusCode()
                );
                return;
            }
            cause = cause.getCause();
        }
        log.warn("OneBot WebSocket 连接失败: {}", error.toString());
    }

    @PreDestroy
    public void stop() {
        shuttingDown = true;
        reconnectScheduled.set(false);
        failPendingActions(new IllegalStateException("应用正在关闭"));
        scheduler.shutdownNow();

        WebSocket current = webSocket;
        webSocket = null;
        if (current != null && !current.isOutputClosed()) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "application shutdown");
        }
    }
}
