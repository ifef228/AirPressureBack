package ru.mstu.yandex.gas.util

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.mstu.yandex.gas.service.TokenBlacklistService
import java.security.Key
import java.util.*

@Component
class JwtUtils(
    @Lazy private val tokenBlacklistService: TokenBlacklistService
) {

    @Value("\${jwt.secret:mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLong}")
    private lateinit var secret: String

    @Value("\${jwt.expiration:86400000}") // 24 часа по умолчанию
    private var expiration: Long = 86400000

    private fun getSigningKey(): Key {
        val keyBytes = secret.toByteArray()
        // Если ключ короче 256 бит, дополняем его
        val key = if (keyBytes.size < 32) {
            Arrays.copyOf(keyBytes, 32)
        } else {
            keyBytes
        }
        return Keys.hmacShaKeyFor(key)
    }

    /**
     * Генерирует JWT токен для пользователя
     */
    fun generateToken(userId: Long, login: String, role: String?): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        val claims: Map<String, Any> = mapOf(
            "userId" to userId,
            "login" to login,
            "role" to (role ?: "USER")
        )

        return Jwts.builder()
            .setClaims(claims as MutableMap<String, Any>?)
            .setSubject(login)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey())
            .compact()
    }

    /**
     * Извлекает claims из токена
     */
    fun getClaimsFromToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Извлекает ID пользователя из токена
     */
    fun getUserIdFromToken(token: String): Long? {
        val claims = getClaimsFromToken(token) ?: return null
        return try {
            val userIdObj = claims["userId"]
            when (userIdObj) {
                is Number -> userIdObj.toLong()
                is String -> userIdObj.toLongOrNull()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Извлекает роль пользователя из токена
     */
    fun getRoleFromToken(token: String): String? {
        val claims = getClaimsFromToken(token) ?: return null
        return claims["role"] as? String
    }

    /**
     * Извлекает логин пользователя из токена
     */
    fun getLoginFromToken(token: String): String? {
        val claims = getClaimsFromToken(token) ?: return null
        return claims.subject
    }

    /**
     * Проверяет, действителен ли токен
     * Проверяет как валидность токена, так и наличие в blacklist
     */
    fun validateToken(token: String): Boolean {
        return try {
            // Сначала проверяем, не находится ли токен в blacklist
            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                return false
            }

            // Затем проверяем валидность и срок действия токена
            val claims = getClaimsFromToken(token) ?: return false
            !isTokenExpired(claims)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Проверяет валидность токена без проверки blacklist
     * Используется для внутренних нужд (например, при добавлении в blacklist)
     */
    fun validateTokenWithoutBlacklist(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token) ?: return false
            !isTokenExpired(claims)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Проверяет, истек ли токен
     */
    private fun isTokenExpired(claims: Claims): Boolean {
        val expiration = claims.expiration
        return expiration.before(Date())
    }

    /**
     * Извлекает токен из заголовка Authorization
     */
    fun extractTokenFromHeader(authHeader: String?): String? {
        if (authHeader.isNullOrBlank()) {
            return null
        }

        return if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            authHeader.trim()
        }
    }
}
