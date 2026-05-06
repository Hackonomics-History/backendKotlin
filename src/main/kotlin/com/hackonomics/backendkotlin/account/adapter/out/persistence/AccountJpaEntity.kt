package com.hackonomics.backendkotlin.account.adapter.out.persistence

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "accounts_accountmodel")
class AccountJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "ory_identity_id", length = 128, unique = true)
    val oryIdentityId: String?,

    @Column(name = "user_id", unique = true)
    val userId: Int? = null,

    @Column(name = "country_code", length = 2)
    var countryCode: String?,

    @Column(name = "currency", length = 3)
    var currency: String?,

    @Column(name = "annual_income", precision = 15, scale = 2)
    var annualIncome: BigDecimal?,

    @Column(name = "monthly_investable_amount", precision = 15, scale = 2)
    var monthlyInvestableAmount: BigDecimal?,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),
)
