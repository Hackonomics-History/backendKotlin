package com.hackonomics.backendkotlin.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides an application-scoped CoroutineScope managed by Spring.
 *
 * Fix 2-A: NewsController previously created CoroutineScope(Dispatchers.IO + SupervisorJob())
 * per request. Spring had no knowledge of those scopes, so on pod shutdown, in-flight streams
 * were abruptly killed without calling emitter.completeWithError() — leaving frontends waiting
 * 90s for the SseEmitter timeout.
 *
 * This bean's cancel() in destroy() allows Spring to propagate shutdown to all active coroutines.
 */
@Configuration
class AppCoroutineScopeConfig : DisposableBean {

    private val job = SupervisorJob()

    @Bean
    fun appCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + job)

    override fun destroy() {
        // cancel() (not join, which would need runBlocking and stall Spring's shutdown thread)
        // propagates cancellation so each SseEmitter completes promptly instead of timing out at 90s.
        job.cancel("Application context closing")
    }
}
