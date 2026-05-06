package com.hackonomics.backendkotlin.account.application.service

import com.hackonomics.backendkotlin.account.application.port.out.AccountRepository
import com.hackonomics.backendkotlin.account.application.port.out.ExchangeRatePort
import com.hackonomics.backendkotlin.account.domain.Account
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
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
    fun getAccount(oryId: String): Map<String, Any?>? {
        val account = accountRepo.findByOryId(oryId) ?: return null
        if (account.countryCode == null || account.currency == null ||
            account.annualIncome == null || account.monthlyInvestableAmount == null) return null
        return mapOf(
            "country_code" to account.countryCode,
            "currency" to account.currency,
            "annual_income" to account.annualIncome.toPlainString(),
            "monthly_investable_amount" to account.monthlyInvestableAmount.toPlainString(),
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

    fun getExchangeRate(oryId: String): ExchangeRateResult {
        val account = accountRepo.findByOryId(oryId)
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        val currency = account.currency?.uppercase()
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        val rate = exchangeRatePort.getUsdRate(currency)
        return ExchangeRateResult("USD", currency, rate)
    }
}
