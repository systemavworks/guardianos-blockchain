FROM eclipse-temurin:17-jre-alpine

# Instalar Python + Slither para SmartContractScanner (Sprint 2)
USER root
RUN apk add --no-cache python3 py3-pip py3-setuptools && \
    pip3 install --break-system-packages slither-analyzer && \
    apk del py3-setuptools

WORKDIR /app
COPY build/libs/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
