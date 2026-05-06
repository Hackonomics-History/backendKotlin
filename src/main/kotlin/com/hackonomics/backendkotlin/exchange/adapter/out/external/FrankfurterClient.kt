package com.hackonomics.backendkotlin.exchange.adapter.out.external

import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDate

@Component
class FrankfurterClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://api.frankfurter.app")

    fun getLatestRate(base: String, target: String): Double {
        return try {
            val response = client.get()
                .uri("/latest?from={base}&to={target}", base, target)
                .retrieve()
                .body(Map::class.java) ?: return 0.0
            @Suppress("UNCHECKED_CAST")
            val rates = response["rates"] as? Map<String, Any> ?: return 0.0
            (rates[target] as? Number)?.toDouble() ?: 0.0
        } catch (ex: RestClientException) {
            log.warn("Exchange API request failed: {}", ex.message)
            0.0
        }
    }

    fun getHistoricalRates(start: LocalDate, end: LocalDate, base: String, target: String): Map<String, Map<String, Double>> {
        return try {
            val response = client.get()
                .uri("/{start}..{end}?from={base}&to={target}", start, end, base, target)
                .retrieve()
                .body(Map::class.java) ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
            @Suppress("UNCHECKED_CAST")
            response["rates"] as? Map<String, Map<String, Double>>
                ?: throw BusinessException(ErrorCode.INVALID_RESPONSE)
        } catch (ex: BusinessException) {
            throw ex
        } catch (ex: Exception) {
            log.error("Frankfurter historical request failed: {}", ex.message)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        }
    }
}
