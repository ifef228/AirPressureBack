package ru.mstu.yandex.gas.dto

/**
 * Базовый класс для API ответов
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
) {
    companion object {
        fun <T> success(data: T, message: String? = null) = ApiResponse(true, data, message)
        fun <T> error(message: String, error: String? = null) = ApiResponse<T>(false, null, message, error)
    }
}

/**
 * Ответ для списков с пагинацией
 */
data class PagedResponse<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int
)
