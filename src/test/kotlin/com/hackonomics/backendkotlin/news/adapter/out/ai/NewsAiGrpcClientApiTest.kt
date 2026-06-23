package com.hackonomics.backendkotlin.news.adapter.out.ai

import org.junit.jupiter.api.Test
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fix A [MEDIUM] — Dead code removal: NewsAiGrpcClient.generateNews()
 *
 * generateNews(countryCode, force): List<Pair<String,String>> at line 41 has zero callers.
 * NewsController only calls generateNewsFull() and chatStream(). This was the MVP
 * implementation before generateNewsFull() was added; it was never removed.
 *
 * Keeping dead code costs:
 *  - Reader confusion (two methods doing "the same thing"? which is canonical?)
 *  - Future drift (someone may call generateNews() thinking it's equivalent to generateNewsFull())
 *  - Unnecessary gRPC stub invocations in test mocks
 *
 * RED: Both tests fail because generateNews() currently exists on NewsAiGrpcClient.
 * GREEN: Pass once generateNews() is deleted from NewsAiGrpcClient.
 */
class NewsAiGrpcClientApiTest {

    /**
     * generateNews must not be callable on NewsAiGrpcClient.
     *
     * RED: fails because generateNews() is currently a public suspend function.
     * GREEN: passes once the method is deleted.
     */
    @Test
    fun `generateNews must not be a public method on NewsAiGrpcClient`() {
        val publicMethodNames = NewsAiGrpcClient::class.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }

        assertFalse(
            publicMethodNames.contains("generateNews"),
            "NewsAiGrpcClient.generateNews() is dead code — it has zero callers in the codebase. " +
            "NewsController only calls generateNewsFull() and chatStream(). " +
            "Having two methods that generate news creates confusion about which is canonical. " +
            "Fix: delete the generateNews() method (lines 41-53) from NewsAiGrpcClient.kt. " +
            "Current public methods: $publicMethodNames"
        )
    }

    /**
     * The public API of NewsAiGrpcClient must contain exactly the methods that are
     * actually called: generateNewsFull and chatStream (plus standard Object methods).
     *
     * RED: fails because generateNews is currently present.
     * GREEN: passes once generateNews is removed.
     */
    @Test
    fun `NewsAiGrpcClient public API must not include generateNews`() {
        val userFacingMethods = NewsAiGrpcClient::class.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }
            .filter { it !in setOf("equals", "hashCode", "toString") }

        assertTrue(
            "generateNewsFull" in userFacingMethods,
            "generateNewsFull must still exist after dead code removal. Current API: $userFacingMethods"
        )

        assertTrue(
            "chatStream" in userFacingMethods,
            "chatStream must still exist after dead code removal. Current API: $userFacingMethods"
        )

        assertFalse(
            "generateNews" in userFacingMethods,
            "generateNews must NOT exist — it is dead code with zero callers. " +
            "Current API (should not contain generateNews): $userFacingMethods. " +
            "Fix: delete generateNews() from NewsAiGrpcClient.kt."
        )
    }
}
