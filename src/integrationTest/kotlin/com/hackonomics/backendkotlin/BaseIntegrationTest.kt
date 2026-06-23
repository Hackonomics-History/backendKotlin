package com.hackonomics.backendkotlin

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka

/**
 * Base class for all integration tests.
 *
 * - @SpringBootTest loads the full application context once and caches it across all subclasses
 *   that share the same configuration, minimising broker-startup overhead in CI.
 * - @EmbeddedKafka(ports = [0]) assigns a random OS port on each test run to prevent collisions
 *   when tests run in parallel locally or on the CI runner.
 * - bootstrapServersProperty wires the embedded broker address directly into
 *   spring.kafka.bootstrap-servers so no application.yaml override is needed.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    ports = [0],
)
abstract class BaseIntegrationTest
