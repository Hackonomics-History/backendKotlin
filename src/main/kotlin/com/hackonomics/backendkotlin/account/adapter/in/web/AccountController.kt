package com.hackonomics.backendkotlin.account.adapter.`in`.web

import com.hackonomics.backendkotlin.account.adapter.`in`.web.dto.UpdateAccountRequest
import com.hackonomics.backendkotlin.account.application.service.AccountService
import com.hackonomics.backendkotlin.account.application.service.AccountUpdateCommand
import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/account")
class AccountController(private val service: AccountService) {

    @GetMapping("/me/")
    fun me(identity: OryIdentity): ResponseEntity<Map<String, Any?>> {
        val result = service.getAccount(identity.id)
        return if (result != null) ResponseEntity.ok(result)
        else ResponseEntity.ok(mapOf(
            "country_code" to null,
            "currency" to null,
            "annual_income" to null,
            "monthly_investable_amount" to null,
        ))
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
    fun exchangeRate(identity: OryIdentity): ResponseEntity<Map<String, Any>> {
        val result = service.getExchangeRate(identity.id)
        return ResponseEntity.ok(mapOf(
            "base" to result.base,
            "target" to result.target,
            "rate" to result.rate,
        ))
    }
}
