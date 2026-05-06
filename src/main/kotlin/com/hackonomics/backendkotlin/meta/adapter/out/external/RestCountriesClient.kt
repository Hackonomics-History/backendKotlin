package com.hackonomics.backendkotlin.meta.adapter.out.external

import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class RestCountriesClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://restcountries.com/v3.1")

    fun fetchAll(): List<Map<String, Any>> {
        return try {
            @Suppress("UNCHECKED_CAST")
            client.get()
                .uri("/all?fields=cca2,name,currencies,flags")
                .retrieve()
                .body(List::class.java) as? List<Map<String, Any>> ?: emptyList()
        } catch (ex: Exception) {
            log.error("RestCountries fetchAll failed: {}", ex.message)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        }
    }

    fun fetchByCode(code: String): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val result = client.get()
                .uri("/alpha/{code}?fields=cca2,name,currencies,flags", code)
                .retrieve()
                .body(Any::class.java)
            when (result) {
                is Map<*, *> -> result as Map<String, Any>
                is List<*> -> (result.firstOrNull() as? Map<String, Any>)
                    ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
                else -> throw BusinessException(ErrorCode.DATA_NOT_FOUND)
            }
        } catch (ex: BusinessException) {
            throw ex
        } catch (ex: Exception) {
            log.error("RestCountries fetchByCode({}) failed: {}", code, ex.message)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        }
    }
}
