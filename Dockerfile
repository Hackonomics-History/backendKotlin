FROM gradle:8-jdk21 AS builder

WORKDIR /workspace

# Proto files are at /workspace/proto (resolved from repo-root build context)
COPY proto/ proto/
COPY backendKotlin/settings.gradle.kts backendKotlin/settings.gradle.kts
COPY backendKotlin/build.gradle.kts    backendKotlin/build.gradle.kts
COPY backendKotlin/gradle/             backendKotlin/gradle/
COPY backendKotlin/gradlew             backendKotlin/gradlew

WORKDIR /workspace/backendKotlin
# Resolve dependencies before copying source (layer-cache friendly)
RUN gradle dependencies --no-daemon --quiet || true

WORKDIR /workspace
COPY backendKotlin/src/ backendKotlin/src/

WORKDIR /workspace/backendKotlin
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /workspace/backendKotlin/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
