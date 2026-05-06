package com.hackonomics.backendkotlin.account.application.port.out

import com.hackonomics.backendkotlin.account.domain.Account

interface AccountRepository {
    fun findByOryId(oryId: String): Account?
    fun save(account: Account)
    fun getAllCountryCodes(): List<String>
}
