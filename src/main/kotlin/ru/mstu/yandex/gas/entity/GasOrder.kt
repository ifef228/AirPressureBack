package ru.mstu.yandex.gas.entity

import jakarta.persistence.*

@Entity
@Table(name = "gas_order")
data class GasOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "gas_id")
    val gasId: Long,

    @Column(name = "calc_order_id")
    val calcOrderId: Long? = null,

    @Column(name = "concentration", nullable = false)
    val concentration: Double,

    @Column(name = "temperature", nullable = false)
    val temperature: Double,

    @Column(name = "result")
    val result: Double? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gas_id", insertable = false, updatable = false)
    val gas: Gas? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calc_order_id", insertable = false, updatable = false)
    val calcOrder: CalcOrder? = null
)
