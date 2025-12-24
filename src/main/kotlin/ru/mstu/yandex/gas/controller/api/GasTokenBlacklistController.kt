package ru.mstu.yandex.gas.controller.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.mstu.yandex.gas.dto.ApiResponse
import ru.mstu.yandex.gas.service.TokenBlacklistService

/**
 * Контроллер для администрирования blacklist токенов
 * ВНИМАНИЕ: В production следует ограничить доступ только для администраторов!
 */
@RestController
@RequestMapping("/api/admin/blacklist")
@Tag(name = "Token Blacklist Admin", description = "API для администрирования blacklist JWT токенов")
class GasTokenBlacklistController(
    private val tokenBlacklistService: TokenBlacklistService,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @Operation(
        summary = "Получить все ключи blacklist",
        description = "Возвращает список всех токенов в blacklist с их TTL. В поле 'value' содержится полный JWT токен."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Список ключей получен"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/keys")
    fun getAllBlacklistKeys(): ResponseEntity<ApiResponse<List<BlacklistKeyInfo>>> {
        return try {
            val keys = redisTemplate.keys("jwt:blacklist:*") ?: emptySet()

            val keyInfoList = keys.map { key ->
                val ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS)
                val value = redisTemplate.opsForValue().get(key)

                BlacklistKeyInfo(
                    key = key,
                    value = value ?: "N/A",
                    ttlSeconds = ttl,
                    ttlHours = if (ttl > 0) ttl / 3600.0 else 0.0,
                    ttlDays = if (ttl > 0) ttl / 86400.0 else 0.0
                )
            }.sortedByDescending { it.ttlSeconds }

            ResponseEntity.ok(
                ApiResponse.success(
                    keyInfoList,
                    "Найдено ключей в blacklist: ${keyInfoList.size}"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении ключей: ${e.message}"))
        }
    }

    @Operation(
        summary = "Получить размер blacklist",
        description = "Возвращает количество токенов в blacklist"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Размер получен"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/size")
    fun getBlacklistSize(): ResponseEntity<ApiResponse<BlacklistSizeInfo>> {
        return try {
            val size = tokenBlacklistService.getBlacklistSize()
            val info = BlacklistSizeInfo(
                totalTokens = size,
                message = "Токенов в blacklist: $size"
            )

            ResponseEntity.ok(ApiResponse.success(info))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении размера: ${e.message}"))
        }
    }

    @Operation(
        summary = "Проверить токен в blacklist",
        description = "Проверяет, находится ли конкретный токен в blacklist"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Проверка выполнена"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/check")
    fun checkToken(@RequestBody request: CheckTokenRequest): ResponseEntity<ApiResponse<TokenCheckResult>> {
        return try {
            val isBlacklisted = tokenBlacklistService.isTokenBlacklisted(request.token)
            val key = "jwt:blacklist:${request.token.hashCode()}"

            val result = TokenCheckResult(
                token = request.token.take(20) + "...", // Показываем только начало
                isBlacklisted = isBlacklisted,
                redisKey = key,
                ttlSeconds = if (isBlacklisted) redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS) else -1
            )

            ResponseEntity.ok(ApiResponse.success(result))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при проверке токена: ${e.message}"))
        }
    }

    @Operation(
        summary = "Очистить весь blacklist",
        description = "ОПАСНО! Удаляет все токены из blacklist"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Blacklist очищен"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/clear")
    fun clearBlacklist(): ResponseEntity<ApiResponse<String>> {
        return try {
            val sizeBefore = tokenBlacklistService.getBlacklistSize()
            tokenBlacklistService.clearBlacklist()

            ResponseEntity.ok(
                ApiResponse.success(
                    "Blacklist очищен. Удалено токенов: $sizeBefore"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при очистке blacklist: ${e.message}"))
        }
    }

    @Operation(
        summary = "Статистика Redis",
        description = "Получить общую статистику Redis"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Статистика получена"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/stats")
    fun getRedisStats(): ResponseEntity<ApiResponse<RedisStats>> {
        return try {
            val allKeys = redisTemplate.keys("*") ?: emptySet()
            val blacklistKeys = redisTemplate.keys("jwt:blacklist:*") ?: emptySet()

            val stats = RedisStats(
                totalKeys = allKeys.size.toLong(),
                blacklistKeys = blacklistKeys.size.toLong(),
                otherKeys = (allKeys.size - blacklistKeys.size).toLong()
            )

            ResponseEntity.ok(ApiResponse.success(stats))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении статистики: ${e.message}"))
        }
    }
}

// DTOs для ответов

data class BlacklistKeyInfo(
    val key: String,
    val value: String,
    val ttlSeconds: Long,
    val ttlHours: Double,
    val ttlDays: Double
)

data class BlacklistSizeInfo(
    val totalTokens: Long,
    val message: String
)

data class CheckTokenRequest(
    val token: String
)

data class TokenCheckResult(
    val token: String,
    val isBlacklisted: Boolean,
    val redisKey: String,
    val ttlSeconds: Long
)

data class RedisStats(
    val totalKeys: Long,
    val blacklistKeys: Long,
    val otherKeys: Long
)
