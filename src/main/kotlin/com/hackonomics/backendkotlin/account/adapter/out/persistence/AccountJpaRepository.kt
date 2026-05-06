package com.hackonomics.backendkotlin.account.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AccountJpaRepository : JpaRepository<AccountJpaEntity, Long> {
    fun findByOryIdentityId(oryIdentityId: String): AccountJpaEntity?

    @Query("SELECT DISTINCT a.countryCode FROM AccountJpaEntity a WHERE a.countryCode IS NOT NULL")
    fun findDistinctCountryCodes(): List<String>
}
