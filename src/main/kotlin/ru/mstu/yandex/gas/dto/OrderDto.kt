package ru.mstu.yandex.gas.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import ru.mstu.yandex.gas.entity.OrderStatus
import java.time.LocalDateTime

/**
 * DTO для обновления заявки
 */
data class UpdateOrderDto(
    @field:Size(max = 500, message = "Описание не должно превышать 500 символов")
    val description: String? = null
)

/**
 * DTO для ответа с информацией о заявке
 */
data class OrderResponseDto(
    val id: Long,
    val userId: Long,
    val tempResult: Double? = null,
    val timestamp: String,
    val status: String,
    val description: String? = null,
    val createdAt: String? = null,
    val formedAt: String? = null,
    val completedAt: String? = null,
    val creatorLogin: String? = null,
    val moderatorLogin: String? = null,
    val gases: List<GasOrderResponseDto> = emptyList(),
    val completedResultsCount: Int = 0  // Количество записей м-м с заполненным результатом
)

/**
 * DTO для ответа с информацией о газе в заявке
 */
data class GasOrderResponseDto(
    val id: Long,
    val gasId: Long,
    val gasName: String,
    val gasFormula: String,
    val gasImageUrl: String? = null,
    val concentration: Double,
    val temperature: Double,
    val result: Double? = null  // Результат расчета от асинхронного сервиса
)

/**
 * DTO для фильтрации заявок
 */
data class OrderFilterDto(
    val status: String? = null,
    val formedDateFrom: String? = null,
    val formedDateTo: String? = null,
    val page: Int = 0,
    val size: Int = 20
)

/**
 * DTO для иконки корзины
 */
data class CartIconDto(
    val orderId: Long? = null,
    val itemsCount: Int = 0
)

/**
 * DTO для добавления услуги в заявку
 */
data class AddGasToOrderDto(
    @field:NotNull(message = "ID газа обязателен")
    val gasId: Long,

    @field:NotNull(message = "Концентрация обязательна")
    @field:DecimalMin(value = "0.0", message = "Концентрация не может быть отрицательной")
    @field:DecimalMax(value = "100.0", message = "Концентрация не может превышать 100%")
    val concentration: Double,

    @field:NotNull(message = "Температура обязательна")
    @field:DecimalMin(value = "-273.15", message = "Температура не может быть ниже абсолютного нуля")
    @field:DecimalMax(value = "1000.0", message = "Температура не может превышать 1000°C")
    val temperature: Double
)

/**
 * DTO для обновления связи газ-заявка
 */
data class UpdateGasOrderDto(
    @field:DecimalMin(value = "0.0", message = "Концентрация не может быть отрицательной")
    @field:DecimalMax(value = "100.0", message = "Концентрация не может превышать 100%")
    val concentration: Double? = null,

    @field:DecimalMin(value = "-273.15", message = "Температура не может быть ниже абсолютного нуля")
    @field:DecimalMax(value = "1000.0", message = "Температура не может превышать 1000°C")
    val temperature: Double? = null
)

/**
 * DTO для приема результатов от асинхронного сервиса
 */
data class AsyncResultsDto(
    val results: List<AsyncResultItemDto>
)

/**
 * DTO для одного результата от асинхронного сервиса
 */
data class AsyncResultItemDto(
    val calcOrderId: Long,
    val gasId: Long,
    val result: Double
)
