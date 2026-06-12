package com.hackonomics.backendkotlin.calendar.adapter.out.api

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

private val log = LoggerFactory.getLogger(FastApiCalendarClient::class.java)

@Component
class FastApiCalendarClient(
    @Value("\${fastapi.rest.url:http://localhost:8000}") private val fastapiUrl: String,
) {
    private val restClient = RestClient.create(fastapiUrl)

    fun get(path: String, internalToken: String, userId: String): ResponseEntity<String> = proxy {
        restClient.get()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $internalToken")
            .header("X-User-ID", userId)
            .retrieve()
            .toEntity(String::class.java)
    }

    fun post(path: String, body: String?, internalToken: String, userId: String): ResponseEntity<String> = proxy {
        val base = restClient.post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $internalToken")
            .header("X-User-ID", userId)
            .contentType(MediaType.APPLICATION_JSON)
        if (body != null) {
            base.body(body).retrieve().toEntity(String::class.java)
        } else {
            base.retrieve().toEntity(String::class.java)
        }
    }

    fun put(path: String, body: String?, internalToken: String, userId: String): ResponseEntity<String> = proxy {
        val base = restClient.put()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $internalToken")
            .header("X-User-ID", userId)
            .contentType(MediaType.APPLICATION_JSON)
        if (body != null) {
            base.body(body).retrieve().toEntity(String::class.java)
        } else {
            base.retrieve().toEntity(String::class.java)
        }
    }

    fun delete(path: String, internalToken: String, userId: String): ResponseEntity<String> = proxy {
        restClient.delete()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $internalToken")
            .header("X-User-ID", userId)
            .retrieve()
            .toEntity(String::class.java)
    }

    private fun proxy(call: () -> ResponseEntity<String>): ResponseEntity<String> {
        return try {
            call()
        } catch (e: HttpClientErrorException) {
            ResponseEntity.status(e.statusCode).body(e.responseBodyAsString)
        } catch (e: HttpServerErrorException) {
            ResponseEntity.status(e.statusCode).body(e.responseBodyAsString)
        } catch (e: Exception) {
            log.error("FastAPI proxy error: {}", e.message, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("""{"error":"FastAPI service unavailable"}""")
        }
    }
}
