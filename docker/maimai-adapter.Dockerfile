# maimai.py v1.5.2rc2 requires GLIBC 2.38 or newer.
# Ubuntu 24.04 provides GLIBC 2.39, while Debian Bookworm only provides 2.36.
FROM ubuntu:24.04

ARG MAIMAI_ADAPTER_VERSION=v1.5.2rc2
ARG MAIMAI_ADAPTER_URL=https://github.com/TrueRou/maimai.py/releases/download/${MAIMAI_ADAPTER_VERSION}/maimai.py-linux-amd64

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && curl -fL "${MAIMAI_ADAPTER_URL}" -o /usr/local/bin/maimai-adapter \
    && chmod 0755 /usr/local/bin/maimai-adapter \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home maimai

USER maimai
EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8082/ >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/maimai-adapter"]
CMD ["--host", "0.0.0.0", "--port", "8082"]
