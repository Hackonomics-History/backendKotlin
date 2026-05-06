package com.hackonomics.backendkotlin.account.adapter.out.persistence

import com.hackonomics.backendkotlin.account.application.port.out.AccountRepository
import com.hackonomics.backendkotlin.account.domain.Account
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AccountPersistenceAdapter(private val jpa: AccountJpaRepository) : AccountRepository {

    override fun findByOryId(oryId: String): Account? =
        jpa.findByOryIdentityId(oryId)?.toDomain()

    override fun save(account: Account) {
        val existing = jpa.findByOryIdentityId(account.userId)
        if (existing != null) {
            existing.countryCode = account.countryCode
            existing.currency = account.currency
            existing.annualIncome = account.annualIncome
            existing.monthlyInvestableAmount = account.monthlyInvestableAmount
            existing.updatedAt = Instant.now()
            jpa.save(existing)
        } else {
            jpa.save(AccountJpaEntity(
                oryIdentityId = account.userId,
                countryCode = account.countryCode,
                currency = account.currency,
                annualIncome = account.annualIncome,
                monthlyInvestableAmount = account.monthlyInvestableAmount,
            ))
        }
    }

    override fun getAllCountryCodes(): List<String> = jpa.findDistinctCountryCodes()

    private fun AccountJpaEntity.toDomain() = Account(
        userId = oryIdentityId ?: "",
        countryCode = countryCode,
        currency = currency,
        annualIncome = annualIncome,
        monthlyInvestableAmount = monthlyInvestableAmount,
    )
}
