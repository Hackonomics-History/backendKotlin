package com.hackonomics.backendkotlin.calendar.adapter.out.ai

import com.hackonomics.backendkotlin.ai.v1.CalendarAdviceRequest
import com.hackonomics.backendkotlin.ai.v1.CalendarAiServiceGrpcKt
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(CalendarAiGrpcClient::class.java)

data class AdviceItem(
    val title: String,
    val description: String,
    val eventIds: List<String>,
    val priority: String,
)

@Component
class CalendarAiGrpcClient(
    @Value("\${ai-service.grpc.target:localhost:50052}") private val target: String,
    @Value("\${ai-service.internal-token:internal-token}") private val token: String,
) {
    private val channel = ManagedChannelBuilder.forTarget(target)
        .usePlaintext()
        .build()

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
        val resp = stub.getAdvice(req, meta())
        return resp.itemsList.map { item ->
            AdviceItem(
                title = item.title,
                description = item.description,
                eventIds = item.eventIdsList,
                priority = item.priority,
            )
        }
    }

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down CalendarAiGrpcClient channel")
        channel.shutdown()
    }
}
