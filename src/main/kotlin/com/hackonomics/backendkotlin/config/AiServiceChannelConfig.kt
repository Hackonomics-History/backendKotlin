package com.hackonomics.backendkotlin.config

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

private const val KEEPALIVE_INTERVAL_SECONDS = 30L
private const val CHANNEL_SHUTDOWN_GRACE_SECONDS = 5L

// Provides a single shared gRPC channel to the AI service.
// Both NewsAiGrpcClient and CalendarAiGrpcClient inject this bean so that only
// one HTTP/2 connection is opened to ai-service (gRPC multiplexes all RPCs over it).
@Configuration
class AiServiceChannelConfig(
    @Value("\${ai-service.grpc.target:localhost:50052}") private val target: String,
) : DisposableBean {

    private val channel: ManagedChannel = ManagedChannelBuilder.forTarget(target)
        .usePlaintext()
        .keepAliveTime(KEEPALIVE_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .keepAliveWithoutCalls(true)
        .build()

    @Bean
    fun aiServiceChannel(): ManagedChannel = channel

    override fun destroy() {
        channel.shutdown()
        if (!channel.awaitTermination(CHANNEL_SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
            channel.shutdownNow()
            channel.awaitTermination(1L, TimeUnit.SECONDS)
        }
    }
}
