package com.hackonomics.backendkotlin.news.adapter.`in`.web

import com.hackonomics.backendkotlin.account.application.port.out.AccountRepository
import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import com.hackonomics.backendkotlin.meta.application.service.CountryService
import com.hackonomics.backendkotlin.news.adapter.out.ai.NewsAiGrpcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.UUID

data class ChatRequest(
    val question: String = "",
    val news: List<Map<String, String>> = emptyList(),
)

@RestController
@RequestMapping("/api/news")
class NewsController(
    private val newsAiGrpcClient: NewsAiGrpcClient,
    private val accountRepo: AccountRepository,
    private val countryService: CountryService,
) {
    private val updateIntervalHours = 6L

    @GetMapping("/business-news/")
    fun getBusinessNews(identity: OryIdentity): ResponseEntity<Map<String, Any?>> {
        val countryCode = accountRepo.findByOryId(identity.id)?.countryCode
            ?: return ResponseEntity.ok(emptyNewsResponse())

        val result = runBlocking { newsAiGrpcClient.generateNewsFull(countryCode, false) }
        val countryName = runCatching {
            countryService.getCountry(countryCode)["name"] as? String
        }.getOrNull()

        val nextUpdate = result.generatedAt
            ?.plusSeconds(Duration.ofHours(updateIntervalHours).toSeconds())

        return ResponseEntity.ok(mapOf(
            "news" to result.items,
            "country_code" to result.countryCode,
            "country_name" to countryName,
            "last_updated" to result.generatedAt?.toString(),
            "next_update" to nextUpdate?.toString(),
            "update_interval_hours" to updateIntervalHours,
        ))
    }

    @PostMapping("/business-news/refresh/")
    fun refreshBusinessNews(identity: OryIdentity): ResponseEntity<Map<String, Any?>> {
        val countryCode = accountRepo.findByOryId(identity.id)?.countryCode
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Account or country not found"))

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching { newsAiGrpcClient.generateNewsFull(countryCode, true) }
                .onFailure { log.error("Background news refresh failed for {}", countryCode, it) }
        }

        return ResponseEntity.ok(mapOf(
            "status" to "queued",
            "country_code" to countryCode,
            "task_id" to UUID.randomUUID().toString(),
        ))
    }

    @PostMapping("/chat/stream/", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(@RequestBody req: ChatRequest, identity: OryIdentity): SseEmitter {
        val emitter = SseEmitter(90_000L)
        val countryCode = accountRepo.findByOryId(identity.id)?.countryCode ?: ""

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                newsAiGrpcClient.chatStream(req.question, countryCode, identity.id)
                    .collect { text ->
                        if (text.isNotEmpty()) {
                            emitter.send(SseEmitter.event().data(text))
                        }
                    }
                emitter.send(SseEmitter.event().data("done"))
                emitter.complete()
            } catch (e: Exception) {
                log.error("SSE chat stream error", e)
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    private fun emptyNewsResponse(): Map<String, Any?> = mapOf(
        "news" to emptyList<Any>(),
        "country_code" to null,
        "country_name" to null,
        "last_updated" to null,
        "next_update" to null,
        "update_interval_hours" to updateIntervalHours,
    )

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(NewsController::class.java)
    }
}
