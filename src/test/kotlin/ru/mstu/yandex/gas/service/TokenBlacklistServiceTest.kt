package ru.mstu.yandex.gas.service

import io.jsonwebtoken.Claims
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import ru.mstu.yandex.gas.util.JwtUtils
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenBlacklistServiceTest {

    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var jwtUtils: JwtUtils
    private lateinit var tokenBlacklistService: TokenBlacklistService
    private lateinit var valueOperations: ValueOperations<String, String>

    @BeforeEach
    fun setup() {
        redisTemplate = mock()
        jwtUtils = mock()
        valueOperations = mock()

        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)

        tokenBlacklistService = TokenBlacklistService(redisTemplate, jwtUtils)
    }

    @Test
    fun `addTokenToBlacklist should add valid token with correct TTL`() {
        // Arrange
        val token = "valid.jwt.token"
        val claims = createMockClaims(expirationMinutes = 60)

        whenever(jwtUtils.getClaimsFromToken(token)).thenReturn(claims)

        // Act
        tokenBlacklistService.addTokenToBlacklist(token)

        // Assert
        verify(valueOperations).set(
            argThat { key: String -> key.startsWith("jwt:blacklist:") },
            eq(token), // Сохраняем полный JWT токен
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `addTokenToBlacklist should handle token without claims gracefully`() {
        // Arrange
        val token = "invalid.jwt.token"

        whenever(jwtUtils.getClaimsFromToken(token)).thenReturn(null)

        // Act
        tokenBlacklistService.addTokenToBlacklist(token)

        // Assert
        verify(valueOperations).set(
            argThat { key: String -> key.startsWith("jwt:blacklist:") },
            eq(token), // Сохраняем полный JWT токен
            eq(24),
            eq(TimeUnit.HOURS)
        )
    }

    @Test
    fun `isTokenBlacklisted should return true for blacklisted token`() {
        // Arrange
        val token = "blacklisted.jwt.token"
        val key = "jwt:blacklist:${token.hashCode()}"

        whenever(redisTemplate.hasKey(key)).thenReturn(true)

        // Act
        val result = tokenBlacklistService.isTokenBlacklisted(token)

        // Assert
        assertTrue(result)
        verify(redisTemplate).hasKey(key)
    }

    @Test
    fun `isTokenBlacklisted should return false for non-blacklisted token`() {
        // Arrange
        val token = "valid.jwt.token"
        val key = "jwt:blacklist:${token.hashCode()}"

        whenever(redisTemplate.hasKey(key)).thenReturn(false)

        // Act
        val result = tokenBlacklistService.isTokenBlacklisted(token)

        // Assert
        assertFalse(result)
        verify(redisTemplate).hasKey(key)
    }

    @Test
    fun `removeTokenFromBlacklist should delete token from redis`() {
        // Arrange
        val token = "token.to.remove"
        val key = "jwt:blacklist:${token.hashCode()}"

        // Act
        tokenBlacklistService.removeTokenFromBlacklist(token)

        // Assert
        verify(redisTemplate).delete(key)
    }

    @Test
    fun `getBlacklistSize should return correct count`() {
        // Arrange
        val keys = setOf(
            "jwt:blacklist:123",
            "jwt:blacklist:456",
            "jwt:blacklist:789"
        )

        whenever(redisTemplate.keys("jwt:blacklist:*")).thenReturn(keys)

        // Act
        val size = tokenBlacklistService.getBlacklistSize()

        // Assert
        assertEquals(3, size)
    }

    @Test
    fun `getBlacklistSize should return 0 when no tokens in blacklist`() {
        // Arrange
        whenever(redisTemplate.keys("jwt:blacklist:*")).thenReturn(emptySet())

        // Act
        val size = tokenBlacklistService.getBlacklistSize()

        // Assert
        assertEquals(0, size)
    }

    @Test
    fun `clearBlacklist should delete all blacklisted tokens`() {
        // Arrange
        val keys = setOf(
            "jwt:blacklist:123",
            "jwt:blacklist:456",
            "jwt:blacklist:789"
        )

        whenever(redisTemplate.keys("jwt:blacklist:*")).thenReturn(keys)

        // Act
        tokenBlacklistService.clearBlacklist()

        // Assert
        verify(redisTemplate).delete(keys)
    }

    @Test
    fun `isTokenBlacklisted should return false on redis exception`() {
        // Arrange
        val token = "problematic.jwt.token"

        whenever(redisTemplate.hasKey(any())).thenThrow(RuntimeException("Redis connection error"))

        // Act
        val result = tokenBlacklistService.isTokenBlacklisted(token)

        // Assert
        assertFalse(result, "Should return false on exception to fail open")
    }

    // Helper method to create mock Claims
    private fun createMockClaims(expirationMinutes: Long): Claims {
        val now = Date()
        val expiration = Date(now.time + TimeUnit.MINUTES.toMillis(expirationMinutes))

        // Используем mock для Claims, так как в версии 0.12.x изменился API
        val claims = mock<Claims>()
        whenever(claims["userId"]).thenReturn(1L)
        whenever(claims["login"]).thenReturn("testuser")
        whenever(claims["role"]).thenReturn("USER")
        whenever(claims.subject).thenReturn("testuser")
        whenever(claims.issuedAt).thenReturn(now)
        whenever(claims.expiration).thenReturn(expiration)

        return claims
    }
}
