package com.hackonomics.backendkotlin.config

import com.hackonomics.backendkotlin.calendar.adapter.out.ai.CalendarAiGrpcClient
import com.hackonomics.backendkotlin.news.adapter.out.ai.NewsAiGrpcClient
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import jakarta.annotation.PreDestroy
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration tests verifying component interaction for the shared ManagedChannel fix.
 *
 * These tests verify:
 * - Both clients share the same channel instance when wired together
 * - Neither client has @PreDestroy (lifecycle ownership belongs to AiServiceChannelConfig)
 * - AiServiceChannelConfig.destroy() can be called safely
 */
class AiServiceChannelIntegrationTest {

    /**
     * When both clients are instantiated with the same ManagedChannel,
     * the channel object is shared (not copied or wrapped).
     *
     * This verifies the key correctness invariant: gRPC multiplexes over ONE connection.
     */
    @Test
    fun `both clients share the same ManagedChannel instance when wired with shared bean`() {
        val sharedChannel: ManagedChannel = ManagedChannelBuilder
            .forTarget("localhost:50052")
            .usePlaintext()
            .build()

        try {
            val newsClient = NewsAiGrpcClient(token = "test-token", channel = sharedChannel)
            val calendarClient = CalendarAiGrpcClient(token = "test-token", channel = sharedChannel)

            // Both constructors must accept and hold the same reference
            // (structural test: both accept ManagedChannel — the actual sharing is
            // enforced by Spring's singleton bean scope)
            val newsChannelField = NewsAiGrpcClient::class.java.getDeclaredField("channel")
            newsChannelField.isAccessible = true
            val calendarChannelField = CalendarAiGrpcClient::class.java.getDeclaredField("channel")
            calendarChannelField.isAccessible = true

            assertSame(
                sharedChannel,
                newsChannelField.get(newsClient),
                "NewsAiGrpcClient must hold the injected channel instance"
            )
            assertSame(
                sharedChannel,
                calendarChannelField.get(calendarClient),
                "CalendarAiGrpcClient must hold the injected channel instance"
            )
            assertSame(
                newsChannelField.get(newsClient),
                calendarChannelField.get(calendarClient),
                "Both clients must reference the same ManagedChannel object when wired with a shared bean"
            )
        } finally {
            sharedChannel.shutdown()
        }
    }

    /**
     * Neither NewsAiGrpcClient nor CalendarAiGrpcClient should have a @PreDestroy method.
     * Channel lifecycle belongs to AiServiceChannelConfig, not the clients.
     * Having @PreDestroy on both clients would shut the channel down twice.
     */
    @Test
    fun `NewsAiGrpcClient must not have a PreDestroy method`() {
        val preDestroyMethods = NewsAiGrpcClient::class.memberFunctions
            .filter { fn ->
                fn.javaMethod?.isAnnotationPresent(PreDestroy::class.java) == true
            }

        assertTrue(
            preDestroyMethods.isEmpty(),
            "NewsAiGrpcClient must not have @PreDestroy — the channel bean (AiServiceChannelConfig) " +
            "owns shutdown. Having @PreDestroy on the client would shut the shared channel down " +
            "while CalendarAiGrpcClient may still be handling requests. " +
            "Found @PreDestroy methods: ${preDestroyMethods.map { it.name }}"
        )
    }

    /**
     * CalendarAiGrpcClient must not have a @PreDestroy method.
     */
    @Test
    fun `CalendarAiGrpcClient must not have a PreDestroy method`() {
        val preDestroyMethods = CalendarAiGrpcClient::class.memberFunctions
            .filter { fn ->
                fn.javaMethod?.isAnnotationPresent(PreDestroy::class.java) == true
            }

        assertTrue(
            preDestroyMethods.isEmpty(),
            "CalendarAiGrpcClient must not have @PreDestroy — channel lifecycle is owned by " +
            "AiServiceChannelConfig. Found @PreDestroy methods: ${preDestroyMethods.map { it.name }}"
        )
    }

    /**
     * AiServiceChannelConfig.destroy() must call shutdown on the channel.
     * After destroy(), the channel must be shutdown (not just terminated).
     */
    @Test
    fun `AiServiceChannelConfig destroy shuts down the shared channel`() {
        val config = AiServiceChannelConfig(target = "localhost:50052")
        val channel = config.aiServiceChannel()

        assertFalse(channel.isShutdown, "Channel must be open before destroy()")

        config.destroy()

        assertTrue(
            channel.isShutdown,
            "Channel must be shutdown after AiServiceChannelConfig.destroy() is called. " +
            "If not, the channel leaks OS TCP connections on pod shutdown."
        )
    }
}
