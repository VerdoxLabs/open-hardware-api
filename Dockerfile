FROM eclipse-temurin:21-jdk AS build

ARG VERSION=dev
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.version=$VERSION \
      org.opencontainers.image.revision=$VCS_REF \
      org.opencontainers.image.created=$BUILD_DATE

ENV HOME=/app
WORKDIR $HOME
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="open-hardware-api" \
      org.opencontainers.image.description="Open Hardware API (Spring Boot)" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/derverdox/open-hardware-api (private repo)"

ARG VERSION=dev
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.version=$VERSION \
      org.opencontainers.image.revision=$VCS_REF \
      org.opencontainers.image.created=$BUILD_DATE

RUN adduser -D -u 1000 appuser

WORKDIR /data

RUN mkdir -p /data \
  && chown -R appuser:appuser /data

COPY --chown=appuser:appuser --from=build /app/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

VOLUME ["/data"]

ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
