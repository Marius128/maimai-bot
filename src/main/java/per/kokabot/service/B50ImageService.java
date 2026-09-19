package per.kokabot.service;

import per.kokabot.maimai.model.B50Result;

import java.util.concurrent.CompletableFuture;

public interface B50ImageService {

    CompletableFuture<byte[]> render(B50Result result);
}
