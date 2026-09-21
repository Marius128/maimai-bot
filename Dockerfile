FROM maven:3.9.11-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        fontconfig \
        fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system kokabot \
    && useradd --system --gid kokabot --home-dir /app kokabot

WORKDIR /app
COPY --from=builder /workspace/target/*.jar /app/kokabot.jar
RUN mkdir -p /app/data/maimai && chown -R kokabot:kokabot /app

USER kokabot
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Dfile.encoding=UTF-8"

ENTRYPOINT ["java", "-jar", "/app/kokabot.jar"]
