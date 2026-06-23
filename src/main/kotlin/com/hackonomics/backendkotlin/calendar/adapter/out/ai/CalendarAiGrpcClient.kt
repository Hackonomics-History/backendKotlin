package com.hackonomics.backendkotlin.calendar.adapter.out.ai

import com.hackonomics.backendkotlin.ai.v1.CalendarAdviceRequest
import com.hackonomics.backendkotlin.ai.v1.CalendarAiServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.Metadata
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val GET_ADVICE_DEADLINE_SECONDS = 15L

data class AdviceItem(
    val title: String,
    val description: String,
    val eventIds: List<String>,
    val priority: String,
)

@Component
class CalendarAiGrpcClient(
    @Value("\${ai-service.internal-token}") private val token: String,
    private val channel: ManagedChannel,
) {
    private val stub = CalendarAiServiceGrpcKt.CalendarAiServiceCoroutineStub(channel)

    private fun meta(): Metadata = Metadata().apply {
        put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token)
    }

    suspend fun getAdvice(
        documentText: String,
        eventsText: String,
        countryContext: String,
        userId: String,
    ): List<AdviceItem> {
        val req = CalendarAdviceRequest.newBuilder()
            .setDocumentText(documentText)
            .setEventsText(eventsText)
            .setCountryContext(countryContext)
            .setUserId(userId)
            .build()
        val resp = stub.withDeadlineAfter(GET_ADVICE_DEADLINE_SECONDS, TimeUnit.SECONDS).getAdvice(req, meta())
        return resp.itemsList.map { item ->
            AdviceItem(
                title = item.title,
                description = item.description,
                eventIds = item.eventIdsList,
                priority = item.priority,
            )
        }
    }
}
