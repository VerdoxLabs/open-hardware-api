FROM eclipse-temurin:21-jdk AS build

ARG VERSION=dev
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.version=$VERSION \
      org.opencontainers.image.revision=$VCS_REF \
      org.opencontainers.image.created=$BUILD_DATE

ENV HOME=/app
WORKDIR $HOME
COPY .. .

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

WORKDIR /var/lib/open-hardware-api

RUN mkdir -p /var/lib/open-hardware-api \
  && chown -R appuser:appuser /var/lib/open-hardware-api

COPY --chown=appuser:appuser --from=build /app/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

VOLUME ["/var/lib/open-hardware-api"]

ENTRYPOINT ["java","-Dcom.sun.management.jmxremote=true", "-Dcom.sun.management.jmxremote.port=9010","-Dcom.sun.management.jmxremote.local.only=false","-Dcom.sun.management.jmxremote.authenticate=false","-Dcom.sun.management.jmxremote.ssl=false","-Dcom.sun.management.jmxremote.rmi.port=9010","-Djava.rmi.server.hostname=192.168.251.3","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
