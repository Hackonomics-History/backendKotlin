package com.hackonomics.backendkotlin.config

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * TDD Red Phase — AI gRPC Channel Missing keepAlive and Unary RPC Deadline
 *
 * AiServiceChannelConfig creates a ManagedChannel with only .usePlaintext().build() —
 * no keepAlive settings. In production, cloud load-balancers (AWS NLB, GCP GLBC)
 * silently drop idle TCP connections after 30–90s. Without keepAlive PING frames,
 * the gRPC client cannot detect the dead connection. The next call blocks until the
 * OS TCP timeout (~2 min) fires before returning an error.
 *
 * Additionally, neither NewsAiGrpcClient.generateNewsFull() nor CalendarAiGrpcClient.getAdvice()
 * applies stub.withDeadlineAfter() on their unary RPCs. If the AI service pod is restarting
 * or hanging, the calling Spring thread blocks indefinitely. With a typical Tomcat thread
 * pool of 200 threads and a 30-second AI-service outage, all threads can be exhausted within
 * seconds, making the entire backendKotlin service unresponsive.
 *
 * Note: CentralAuthGrpcClient already has keepAliveTime and withDeadlineAfter configured
 * (as verified by CentralAuthGrpcClientConfigTest). These tests target the AI-service
 * path which lacks the same protections.
 *
 * RED: All three tests fail because the current source has none of the required settings.
 * GREEN: Pass once:
 *   1. AiServiceChannelConfig adds .keepAliveTime(30, SECONDS).keepAliveWithoutCalls(true)
 *   2. NewsAiGrpcClient.generateNewsFull() applies stub.withDeadlineAfter(10, SECONDS)
 *   3. CalendarAiGrpcClient.getAdvice() applies stub.withDeadlineAfter(15, SECONDS)
 */
class AiChannelResilienceConfigTest {

    /**
     * AiServiceChannelConfig must configure keepAliveTime on the shared ManagedChannel.
     *
     * Cloud load-balancers drop idle TCP connections after 30–90 seconds without any RST.
     * gRPC needs to send HTTP/2 PING frames on the keepAlive interval to detect and
     * re-establish dead connections BEFORE the next RPC is attempted.
     *
     * Without keepAlive, the first news generation or calendar advice call after an idle
     * period fails with UNAVAILABLE and the user sees a 500 error.
     *
     * RED: fails because AiServiceChannelConfig only calls .usePlaintext().build()
     *      with no keepAlive configuration.
     * GREEN: passes once .keepAliveTime(30, TimeUnit.SECONDS).keepAliveWithoutCalls(true)
     *        is added to the ManagedChannelBuilder chain in AiServiceChannelConfig.
     */
    @Test
    fun `AiServiceChannelConfig must configure keepAliveTime to detect dead connections before idle period exceeds LB timeout`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/config/AiServiceChannelConfig.kt"
        )
        assertTrue(sourceFile.exists(), "AiServiceChannelConfig.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertTrue(
            source.contains("keepAliveTime"),
            "AiServiceChannelConfig builds the shared ManagedChannel without keepAliveTime. " +
            "Current builder chain: ManagedChannelBuilder.forTarget(target).usePlaintext().build() " +
            "with no keepAlive. After an idle period > ~60 seconds, cloud LBs (AWS NLB, GCP GLBC) " +
            "silently drop the TCP connection. The next gRPC call blocks for the full OS TCP timeout " +
            "(~2 min) before failing with UNAVAILABLE — causing frontend requests to hang. " +
            "Fix: add to AiServiceChannelConfig:\n" +
            "  ManagedChannelBuilder.forTarget(target)\n" +
            "      .usePlaintext()\n" +
            "      .keepAliveTime(30, TimeUnit.SECONDS)\n" +
            "      .keepAliveWithoutCalls(true)\n" +
            "      .build()"
        )
    }

    /**
     * NewsAiGrpcClient.generateNewsFull() must apply withDeadlineAfter() on the stub
     * before the unary RPC call to cap the maximum wait time on AI service responses.
     *
     * Without a deadline, if the AI service pod is restarting, OOM-killed, or hanging,
     * the calling Spring servlet thread blocks indefinitely waiting for a gRPC response.
     * generateNewsFull is on the hot request path of GET /api/news/business-news/.
     * A 10-second AI-service outage × 200-thread pool = full thread exhaustion in seconds.
     *
     * RED: fails because generateNewsFull() calls stub.generateNews(req, meta()) with no
     *      withDeadlineAfter() — the stub has no time bound.
     * GREEN: passes once the call becomes stub.withDeadlineAfter(10, TimeUnit.SECONDS)
     *        .generateNews(req, meta()), mirroring the pattern in CentralAuthGrpcClient.
     */
    @Test
    fun `NewsAiGrpcClient generateNewsFull must apply withDeadlineAfter to prevent indefinite thread blocking`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/news/adapter/out/ai/NewsAiGrpcClient.kt"
        )
        assertTrue(sourceFile.exists(), "NewsAiGrpcClient.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertTrue(
            source.contains("withDeadlineAfter") || source.contains("deadline"),
            "NewsAiGrpcClient.generateNewsFull() calls stub.generateNews(req, meta()) with no " +
            "deadline. If the AI-service pod hangs or is being restarted, the Spring thread " +
            "handling GET /api/news/business-news/ blocks indefinitely. With Tomcat's default " +
            "pool of 200 threads, a 30-second AI outage exhausts all threads and makes the " +
            "entire backendKotlin service unresponsive to every other endpoint. " +
            "Fix: apply a deadline before the RPC call (same pattern as CentralAuthGrpcClient):\n" +
            "  val resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS).generateNews(req, meta())"
        )
    }

    /**
     * CalendarAiGrpcClient.getAdvice() must apply withDeadlineAfter() on the stub
     * before the unary RPC call.
     *
     * getAdvice is on the hot path of POST /api/calendar/advice. Without a deadline,
     * a slow or unresponsive AI-service pod blocks the Spring thread indefinitely,
     * cascading into thread pool exhaustion that affects all endpoints.
     *
     * RED: fails because getAdvice() calls stub.getAdvice(req, meta()) with no
     *      withDeadlineAfter() — same class of bug as NewsAiGrpcClient.
     * GREEN: passes once stub.withDeadlineAfter(15, TimeUnit.SECONDS).getAdvice(req, meta())
     *        is used (15 s is appropriate as Groq calendar analysis takes longer than news fetch).
     */
    @Test
    fun `CalendarAiGrpcClient getAdvice must apply withDeadlineAfter to prevent indefinite thread blocking`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/calendar/adapter/out/ai/CalendarAiGrpcClient.kt"
        )
        assertTrue(sourceFile.exists(), "CalendarAiGrpcClient.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertTrue(
            source.contains("withDeadlineAfter") || source.contains("deadline"),
            "CalendarAiGrpcClient.getAdvice() calls stub.getAdvice(req, meta()) with no " +
            "deadline. If the AI-service Groq call hangs (Groq API outage, network partition), " +
            "the Spring thread handling the calendar advice request blocks indefinitely. " +
            "Calendar advice calls are expensive and long-running; a single stuck request per " +
            "concurrent user quickly exhausts the thread pool. " +
            "Fix: apply a deadline appropriate for Groq's typical response time:\n" +
            "  val resp = stub.withDeadlineAfter(15, TimeUnit.SECONDS).getAdvice(req, meta())"
        )
    }
}
