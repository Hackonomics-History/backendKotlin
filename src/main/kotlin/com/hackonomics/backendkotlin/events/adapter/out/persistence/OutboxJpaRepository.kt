package com.hackonomics.backendkotlin.events.adapter.out.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface OutboxJpaRepository : JpaRepository<OutboxJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.published = false ORDER BY e.createdAt ASC")
    fun findUnpublishedForUpdate(): List<OutboxJpaEntity>

    @Modifying
    @Query("UPDATE OutboxJpaEntity e SET e.published = true WHERE e.id IN :ids")
    fun markPublished(ids: List<Long>): Int
}
