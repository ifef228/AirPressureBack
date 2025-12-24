package ru.mstu.yandex.gas.entity

import jakarta.persistence.*

@Entity
@Table(name = "gas")
data class Gas(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "formula", nullable = false)
    val formula: String,

    @Column(name = "detailed_description", nullable = false)
    val detailedDescription: String,

    @Column(name = "image_url", length = 500, nullable = true, insertable = false, updatable = true)
    val imageUrl: String? = null,

    @OneToMany(mappedBy = "gas", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val gasOrders: List<GasOrder> = emptyList()
)
