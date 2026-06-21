package com.hackonomics.backendkotlin.calendar.adapter.out.api

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

private val log = LoggerFactory.getLogger(FastApiCalendarClient::class.java)

@Component
class FastApiCalendarClient(
    @Value("\${fastapi.rest.url:http://localhost:8000}") private val fastapiUrl: String,
) {
    // Force HTTP/1.1: Spring Boot 4 / Java 21 auto-selects JdkClientHttpRequestFactory whose
    // HttpClient defaults to HTTP_2. For cleartext targets (http://) it sends the h2c binary
    // preface instead of HTTP/1.1, which uvicorn/httptools rejects with
    // "Invalid HTTP request received". Pinning to HTTP_1_1 restores normal behaviour.
    private val restClient = RestClient.builder()
        .baseUrl(fastapiUrl)
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
            )
        )
        .build()

    init {
        log.info("[DIAG] FastApiCalendarClient init fastapiUrl={}", fastapiUrl)
    }

    fun get(path: String, internalToken: String, userId: String): ResponseEntity<String> = proxy {
        restClient.get()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $internalToken")
            .header("X-User-ID", userId)
            .retrieve()
            .toEntity(String::class.java)
    }

    fun post(path: String, body: String?, internalToken: String, userId: String): ResponseEntity<String> = proxy {
        val maskedToken = maskToken(internalToken)
        log.info(
            "[DIAG] FastAPI POST url={}{} userId={} tokenLen={} tokenMasked={} authHeader=\"Bearer {}\" bodyLen={}",
            fastapiUrl, path, userId, internalToken.length, maskedToken, maskedToken, body?.length
        )
        val base = restClient.post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $internalToken")
            .header("X-User-ID", userId)
            .contentType(MediaType.APPLICATION_JSON)
        val response = if (body != null) {
            base.body(body).retrieve().toEntity(String::class.java)
        } else {
            base.retrieve().toEntity(String::class.java)
        }
        log.info("[DIAG] FastAPI POST {} status={} responseBody={}", path, response.statusCode, response.body)
        response
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
            log.info("[DIAG] FastAPI error status={} errorBody={}", e.statusCode, e.responseBodyAsString)
            ResponseEntity.status(e.statusCode).body(e.responseBodyAsString)
        } catch (e: HttpServerErrorException) {
            log.info("[DIAG] FastAPI error status={} errorBody={}", e.statusCode, e.responseBodyAsString)
            ResponseEntity.status(e.statusCode).body(e.responseBodyAsString)
        } catch (e: Exception) {
            log.error("[DIAG] FastAPI proxy error class={} msg={}", e::class.simpleName, e.message, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("""{"error":"FastAPI service unavailable"}""")
        }
    }

    private fun maskToken(token: String): String =
        if (token.length > 8) "${token.take(4)}…${token.takeLast(4)}" else "***"
}
