package ru.mstu.yandex.gas.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * DTO для завершения заявки
 */
data class CompleteOrderDto(
    @field:NotBlank(message = "Действие обязательно")
    @field:Pattern(
        regexp = "APPROVE|REJECT",
        message = "Действие должно быть APPROVE (подтвердить) или REJECT (отклонить)"
    )
    val action: String,

    val comment: String? = null
)
