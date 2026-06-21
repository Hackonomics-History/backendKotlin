package com.hackonomics.backendkotlin.account.adapter.`in`.web

import com.hackonomics.backendkotlin.account.adapter.`in`.web.dto.AccountResponse
import com.hackonomics.backendkotlin.account.adapter.`in`.web.dto.UpdateAccountRequest
import com.hackonomics.backendkotlin.account.application.service.AccountService
import com.hackonomics.backendkotlin.account.application.service.AccountUpdateCommand
import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto.ExchangeRateResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/account")
class AccountController(private val service: AccountService) {

    @GetMapping("/me/")
    fun me(identity: OryIdentity): ResponseEntity<AccountResponse> {
        val result = service.getAccount(identity.id)
        return ResponseEntity.ok(result ?: AccountResponse(null, null, null, null))
    }

    @PutMapping("/me/")
    fun update(identity: OryIdentity, @Valid @RequestBody req: UpdateAccountRequest): ResponseEntity<Void> {
        service.updateAccount(identity.id, AccountUpdateCommand(
            countryCode = req.countryCode,
            currency = req.currency,
            annualIncome = req.annualIncome,
            monthlyInvestableAmount = req.monthlyInvestableAmount,
        ))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me/exchange-rate/")
    fun exchangeRate(identity: OryIdentity): ResponseEntity<ExchangeRateResponse> {
        val result = service.getExchangeRate(identity.id)
        return if (result != null)
            ResponseEntity.ok(ExchangeRateResponse(result.base, result.target, result.rate))
        else
            ResponseEntity.ok(ExchangeRateResponse(null, null, 0.0))
    }
}
