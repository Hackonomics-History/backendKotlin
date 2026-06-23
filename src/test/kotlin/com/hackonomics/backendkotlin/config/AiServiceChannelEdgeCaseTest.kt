package com.hackonomics.backendkotlin.config

import io.grpc.ManagedChannel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Edge case and boundary tests for the shared ManagedChannel configuration.
 */
class AiServiceChannelEdgeCaseTest {

    /**
     * AiServiceChannelConfig must bind its target from @Value so that different
     * environments (dev/staging/prod) can override ai-service.grpc.target.
     *
     * Verifies the constructor parameter carries @Value annotation with the right property key.
     */
    @Test
    fun `AiServiceChannelConfig target must be bound to ai-service grpc target property`() {
        val constructor = AiServiceChannelConfig::class.primaryConstructor
        assertNotNull(constructor, "AiServiceChannelConfig must have a primary constructor")

        val targetParam = constructor.parameters.firstOrNull { it.name == "target" }
        assertNotNull(
            targetParam,
            "AiServiceChannelConfig must have a 'target' constructor parameter. " +
            "Found: ${constructor.parameters.map { it.name }}"
        )

        val valueAnnotation = targetParam.annotations.filterIsInstance<Value>().firstOrNull()
        assertNotNull(
            valueAnnotation,
            "AiServiceChannelConfig.target must be annotated with @Value to allow " +
            "environment-specific override of the AI service address."
        )

        assertTrue(
            valueAnnotation.value.contains("ai-service.grpc.target"),
            "AiServiceChannelConfig.target @Value must reference 'ai-service.grpc.target'. " +
            "Got: '${valueAnnotation.value}'"
        )
    }

    /**
     * AiServiceChannelConfig must implement DisposableBean (or have @PreDestroy)
     * so Spring calls destroy() during context shutdown.
     * Without this, the TCP connection leaks when the pod stops.
     */
    @Test
    fun `AiServiceChannelConfig must implement DisposableBean for proper Spring lifecycle`() {
        val implementsDisposable = AiServiceChannelConfig::class.java.interfaces
            .any { it.name == "org.springframework.beans.factory.DisposableBean" }

        assertTrue(
            implementsDisposable,
            "AiServiceChannelConfig must implement DisposableBean so Spring calls destroy() " +
            "during application context shutdown. Without this, the ManagedChannel TCP connection " +
            "leaks on pod shutdown (kept alive by the OS until the 4-minute FIN_WAIT_2 timeout)."
        )
    }

    /**
     * The aiServiceChannel() @Bean must return the same object on every call
     * (the channel is a field, not recreated on each call).
     *
     * Note: Spring's @Configuration proxy enforces singleton semantics for @Bean methods,
     * but the channel being a field is an extra guarantee that matters even without the proxy.
     */
    @Test
    fun `aiServiceChannel bean method must return the same channel instance on repeated calls`() {
        val config = AiServiceChannelConfig(target = "localhost:50052")
        val channel1: ManagedChannel = config.aiServiceChannel()
        val channel2: ManagedChannel = config.aiServiceChannel()

        assertTrue(
            channel1 === channel2,
            "aiServiceChannel() must return the same ManagedChannel instance on every call. " +
            "Creating a new channel per call would defeat the purpose of sharing and leave " +
            "orphaned channels that are never shut down."
        )

        channel1.shutdown()
    }
}
