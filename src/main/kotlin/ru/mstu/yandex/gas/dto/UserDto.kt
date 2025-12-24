package ru.mstu.yandex.gas.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * DTO для регистрации пользователя
 */
data class RegisterUserDto(
    @field:NotBlank(message = "Логин обязателен")
    @field:Size(min = 3, max = 50, message = "Логин должен содержать от 3 до 50 символов")
    val login: String,

    @field:NotBlank(message = "Пароль обязателен")
    @field:Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    val password: String,

    @field:Email(message = "Некорректный email")
    val email: String? = null,

    @field:Size(max = 100, message = "Имя не должно превышать 100 символов")
    val firstName: String? = null,

    @field:Size(max = 100, message = "Фамилия не должна превышать 100 символов")
    val lastName: String? = null,

    @field:Size(max = 128, message = "Роль не должна превышать 128 символов")
    val role: String? = null
)

/**
 * DTO для аутентификации
 */
data class LoginDto(
    @field:NotBlank(message = "Логин обязателен")
    val login: String,

    @field:NotBlank(message = "Пароль обязателен")
    val password: String
)

/**
 * DTO для обновления пользователя
 */
data class UpdateUserDto(
    @field:Email(message = "Некорректный email")
    val email: String? = null,

    @field:Size(max = 100, message = "Имя не должно превышать 100 символов")
    val firstName: String? = null,

    @field:Size(max = 100, message = "Фамилия не должна превышать 100 символов")
    val lastName: String? = null,

    @field:Size(max = 128, message = "Роль не должна превышать 128 символов")
    val role: String? = null
)

/**
 * DTO для ответа с информацией о пользователе
 */
data class UserResponseDto(
    val id: Long,
    val login: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val createdAt: String? = null
)

/**
 * DTO для ответа аутентификации
 */
data class AuthResponseDto(
    val token: String? = null,
    val user: UserResponseDto
)
