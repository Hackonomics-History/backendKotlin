package com.hackonomics.backendkotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BackendKotlinApplication

fun main(args: Array<String>) {
    runApplication<BackendKotlinApplication>(*args)
}
