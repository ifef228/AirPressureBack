package ru.mstu.yandex.gas.util

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import ru.mstu.yandex.gas.dto.ApiResponse

@Component
class RoleUtils @Autowired constructor(
    private val jwtUtils: JwtUtils
) {

    /**
     * Проверить, является ли пользователь модератором (MODERATOR или ADMIN)
     */
    fun checkModeratorRole(authHeader: String?, cookieHeader: String? = null): ResponseEntity<ApiResponse<Nothing>>? {
        val authResult = validateTokenAndGetRole(authHeader, cookieHeader) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Токен авторизации не предоставлен или неверен"))

        val role = authResult.role
        if (role != "MODERATOR" && role != "ADMIN") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Доступ запрещен. Требуется роль модератора"))
        }

        return null // Если проверка прошла успешно
    }

    /**
     * Проверить, авторизован ли пользователь (любая роль)
     */
    fun checkAuthenticated(authHeader: String?, cookieHeader: String? = null): ResponseEntity<ApiResponse<Nothing>>? {
        if (validateTokenAndGetRole(authHeader, cookieHeader) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Токен авторизации не предоставлен или неверен"))
        }
        return null
    }

    /**
     * Валидация токена и получение информации о пользователе (Authorization или Cookie)
     */
    fun validateTokenAndGetRole(authHeader: String?, cookieHeader: String? = null): AuthResult? {
        val tokenCandidate = authHeader ?: extractTokenFromCookie(cookieHeader)
        if (tokenCandidate.isNullOrBlank()) {
            return null
        }

        val cleanToken = jwtUtils.extractTokenFromHeader(tokenCandidate) ?: return null

        if (!jwtUtils.validateToken(cleanToken)) {
            return null
        }

        val userId = jwtUtils.getUserIdFromToken(cleanToken) ?: return null
        val role = jwtUtils.getRoleFromToken(cleanToken) ?: "USER"
        val login = jwtUtils.getLoginFromToken(cleanToken) ?: return null

        return AuthResult(userId, login, role)
    }

    private fun extractTokenFromCookie(cookieHeader: String?): String? {
        if (cookieHeader.isNullOrBlank()) return null
        val cookies = cookieHeader.split(";").map { it.trim() }
        for (cookie in cookies) {
            if (cookie.startsWith("jwt_token=")) return cookie.substringAfter("jwt_token=")
            if (cookie.startsWith("token=")) return cookie.substringAfter("token=")
        }
        return null
    }

    /**
     * Получить ID пользователя из токена
     */
    fun getUserIdFromToken(authHeader: String?, cookieHeader: String? = null): Long? {
        val auth = validateTokenAndGetRole(authHeader, cookieHeader) ?: return null
        return auth.userId
    }

    /**
     * Результат валидации токена
     */
    data class AuthResult(
        val userId: Long,
        val login: String,
        val role: String
    )
}
