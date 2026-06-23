package com.hackonomics.backendkotlin.auth.adapter.out.grpc

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * TDD Red Phase — application.yaml missing central-auth.grpc.target
 *
 * CentralAuthGrpcClient is annotated with:
 *   @Value("\${central-auth.grpc.target:localhost:50051}") private val target: String
 *
 * The fallback `localhost:50051` silently applies when NEITHER application.yaml declares
 * `central-auth.grpc.target` NOR the env var CENTRAL_AUTH_GRPC_TARGET is set.
 * In production K8s, `localhost:50051` resolves to the pod itself — not the Central Auth
 * service — so every login, signup, refresh, and logout call fails with UNAVAILABLE or
 * UNIMPLEMENTED, but the pod starts without any error at startup.
 *
 * The fix is two-part:
 *  1. Add `central-auth.grpc.target: ${CENTRAL_AUTH_GRPC_TARGET:localhost:50051}` to
 *     application.yaml under the existing `central-auth:` section so that:
 *     a. The key is explicit and discoverable by ops when reading the config file.
 *     b. The env var override path is clearly documented.
 *  2. (Optional hardening) Remove the `:localhost:50051` fallback from the @Value annotation
 *     so that a missing CENTRAL_AUTH_GRPC_TARGET causes a fast startup failure instead of a
 *     silent misrouting to localhost.
 *
 * RED: Both tests fail because application.yaml currently has no central-auth.grpc section.
 * GREEN: Pass once the key is added to application.yaml.
 */
class CentralAuthYamlConfigTest {

    /**
     * application.yaml must declare central-auth.grpc.target so that:
     *  - The gRPC address for Central Auth is explicitly documented in the config file.
     *  - CENTRAL_AUTH_GRPC_TARGET env var override is surfaced as the expected mechanism.
     *  - A missing env var in a non-local environment is caught at deployment review time,
     *    not discovered as mysterious 100% auth failures in production traffic.
     *
     * RED: fails because the current yaml has only `central-auth.service-key`, no `grpc.target`.
     * GREEN: passes once `central-auth.grpc.target: ${CENTRAL_AUTH_GRPC_TARGET:localhost:50051}`
     *        is added inside the `central-auth:` block.
     */
    @Test
    fun `application yaml must declare central-auth grpc target to prevent silent localhost fallback in production`() {
        val sourceFile = File("src/main/resources/application.yaml")
        assertTrue(
            sourceFile.exists(),
            "application.yaml not found at: ${sourceFile.absolutePath}. " +
            "Run this test from the backendKotlin/ module root."
        )

        val content = sourceFile.readText()

        assertTrue(
            content.contains("CENTRAL_AUTH_GRPC_TARGET"),
            "application.yaml is missing the 'central-auth.grpc.target' key. " +
            "CentralAuthGrpcClient uses @Value(\"\${central-auth.grpc.target:localhost:50051}\") " +
            "which silently falls back to 'localhost:50051' when CENTRAL_AUTH_GRPC_TARGET is absent. " +
            "In Kubernetes, localhost:50051 resolves to the application pod itself — not the " +
            "Central Auth service — so ALL auth RPCs (login, signup, refresh, logout) fail with " +
            "UNAVAILABLE/UNIMPLEMENTED, but the pod starts cleanly with no startup error. " +
            "Fix: add the following under the existing 'central-auth:' section in application.yaml:\n" +
            "  central-auth:\n" +
            "    service-key: \${CENTRAL_AUTH_SERVICE_KEY}\n" +
            "    grpc:\n" +
            "      target: \${CENTRAL_AUTH_GRPC_TARGET:localhost:50051}"
        )
    }

    /**
     * The central-auth section in application.yaml must have a nested `grpc.target` key,
     * not just the service-key.
     *
     * This test validates the YAML structure rather than just the env-var reference,
     * guarding against the case where CENTRAL_AUTH_GRPC_TARGET is added at the wrong
     * nesting level (e.g., as a flat top-level key instead of under central-auth.grpc).
     *
     * RED: fails because the central-auth block has only `service-key`, no `grpc:` sub-block.
     * GREEN: passes once `grpc:` with `target:` is nested under `central-auth:`.
     */
    @Test
    fun `central-auth section in application yaml must contain a nested grpc target key`() {
        val sourceFile = File("src/main/resources/application.yaml")
        assertTrue(
            sourceFile.exists(),
            "application.yaml not found at: ${sourceFile.absolutePath}."
        )

        val content = sourceFile.readText()

        // Find the central-auth block and verify it contains a grpc.target entry.
        // The expected YAML structure is:
        //   central-auth:
        //     service-key: ${CENTRAL_AUTH_SERVICE_KEY}
        //     grpc:
        //       target: ${CENTRAL_AUTH_GRPC_TARGET:localhost:50051}
        val centralAuthIndex = content.indexOf("central-auth:")
        assertTrue(
            centralAuthIndex >= 0,
            "application.yaml has no 'central-auth:' top-level key at all."
        )

        // Extract the substring from central-auth: until the next top-level key (unindented line)
        // by scanning for the next non-empty, non-space line that starts at column 0.
        val afterCentralAuth = content.substring(centralAuthIndex + "central-auth:".length)
        val nextTopLevelKey = Regex("(?m)^[a-zA-Z]").find(afterCentralAuth)
        val centralAuthBlock = if (nextTopLevelKey != null) {
            afterCentralAuth.substring(0, nextTopLevelKey.range.first)
        } else {
            afterCentralAuth
        }

        assertTrue(
            centralAuthBlock.contains("grpc:") || centralAuthBlock.contains("grpc.target"),
            "The 'central-auth:' section in application.yaml has no 'grpc:' sub-key. " +
            "Current central-auth block:\n$centralAuthBlock\n" +
            "Fix: add a 'grpc:' block with 'target: \${CENTRAL_AUTH_GRPC_TARGET:localhost:50051}' " +
            "under central-auth:. Without this, the @Value fallback silently misroutes all " +
            "Central Auth gRPC traffic to localhost, causing 100% auth failure in every " +
            "non-local environment."
        )
    }
}
