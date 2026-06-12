package com.hackonomics.backendkotlin.meta.application.service

import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import com.hackonomics.backendkotlin.meta.adapter.out.external.CountryV5Response
import com.hackonomics.backendkotlin.meta.adapter.out.external.RestCountriesClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CountryService(private val client: RestCountriesClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getAllCountries(): List<Map<String, Any?>> {
        val raw = client.fetchAll()
        log.info("CountryService raw count from client: {}", raw.size)
        val result = raw.mapNotNull { runCatching { mapCountry(it) }.getOrNull() }
        log.info("CountryService mapped count (after mapNotNull): {}", result.size)
        if (result.isEmpty()) throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        return result
    }

    fun getCountry(code: String): Map<String, Any?> {
        val raw = client.fetchByCode(code)
        return runCatching { mapCountry(raw) }.getOrElse { throw BusinessException(ErrorCode.INVALID_RESPONSE) }
    }

    private fun mapCountry(item: CountryV5Response): Map<String, Any?> {
        val currencyCodes = item.currencies.map { it.code }
        val defaultCurrency = currencyCodes.firstOrNull() ?: throw IllegalArgumentException("No currency")
        return mapOf(
            "code" to item.codes.alpha2,
            "name" to item.names.common,
            "currencies" to currencyCodes,
            "default_currency" to defaultCurrency,
            "flag" to item.flag?.urlPng,
        )
    }
}
