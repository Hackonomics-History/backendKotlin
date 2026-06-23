package com.hackonomics.backendkotlin.config

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * TDD Red Phase — gRPC Channel Shutdown Ordering & CancellationException Swallowing
 *
 * Two shutdown-time defects in the current code:
 *
 * Defect A — Channel closes before SSE coroutines drain:
 *   AiServiceChannelConfig.destroy() calls channel.shutdown() with no awaitTermination().
 *   Spring may destroy the channel bean before AppCoroutineScopeConfig cancels active
 *   coroutines. Any in-flight gRPC call (SSE chatStream, background refresh) then gets
 *   UNAVAILABLE from the already-torn-down channel, surfacing as a noisy error in logs
 *   instead of a clean graceful termination.
 *
 * Defect B — runCatching / catch(Exception) swallows CancellationException:
 *   NewsController.chatStream uses catch(e: Exception) which also catches CancellationException
 *   (a subtype of RuntimeException / IllegalStateException). On pod shutdown, every active
 *   SSE stream logs a spurious "SSE chat stream error" entry and calls emitter.completeWithError().
 *
 *   NewsController.refreshBusinessNews uses runCatching { ... }.onFailure { log.error(...) }.
 *   runCatching captures ALL Throwable, so each background refresh cancelled at shutdown
 *   generates a "Background news refresh failed" ERROR — a false alarm on every deploy.
 *
 * RED: All three tests fail because the current code has none of the required fixes.
 * GREEN: Pass once:
 *   A — channel.awaitTermination(5, TimeUnit.SECONDS) is added to AiServiceChannelConfig.destroy()
 *   B — catch(e: CancellationException) { throw e } is added before the generic catch in chatStream
 *       and onFailure re-throws CancellationException in refreshBusinessNews
 */
class GrpcChannelCoroutineLifecycleTest {

    /**
     * AiServiceChannelConfig.destroy() must call awaitTermination() after channel.shutdown()
     * to allow in-flight RPCs to complete before the TCP connection is torn down.
     *
     * Without awaitTermination(), Spring's bean destruction sequence can destroy the gRPC channel
     * while SSE or background-refresh coroutines are still mid-call, causing UNAVAILABLE errors
     * that appear as real failures in logs and alerts.
     *
     * RED: fails because destroy() only calls channel.shutdown() with no awaitTermination().
     * GREEN: passes once destroy() adds channel.awaitTermination(5, TimeUnit.SECONDS).
     */
    @Test
    fun `AiServiceChannelConfig destroy must call awaitTermination to drain in-flight RPCs before TCP teardown`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/config/AiServiceChannelConfig.kt"
        )
        assertTrue(sourceFile.exists(), "AiServiceChannelConfig.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertTrue(
            source.contains("awaitTermination"),
            "AiServiceChannelConfig.destroy() calls channel.shutdown() but not awaitTermination(). " +
            "If Spring destroys this bean while an SSE stream or background refresh is in-flight, " +
            "gRPC immediately terminates the HTTP/2 stream and the coroutine receives a " +
            "StatusRuntimeException(UNAVAILABLE), which logs as a real error instead of a clean shutdown. " +
            "Fix: add channel.awaitTermination(5, TimeUnit.SECONDS) inside destroy() after shutdown():\n" +
            "  override fun destroy() {\n" +
            "      channel.shutdown()\n" +
            "      channel.awaitTermination(5, TimeUnit.SECONDS)  // drain in-flight calls\n" +
            "  }"
        )
    }

    /**
     * NewsController.chatStream must NOT catch CancellationException with a generic
     * catch(e: Exception) block, because CancellationException is a subtype of Exception.
     *
     * When appScope is cancelled (pod shutdown), every active SSE stream coroutine throws
     * CancellationException. The current catch(e: Exception) intercepts it, calls
     * log.error("SSE chat stream error", e), and passes the cancellation as an error to
     * emitter.completeWithError() — polluting alerting dashboards with false alarms.
     *
     * RED: fails because chatStream has `catch (e: Exception)` with no CancellationException guard.
     * GREEN: passes once `catch (e: CancellationException) { throw e }` is added before the
     *        generic catch, or `if (e is CancellationException) throw e` is added inside it.
     */
    @Test
    fun `chatStream catch block must re-throw CancellationException to prevent spurious shutdown error logs`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/news/adapter/in/web/NewsController.kt"
        )
        assertTrue(sourceFile.exists(), "NewsController.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertTrue(
            source.contains("CancellationException") || source.contains("coroutineContext.isActive"),
            "NewsController.chatStream uses `catch (e: Exception)` which also catches " +
            "CancellationException (RuntimeException → IllegalStateException → CancellationException). " +
            "On pod shutdown, every active SSE stream coroutine is cancelled and logs a spurious " +
            "'SSE chat stream error' ERROR entry, causing false-positive alerts on every deployment. " +
            "Fix: add a guard before or inside the catch block:\n" +
            "  } catch (e: CancellationException) {\n" +
            "      throw e  // let the coroutine framework handle normal cancellation\n" +
            "  } catch (e: Exception) {\n" +
            "      log.error(\"SSE chat stream error\", e)\n" +
            "      emitter.completeWithError(e)\n" +
            "  }"
        )
    }

    /**
     * NewsController.refreshBusinessNews uses runCatching { ... }.onFailure { log.error(...) }.
     * runCatching captures ALL Throwable, including CancellationException.
     * When appScope.launch{} coroutines are cancelled at pod shutdown, onFailure fires and
     * logs a "Background news refresh failed" ERROR entry — a false alarm on every deploy.
     *
     * RED: fails because the source has no CancellationException check inside the onFailure lambda.
     * GREEN: passes once onFailure re-throws CancellationException:
     *        .onFailure { e -> if (e is CancellationException) throw e; log.error(...) }
     */
    @Test
    fun `refreshBusinessNews runCatching onFailure must not log CancellationException as an error`() {
        val sourceFile = File(
            "src/main/kotlin/com/hackonomics/backendkotlin/news/adapter/in/web/NewsController.kt"
        )
        assertTrue(sourceFile.exists(), "NewsController.kt not found at: ${sourceFile.absolutePath}")

        val source = sourceFile.readText()

        assertTrue(
            source.contains("CancellationException") || source.contains("coroutineContext.isActive"),
            "NewsController.refreshBusinessNews uses `runCatching { ... }.onFailure { log.error(...) }`. " +
            "runCatching captures ALL Throwable — including CancellationException — so every " +
            "background news refresh cancelled during pod shutdown logs a false 'Background news " +
            "refresh failed' ERROR entry. With 10 simultaneous users that is 10 ERROR logs per deploy. " +
            "Fix: guard inside onFailure:\n" +
            "  .onFailure { e ->\n" +
            "      if (e is CancellationException) throw e\n" +
            "      log.error(\"Background news refresh failed for {}\", countryCode, e)\n" +
            "  }"
        )
    }
}
