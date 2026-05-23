FROM gradle:8-jdk21 AS builder

WORKDIR /workspace

# Proto files are at /workspace/proto (resolved from repo-root build context)
COPY proto/ /workspace/proto/

COPY backendKotlin/settings.gradle.kts /workspace/backendKotlin/
COPY backendKotlin/build.gradle.kts    /workspace/backendKotlin/
COPY backendKotlin/gradle/             /workspace/backendKotlin/gradle/
COPY backendKotlin/gradlew             /workspace/backendKotlin/gradlew

WORKDIR /workspace/backendKotlin
# Resolve dependencies before copying source (layer-cache friendly)
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY backendKotlin/src/ /workspace/backendKotlin/src/

RUN ./gradlew generateProto --no-daemon
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/backendKotlin/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
