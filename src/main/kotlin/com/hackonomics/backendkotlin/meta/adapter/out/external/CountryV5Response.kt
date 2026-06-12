package com.hackonomics.backendkotlin.meta.adapter.out.external

import com.fasterxml.jackson.annotation.JsonProperty

data class CountryV5Response(
    val codes: CountryCodes,
    val names: CountryNames,
    val currencies: List<CountryCurrency>,
    val flag: CountryFlag?,
)

data class CountryCodes(
    @JsonProperty("alpha_2") val alpha2: String,
)

data class CountryNames(
    val common: String,
)

data class CountryCurrency(
    val code: String,
)

data class CountryFlag(
    @JsonProperty("url_png") val urlPng: String?,
)
