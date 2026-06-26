# Runtime image: copy pre-built JAR into a minimal JRE container
# Build the JAR first:  mvn clean package -DskipTests
FROM eclipse-temurin:17-jre

LABEL maintainer="AutoTradingSystem"
LABEL description="Futu Stock Monitor - headless stock monitoring service"

WORKDIR /app

COPY target/futu-stock-monitor-*.jar app.jar

RUN mkdir -p /app/logs

ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Duser.timezone=Asia/Shanghai"
ENV OPEND_IP="127.0.0.1"
ENV OPEND_PORT="11111"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
