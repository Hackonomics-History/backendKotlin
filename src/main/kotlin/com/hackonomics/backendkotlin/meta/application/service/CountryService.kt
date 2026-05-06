package com.hackonomics.backendkotlin.meta.application.service

import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import com.hackonomics.backendkotlin.meta.adapter.out.external.RestCountriesClient
import org.springframework.stereotype.Service

@Service
class CountryService(private val client: RestCountriesClient) {

    fun getAllCountries(): List<Map<String, Any?>> {
        val raw = client.fetchAll()
        val result = raw.mapNotNull { runCatching { mapCountry(it) }.getOrNull() }
        if (result.isEmpty()) throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        return result
    }

    fun getCountry(code: String): Map<String, Any?> {
        val raw = client.fetchByCode(code)
        return runCatching { mapCountry(raw) }.getOrElse { throw BusinessException(ErrorCode.INVALID_RESPONSE) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapCountry(item: Map<String, Any>): Map<String, Any?> {
        val code = item["cca2"] as String
        val nameMap = item["name"] as Map<String, Any>
        val name = nameMap["common"] as String
        val currencies = item["currencies"] as? Map<String, Any> ?: throw IllegalArgumentException("No currency")
        val currencyCodes = currencies.keys.toList()
        val defaultCurrency = currencyCodes.first()
        val flagsMap = item["flags"] as? Map<String, Any>
        val flag = flagsMap?.get("png") as? String
        return mapOf("code" to code, "name" to name, "currencies" to currencyCodes, "default_currency" to defaultCurrency, "flag" to flag)
    }
}
