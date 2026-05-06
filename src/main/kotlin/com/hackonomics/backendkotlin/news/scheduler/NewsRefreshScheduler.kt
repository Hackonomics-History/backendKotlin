package com.hackonomics.backendkotlin.news.scheduler

import com.hackonomics.backendkotlin.news.adapter.out.kafka.NewsRefreshKafkaPublisher
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(NewsRefreshScheduler::class.java)

// Runs every 6 hours (matching Django's Celery Beat schedule).
// A lightweight JDBC advisory-lock prevents duplicate runs across replicas
// without requiring the ShedLock library.
@Component
@EnableScheduling
class NewsRefreshScheduler(
    private val publisher: NewsRefreshKafkaPublisher,
    private val jdbc: JdbcTemplate,
) {
    // Lock ID 20260505 is arbitrary but stable; pick any unique long.
    private val LOCK_ID = 20260505L

    @Scheduled(cron = "0 0 */6 * * *")
    fun scheduleNewsRefresh() {
        val locked = jdbc.queryForObject(
            "SELECT pg_try_advisory_lock(?)", Boolean::class.java, LOCK_ID
        ) ?: false

        if (!locked) {
            log.debug("NewsRefreshScheduler: advisory lock held by another replica — skipping")
            return
        }

        try {
            // Publish one request per active country.  The country list is
            // derived from distinct country_code values in accounts_accountmodel.
            val countries = jdbc.queryForList(
                "SELECT DISTINCT country_code FROM accounts_accountmodel WHERE country_code IS NOT NULL",
                String::class.java,
            )
            log.info("NewsRefreshScheduler: dispatching refresh for {} countries", countries.size)
            countries.forEach { cc -> publisher.publishRequest(cc, force = false) }
        } finally {
            jdbc.execute("SELECT pg_advisory_unlock($LOCK_ID)")
        }
    }
}
