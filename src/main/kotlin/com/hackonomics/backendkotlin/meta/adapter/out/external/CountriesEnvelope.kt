package com.hackonomics.backendkotlin.meta.adapter.out.external

data class CountriesResponse(
    val data: CountriesData,
)

data class CountriesData(
    val objects: List<CountryV5Response>,
    val meta: CountriesMeta,
)

data class CountriesMeta(
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)
