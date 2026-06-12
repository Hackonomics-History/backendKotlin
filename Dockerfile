FROM gradle:8-jdk21

WORKDIR /workspace/backendKotlin

COPY proto/ /workspace/proto/

COPY backendKotlin/ .

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

CMD ["./gradlew", "bootRun", "--continuous"]