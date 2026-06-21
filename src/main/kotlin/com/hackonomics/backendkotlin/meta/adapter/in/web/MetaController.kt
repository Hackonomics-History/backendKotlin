package com.hackonomics.backendkotlin.meta.adapter.`in`.web

import com.hackonomics.backendkotlin.meta.application.service.CountryService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/meta")
class MetaController(private val service: CountryService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/countries/")
    fun listCountries(): ResponseEntity<List<Map<String, Any?>>> {
        val countries = service.getAllCountries()
        log.info("MetaController /countries/ response size: {}", countries.size)
        return ResponseEntity.ok(countries)
    }

    @GetMapping("/countries/{code}/")
    fun getCountry(@PathVariable code: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(service.getCountry(code.uppercase()))
}
