package ru.mstu.yandex.gas.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "calc_order")
data class CalcOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id")
    val userId: Long? = null,

    @Column(name = "temp_result")
    val tempResult: Double? = null,

    @Column(name = "timestamp", nullable = false)
    val timestamp: LocalDateTime,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    val status: OrderStatus = OrderStatus.DRAFT,

    @Column(name = "description", length = 500)
    val description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null,

    @OneToMany(mappedBy = "calcOrder", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val gasOrders: List<GasOrder> = emptyList()
)

enum class OrderStatus {
    DRAFT,      // Черновик (корзина)
    FORMED,     // Сформирован (ожидает модерации)
    COMPLETED,  // Завершен (одобрен)
    CANCELLED,  // Отклонен
    DELETED     // Удален
}
