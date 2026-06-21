package com.hackonomics.backendkotlin.events.adapter.out.persistence

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox_event")
class OutboxJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id", length = 36, unique = true, nullable = false)
    val eventId: String,

    @Column(name = "aggregate_type", length = 50, nullable = false)
    val aggregateType: String,

    @Column(name = "aggregate_id", length = 50, nullable = false)
    val aggregateId: String,

    @Column(name = "event_type", length = 100, nullable = false)
    val eventType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    val payload: Map<String, Any?>,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "published", nullable = false)
    var published: Boolean = false,
)
