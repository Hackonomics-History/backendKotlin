package com.hackonomics.backendkotlin.news.adapter.out.ai

import com.hackonomics.backendkotlin.ai.v1.ChatStreamRequest
import com.hackonomics.backendkotlin.ai.v1.GenerateNewsRequest
import com.hackonomics.backendkotlin.ai.v1.NewsAiServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.Metadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val GENERATE_NEWS_DEADLINE_SECONDS = 10L

data class NewsGenerateResult(
    val countryCode: String,
    val items: List<Map<String, String>>,
    val generatedAt: Instant?,
    val itemsCount: Int,
)

@Component
class NewsAiGrpcClient(
    @Value("\${ai-service.internal-token}") private val token: String,
    private val channel: ManagedChannel,
) {
    private val stub = NewsAiServiceGrpcKt.NewsAiServiceCoroutineStub(channel)

    private fun meta(): Metadata = Metadata().apply {
        put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token)
    }

    suspend fun generateNewsFull(countryCode: String, force: Boolean): NewsGenerateResult {
        val req = GenerateNewsRequest.newBuilder()
            .setCountryCode(countryCode)
            .setForce(force)
            .setRequestId(UUID.randomUUID().toString())
            .build()

        val resp = stub.withDeadlineAfter(GENERATE_NEWS_DEADLINE_SECONDS, TimeUnit.SECONDS).generateNews(req, meta())

        val generatedAt = if (resp.hasGeneratedAt()) {
            Instant.ofEpochSecond(resp.generatedAt.seconds, resp.generatedAt.nanos.toLong())
        } else null

        return NewsGenerateResult(
            countryCode = resp.countryCode.ifEmpty { countryCode },
            items = resp.newsItemsList.map { mapOf("title" to it.title, "description" to it.description) },
            generatedAt = generatedAt,
            itemsCount = resp.itemsCount,
        )
    }

    fun chatStream(question: String, countryCode: String, userId: String): Flow<String> {
        val req = ChatStreamRequest.newBuilder()
            .setQuestion(question)
            .setCountryCode(countryCode)
            .setUserId(userId)
            .setRequestId(UUID.randomUUID().toString())
            .build()
        return stub.chatStream(req, meta()).map { chunk -> chunk.text }
    }
}
