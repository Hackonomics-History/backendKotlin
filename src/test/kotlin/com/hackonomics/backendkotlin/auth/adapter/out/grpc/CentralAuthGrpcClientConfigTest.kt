package com.hackonomics.backendkotlin.auth.adapter.out.grpc

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import java.io.File
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD Red Phase — Central Auth gRPC Client Configuration Bugs
 *
 * Bug 1 [CRITICAL]: CentralAuthGrpcClient binds its gRPC target to `ai-service.grpc.target`
 *   instead of `central-auth.grpc.target`. In staging/prod the two services live on different
 *   hosts/ports, so all auth RPC calls are silently routed to the AI service → 100% auth failure.
 *
 * Bug 2 [HIGH]: The ManagedChannel is built without keepAliveTime or per-call deadline.
 *   - Missing keepAlive: cloud LBs (AWS NLB, GCP GLBC) drop idle TCP connections after 30–90s;
 *     the next gRPC call on a dead channel hangs until the OS TCP timeout (~2 min).
 *   - Missing deadline: a hung Central-Auth pod blocks the calling Spring thread indefinitely,
 *     cascading into thread pool exhaustion across all API endpoints.
 */
class CentralAuthGrpcClientConfigTest {

    /**
     * CentralAuthGrpcClient must read its gRPC target address from `central-auth.grpc.target`,
     * NOT from `ai-service.grpc.target`.
     *
     * RED: fails because the constructor parameter is currently annotated with
     *      @Value("\${ai-service.grpc.target:localhost:50051}") — the wrong property key.
     *      In production, ai-service.grpc.target points to port 50052 (AI service), not
     *      port 50051 (Central Auth), causing all auth calls to fail with UNIMPLEMENTED.
     *
     * GREEN: passes once the annotation is changed to
     *        @Value("\${central-auth.grpc.target:localhost:50051}").
     */
    @Test
    fun `centralAuthGrpcClient target must be bound to central-auth grpc target property not ai-service`() {
        val constructor = CentralAuthGrpcClient::class.primaryConstructor
        assertNotNull(constructor, "CentralAuthGrpcClient must have a primary constructor")

        val targetParam = constructor.parameters.firstOrNull { it.name == "target" }
        assertNotNull(
            targetParam,
            "CentralAuthGrpcClient primary constructor must have a 'target' parameter. " +
            "Found parameters: ${constructor.parameters.map { "${it.name}: ${it.type}" }}"
        )

        val valueAnnotation = targetParam.annotations
            .filterIsInstance<Value>()
            .firstOrNull()

        assertNotNull(
            valueAnnotation,
            "CentralAuthGrpcClient.target must be annotated with @Value so Spring resolves " +
            "the gRPC address from application properties. " +
            "Parameter '${targetParam.name}' has no @Value annotation. Found: ${targetParam.annotations}"
        )

        assertTrue(
            valueAnnotation.value.contains("central-auth.grpc.target"),
            "CentralAuthGrpcClient.target is bound to '${valueAnnotation.value}' " +
            "but MUST use '\${central-auth.grpc.target}'. " +
            "Using 'ai-service.grpc.target' silently misroutes Central-Auth gRPC traffic " +
            "to the AI service pod (different host and port in any non-local environment). " +
            "Fix: change @Value(\"\${ai-service.grpc.target:localhost:50051}\") to " +
            "@Value(\"\${central-auth.grpc.target:localhost:50051}\") in CentralAuthGrpcClient."
        )
    }

    /**
     * CentralAuthGrpcClient must configure keepAliveTime on the ManagedChannel
     * and set a per-call deadline via withDeadlineAfter on the stub.
     *
     * Missing keepAlive: cloud LBs drop idle TCP connections. The gRPC client has no
     * way to detect a silently-dropped connection without sending periodic PING frames.
     * The next call on a dead channel hangs for the OS TCP timeout (~2 min) before failing.
     *
     * Missing deadline: without withDeadlineAfter(), a hanging Central-Auth call during a
     * pod restart blocks the Spring servlet thread indefinitely. With typical thread pools
     * of 200 threads, a 30-second Central-Auth outage exhausts all threads in 30s ÷ TTL.
     *
     * RED: fails because the channel builder chain currently ends at .usePlaintext().build()
     *      with no keepAlive or deadline configuration.
     * GREEN: passes once the channel includes keepAliveTime and stubs use withDeadlineAfter.
     */
    @Test
    fun `centralAuthGrpcClient channel must configure keepalive and deadline for production stability`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/auth/adapter/out/grpc/CentralAuthGrpcClient.kt"
        )
        assertTrue(
            sourceFile.exists(),
            "CentralAuthGrpcClient.kt not found at: ${sourceFile.absolutePath}. " +
            "Ensure this test is run from the backendKotlin/ module root (Gradle default)."
        )

        val source = sourceFile.readText()

        assertTrue(
            source.contains("keepAliveTime") || source.contains("keepAlive("),
            "CentralAuthGrpcClient ManagedChannel must set keepAliveTime to prevent silent " +
            "connection drops by cloud load-balancers. " +
            "Current ManagedChannelBuilder chain only calls .usePlaintext().build() with no " +
            "keepalive configuration — idle channels are dropped after 30–90s, causing the next " +
            "auth call to hang for ~2 minutes before the OS TCP timeout fires. " +
            "Fix: add .keepAliveTime(30, TimeUnit.SECONDS).keepAliveWithoutCalls(true) " +
            "to the ManagedChannelBuilder chain in CentralAuthGrpcClient."
        )

        assertTrue(
            source.contains("withDeadlineAfter") || source.contains("deadline"),
            "CentralAuthGrpcClient stubs must set a per-call deadline with withDeadlineAfter(). " +
            "Without a deadline, a slow or unresponsive Central-Auth pod (e.g., during rolling " +
            "restarts) blocks the calling Spring thread indefinitely, leading to thread pool " +
            "exhaustion across all API endpoints within seconds. " +
            "Fix: apply stub.withDeadlineAfter(5, TimeUnit.SECONDS) before each RPC call, " +
            "or use a channel-level default deadline via NettyChannelBuilder."
        )
    }
}
