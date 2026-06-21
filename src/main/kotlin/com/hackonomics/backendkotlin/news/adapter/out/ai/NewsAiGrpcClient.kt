package com.hackonomics.backendkotlin.news.adapter.out.ai

import com.hackonomics.backendkotlin.ai.v1.ChatStreamRequest
import com.hackonomics.backendkotlin.ai.v1.GenerateNewsRequest
import com.hackonomics.backendkotlin.ai.v1.NewsAiServiceGrpcKt
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger(NewsAiGrpcClient::class.java)

data class NewsGenerateResult(
    val countryCode: String,
    val items: List<Map<String, String>>,
    val generatedAt: Instant?,
    val itemsCount: Int,
)

@Component
class NewsAiGrpcClient(
    @Value("\${ai-service.grpc.target:localhost:50052}") private val target: String,
    @Value("\${ai-service.internal-token}") private val token: String,
) {
    private val channel = ManagedChannelBuilder.forTarget(target)
        .usePlaintext()
        .build()

    private val stub = NewsAiServiceGrpcKt.NewsAiServiceCoroutineStub(channel)

    private fun meta(): Metadata = Metadata().apply {
        put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token)
    }

    suspend fun generateNews(countryCode: String, force: Boolean): List<Pair<String, String>> {
        val req = GenerateNewsRequest.newBuilder()
            .setCountryCode(countryCode)
            .setForce(force)
            .setRequestId(UUID.randomUUID().toString())
            .build()

        val resp = stub.generateNews(req, meta())

        return resp.newsItemsList.map { item ->
            item.title to item.description
        }
    }

    suspend fun generateNewsFull(countryCode: String, force: Boolean): NewsGenerateResult {
        val req = GenerateNewsRequest.newBuilder()
            .setCountryCode(countryCode)
            .setForce(force)
            .setRequestId(UUID.randomUUID().toString())
            .build()

        val resp = stub.generateNews(req, meta())

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

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down NewsAiGrpcClient channel")
        channel.shutdown()
    }
}
