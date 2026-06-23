package com.hackonomics.backendkotlin.config

import com.hackonomics.backendkotlin.calendar.adapter.out.ai.CalendarAiGrpcClient
import com.hackonomics.backendkotlin.news.adapter.out.ai.NewsAiGrpcClient
import io.grpc.ManagedChannel
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fix B [HIGH] — Shared ManagedChannel Spring Bean
 *
 * Both NewsAiGrpcClient and CalendarAiGrpcClient connect to the same AI service
 * (ai-service.grpc.target) but each builds a private ManagedChannel in their class body.
 *
 * gRPC over HTTP/2 multiplexes unlimited concurrent RPCs over a single TCP connection.
 * Two separate channels to the same host means:
 *  - 2 TCP connections (wasted OS file descriptors)
 *  - 2 × TLS handshake overhead (when TLS is enabled)
 *  - 2 × keepalive timers firing
 *  - Confusing @PreDestroy lifecycle (both clients shut down "the channel" independently)
 *
 * Fix: extract ManagedChannel into a @Bean in AiServiceChannelConfig. Both clients
 * receive it via constructor injection. The bean's @PreDestroy handles shutdown once.
 *
 * RED: All tests fail because AiServiceChannelConfig doesn't exist yet and both
 *      clients still build their own channels.
 * GREEN: Pass once AiServiceChannelConfig is created and both clients are refactored.
 */
class AiServiceChannelConfigTest {

    /**
     * A @Configuration class named AiServiceChannelConfig must exist.
     *
     * RED: fails because AiServiceChannelConfig does not exist.
     * GREEN: passes once the class is created.
     */
    @Test
    fun `AiServiceChannelConfig configuration class must exist`() {
        val configClass = runCatching {
            Class.forName("com.hackonomics.backendkotlin.config.AiServiceChannelConfig")
        }.getOrNull()

        assertNotNull(
            configClass,
            "com.hackonomics.backendkotlin.config.AiServiceChannelConfig not found. " +
            "Create a @Configuration class that provides a single @Bean ManagedChannel " +
            "so both NewsAiGrpcClient and CalendarAiGrpcClient can share it. " +
            "Example:\n" +
            "  @Configuration\n" +
            "  class AiServiceChannelConfig(\n" +
            "      @Value(\"\\\${ai-service.grpc.target:localhost:50052}\") private val target: String\n" +
            "  ) : DisposableBean {\n" +
            "      private val channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build()\n" +
            "      @Bean fun aiServiceChannel(): ManagedChannel = channel\n" +
            "      override fun destroy() { channel.shutdown() }\n" +
            "  }"
        )
    }

    /**
     * AiServiceChannelConfig must expose a method that returns ManagedChannel
     * (this becomes the @Bean factory method Spring uses for injection).
     *
     * RED: fails because the class does not exist.
     * GREEN: passes once @Bean fun aiServiceChannel(): ManagedChannel is added.
     */
    @Test
    fun `AiServiceChannelConfig must provide a ManagedChannel bean method`() {
        val configClass = runCatching {
            Class.forName("com.hackonomics.backendkotlin.config.AiServiceChannelConfig").kotlin
        }.getOrNull()

        assertNotNull(configClass, "AiServiceChannelConfig not found — see previous test for fix")

        val channelMethod = configClass.memberFunctions.firstOrNull { fn ->
            fn.returnType.classifier == ManagedChannel::class
        }

        assertNotNull(
            channelMethod,
            "AiServiceChannelConfig must have a @Bean method returning ManagedChannel. " +
            "Found methods: ${configClass.memberFunctions.map { "${it.name}: ${it.returnType}" }}"
        )
    }

    /**
     * NewsAiGrpcClient must accept a ManagedChannel constructor parameter.
     * This is how Spring injects the shared channel bean.
     *
     * RED: fails because the current constructor is (target: String, token: String) with no ManagedChannel.
     * GREEN: passes once the constructor is refactored to accept ManagedChannel.
     */
    @Test
    fun `NewsAiGrpcClient must accept ManagedChannel via constructor injection`() {
        val constructor = NewsAiGrpcClient::class.primaryConstructor
        assertNotNull(constructor, "NewsAiGrpcClient must have a primary constructor")

        val hasChannelParam = constructor.parameters.any { param ->
            param.type.classifier == ManagedChannel::class
        }

        assertTrue(
            hasChannelParam,
            "NewsAiGrpcClient primary constructor must include a ManagedChannel parameter " +
            "so Spring injects the shared ai-service channel bean. " +
            "Current parameters: ${constructor.parameters.map { "${it.name}: ${it.type}" }}. " +
            "Fix: change constructor to accept ManagedChannel instead of building it internally:\n" +
            "  class NewsAiGrpcClient(\n" +
            "      @Value(\"\\\${ai-service.internal-token}\") private val token: String,\n" +
            "      private val channel: ManagedChannel,\n" +
            "  )"
        )
    }

    /**
     * CalendarAiGrpcClient must accept a ManagedChannel constructor parameter.
     *
     * RED: fails because current constructor is (target: String, token: String).
     * GREEN: passes once the constructor accepts ManagedChannel.
     */
    @Test
    fun `CalendarAiGrpcClient must accept ManagedChannel via constructor injection`() {
        val constructor = CalendarAiGrpcClient::class.primaryConstructor
        assertNotNull(constructor, "CalendarAiGrpcClient must have a primary constructor")

        val hasChannelParam = constructor.parameters.any { param ->
            param.type.classifier == ManagedChannel::class
        }

        assertTrue(
            hasChannelParam,
            "CalendarAiGrpcClient primary constructor must include a ManagedChannel parameter " +
            "so Spring injects the shared ai-service channel bean. " +
            "Current parameters: ${constructor.parameters.map { "${it.name}: ${it.type}" }}. " +
            "Fix: change constructor to accept ManagedChannel instead of building it internally."
        )
    }

    /**
     * NewsAiGrpcClient source must not contain an inline ManagedChannelBuilder call.
     * The channel must come from constructor injection, not be built inside the class body.
     *
     * RED: fails because NewsAiGrpcClient currently has
     *      `private val channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build()`
     * GREEN: passes once the channel is injected and the builder call is removed.
     */
    @Test
    fun `NewsAiGrpcClient must not build its own ManagedChannel internally`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/news/adapter/out/ai/NewsAiGrpcClient.kt"
        )
        assertTrue(sourceFile.exists(), "NewsAiGrpcClient.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertFalse(
            source.contains("ManagedChannelBuilder"),
            "NewsAiGrpcClient must not build its own ManagedChannel. " +
            "Found ManagedChannelBuilder in source — the channel must come from Spring injection. " +
            "Fix: remove `private val channel = ManagedChannelBuilder...build()` and " +
            "add `private val channel: ManagedChannel` constructor parameter instead."
        )
    }

    /**
     * CalendarAiGrpcClient source must not contain an inline ManagedChannelBuilder call.
     *
     * RED: fails because CalendarAiGrpcClient currently has
     *      `private val channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build()`
     * GREEN: passes once the channel is injected.
     */
    @Test
    fun `CalendarAiGrpcClient must not build its own ManagedChannel internally`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/calendar/adapter/out/ai/CalendarAiGrpcClient.kt"
        )
        assertTrue(sourceFile.exists(), "CalendarAiGrpcClient.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertFalse(
            source.contains("ManagedChannelBuilder"),
            "CalendarAiGrpcClient must not build its own ManagedChannel. " +
            "Found ManagedChannelBuilder in source — the channel must come from Spring injection. " +
            "Fix: remove `private val channel = ManagedChannelBuilder...build()` and " +
            "add `private val channel: ManagedChannel` constructor parameter instead."
        )
    }
}
