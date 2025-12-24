package ru.mstu.yandex.gas.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * DTO для создания новой услуги (газа)
 */
data class CreateGasDto(
    @field:NotBlank(message = "Название газа обязательно")
    @field:Size(max = 255, message = "Название газа не должно превышать 255 символов")
    val name: String,

    @field:NotBlank(message = "Формула газа обязательна")
    @field:Size(max = 255, message = "Формула газа не должно превышать 255 символов")
    val formula: String,

    @field:NotBlank(message = "Описание газа обязательно")
    val detailedDescription: String,

    val imageUrl: String? = null
)

/**
 * DTO для обновления услуги (газа)
 */
data class UpdateGasDto(
    @field:Size(max = 255, message = "Название газа не должно превышать 255 символов")
    val name: String? = null,

    @field:Size(max = 255, message = "Формула газа не должна превышать 255 символов")
    val formula: String? = null,

    val detailedDescription: String? = null,

    val imageUrl: String? = null
)

/**
 * DTO для ответа с информацией об услуге (газе)
 */
data class GasResponseDto(
    val id: Long,
    val name: String,
    val formula: String,
    val detailedDescription: String,
    val imageUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * DTO для фильтрации услуг
 */
data class GasFilterDto(
    val name: String? = null,
    val formula: String? = null,
    val page: Int = 0,
    val size: Int = 20
)

/**
 * DTO для добавления изображения по ссылке
 */
data class AddImageUrlDto(
    @field:NotBlank(message = "URL изображения обязателен")
    val imageUrl: String
)
