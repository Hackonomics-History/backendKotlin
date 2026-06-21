package com.hackonomics.backendkotlin.account.application.service

import com.hackonomics.backendkotlin.account.adapter.`in`.web.dto.AccountResponse
import com.hackonomics.backendkotlin.account.application.port.out.AccountRepository
import com.hackonomics.backendkotlin.account.application.port.out.ExchangeRatePort
import com.hackonomics.backendkotlin.account.domain.Account
import com.hackonomics.backendkotlin.events.application.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

data class AccountUpdateCommand(
    val countryCode: String,
    val currency: String,
    val annualIncome: BigDecimal,
    val monthlyInvestableAmount: BigDecimal,
)

data class ExchangeRateResult(val base: String, val target: String, val rate: Double)

@Service
class AccountService(
    private val accountRepo: AccountRepository,
    private val exchangeRatePort: ExchangeRatePort,
    private val eventPublisher: DomainEventPublisher,
) {
    fun getAccount(oryId: String): AccountResponse? {
        val account = accountRepo.findByOryId(oryId) ?: return null
        if (account.countryCode == null || account.currency == null ||
            account.annualIncome == null || account.monthlyInvestableAmount == null) return null
        return AccountResponse(
            countryCode = account.countryCode,
            currency = account.currency,
            annualIncome = account.annualIncome.toPlainString(),
            monthlyInvestableAmount = account.monthlyInvestableAmount.toPlainString(),
        )
    }

    @Transactional
    fun updateAccount(oryId: String, cmd: AccountUpdateCommand) {
        val existing = accountRepo.findByOryId(oryId)
        val income = cmd.annualIncome.setScale(2, RoundingMode.DOWN)
        val monthly = cmd.monthlyInvestableAmount.setScale(2, RoundingMode.DOWN)
        val account = Account(
            userId = oryId,
            countryCode = cmd.countryCode.uppercase(),
            currency = cmd.currency.uppercase(),
            annualIncome = income,
            monthlyInvestableAmount = monthly,
        )
        accountRepo.save(account)
        val eventType = if (existing == null) "ACCOUNT_CREATED" else "ACCOUNT_UPDATED"
        eventPublisher.publish(
            aggregateType = "Account",
            aggregateId = oryId,
            eventType = eventType,
            payload = mapOf("user_id" to oryId),
        )
    }

    fun getExchangeRate(oryId: String): ExchangeRateResult? {
        val account = accountRepo.findByOryId(oryId) ?: return null
        val currency = account.currency?.uppercase() ?: return null
        val rate = exchangeRatePort.getUsdRate(currency)
        return ExchangeRateResult("USD", currency, rate)
    }
}
