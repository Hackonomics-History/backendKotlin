package com.hackonomics.backendkotlin.meta.adapter.`in`.web

import com.hackonomics.backendkotlin.meta.application.service.CountryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/meta")
class MetaController(private val service: CountryService) {

    @GetMapping("/countries/")
    fun listCountries(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(service.getAllCountries())

    @GetMapping("/countries/{code}/")
    fun getCountry(@PathVariable code: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(service.getCountry(code.uppercase()))
}
