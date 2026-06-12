package com.hackonomics.backendkotlin.calendar.adapter.`in`.web

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.hackonomics.backendkotlin.account.application.port.out.AccountRepository
import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import com.hackonomics.backendkotlin.calendar.adapter.out.ai.CalendarAiGrpcClient
import com.hackonomics.backendkotlin.calendar.adapter.out.api.FastApiCalendarClient
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = LoggerFactory.getLogger(CalendarController::class.java)

@RestController
@RequestMapping("/api/calendar")
class CalendarController(
    private val fastApiCalendarClient: FastApiCalendarClient,
    private val calendarAiGrpcClient: CalendarAiGrpcClient,
    private val accountRepo: AccountRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${ai-service.internal-token}") private val internalToken: String,
) {
    private val listMapTypeRef = object : TypeReference<List<Map<String, Any>>>() {}

    // ── UserCalendar ─────────────────────────────────────────────────────────

    @PostMapping("/init/")
    fun initCalendar(identity: OryIdentity): ResponseEntity<String> =
        fastApiCalendarClient.post("/api/calendar/init/", null, internalToken, identity.id)
            .asJsonResponse()

    @GetMapping("/me/")
    fun getMyCalendar(identity: OryIdentity): ResponseEntity<String> =
        fastApiCalendarClient.get("/api/calendar/me/", internalToken, identity.id).asJsonResponse()

    // ── Categories ───────────────────────────────────────────────────────────

    @PostMapping("/categories/")
    fun createCategory(
        @RequestBody(required = false) body: Map<String, Any>?,
        identity: OryIdentity,
    ): ResponseEntity<String> =
        fastApiCalendarClient.post("/api/calendar/categories/", body?.toJson(), internalToken, identity.id)
            .asJsonResponse()

    @GetMapping("/categories/")
    fun listCategories(identity: OryIdentity): ResponseEntity<String> =
        fastApiCalendarClient.get("/api/calendar/categories/", internalToken, identity.id).asJsonResponse()

    @DeleteMapping("/categories/{id}/")
    fun deleteCategory(
        @PathVariable id: String,
        identity: OryIdentity,
    ): ResponseEntity<String> =
        fastApiCalendarClient.delete("/api/calendar/categories/$id/", internalToken, identity.id)
            .asNoContentResponse()

    // ── Events ───────────────────────────────────────────────────────────────

    @PostMapping("/events/")
    fun createEvent(
        @RequestBody(required = false) body: Map<String, Any>?,
        identity: OryIdentity,
    ): ResponseEntity<String> =
        fastApiCalendarClient.post("/api/calendar/events/", body?.toJson(), internalToken, identity.id)
            .asJsonResponse()

    @GetMapping("/events/")
    fun listEvents(identity: OryIdentity): ResponseEntity<String> =
        fastApiCalendarClient.get("/api/calendar/events/", internalToken, identity.id).asJsonResponse()

    @PutMapping("/events/{id}/")
    fun updateEvent(
        @PathVariable id: String,
        @RequestBody(required = false) body: Map<String, Any>?,
        identity: OryIdentity,
    ): ResponseEntity<String> =
        fastApiCalendarClient.put("/api/calendar/events/$id/", body?.toJson(), internalToken, identity.id)
            .asJsonResponse()

    @DeleteMapping("/events/{id}/")
    fun deleteEvent(
        @PathVariable id: String,
        identity: OryIdentity,
    ): ResponseEntity<String> =
        fastApiCalendarClient.delete("/api/calendar/events/$id/", internalToken, identity.id)
            .asNoContentResponse()

    // ── AI Advisor ────────────────────────────────────────────────────────────

    @PostMapping("/advice/")
    fun getAdvice(
        @RequestBody body: Map<String, String>,
        identity: OryIdentity,
    ): ResponseEntity<Map<String, Any?>> {
        val documentText = body["document_text"] ?: ""

        val eventsJson = fastApiCalendarClient.get("/api/calendar/events/", internalToken, identity.id)
        val eventsText = buildEventsText(eventsJson.body)

        val account = accountRepo.findByOryId(identity.id)
        val countryContext = account?.let {
            listOfNotNull(it.countryCode, it.currency?.let { c -> "($c)" }).joinToString(" ")
        } ?: ""

        return try {
            val adviceItems = runBlocking {
                calendarAiGrpcClient.getAdvice(documentText, eventsText, countryContext, identity.id)
            }
            val responseItems = adviceItems.map { item ->
                mapOf(
                    "title" to item.title,
                    "description" to item.description,
                    "event_ids" to item.eventIds,
                    "priority" to item.priority.uppercase(),
                )
            }
            ResponseEntity.ok(mapOf("advice" to responseItems))
        } catch (e: Exception) {
            log.error("Calendar advice gRPC error", e)
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("error" to "AI advisor is currently unavailable. Please try again."))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Map<String, Any>.toJson(): String = objectMapper.writeValueAsString(this)

    private fun buildEventsText(eventsJson: String?): String {
        if (eventsJson.isNullOrBlank()) return "No events"
        return runCatching {
            val events = objectMapper.readValue(eventsJson, listMapTypeRef)
            if (events.isEmpty()) {
                "No events"
            } else {
                events.joinToString("\n") { e ->
                    "- EVENT_ID: ${e["id"]} | TITLE: ${e["title"]} | START: ${e["start_at"]}"
                }
            }
        }.getOrDefault("No events")
    }

    private fun ResponseEntity<String>.asJsonResponse(): ResponseEntity<String> =
        ResponseEntity.status(statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)

    private fun ResponseEntity<String>.asNoContentResponse(): ResponseEntity<String> =
        if (statusCode == HttpStatus.NO_CONTENT || statusCode.is2xxSuccessful) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(statusCode).body(body)
        }
}
