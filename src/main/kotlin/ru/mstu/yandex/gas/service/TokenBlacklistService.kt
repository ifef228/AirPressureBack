package ru.mstu.yandex.gas.service

import io.jsonwebtoken.Claims
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import ru.mstu.yandex.gas.util.JwtUtils
import java.util.concurrent.TimeUnit

/**
 * Сервис для управления blacklist JWT токенов в Redis
 */
@Service
class TokenBlacklistService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val jwtUtils: JwtUtils
) {

    private val logger = LoggerFactory.getLogger(TokenBlacklistService::class.java)

    companion object {
        private const val BLACKLIST_PREFIX = "jwt:blacklist:"
    }

    /**
     * Добавляет токен в blacklist
     * @param token JWT токен для добавления в blacklist
     */
    fun addTokenToBlacklist(token: String) {
        try {
            val key = getBlacklistKey(token)
            logger.debug("Добавление токена в blacklist. Ключ: $key, Токен (первые 20 символов): ${token.take(20)}...")

            // Проверяем подключение к Redis
            if (!isRedisAvailable()) {
                logger.error("Redis недоступен! Проверьте, запущен ли Redis сервер.")
                throw RuntimeException("Redis недоступен. Проверьте подключение к Redis серверу.")
            }
            logger.debug("Подключение к Redis успешно")

            // Получаем время жизни токена из его claims
            val claims = jwtUtils.getClaimsFromToken(token)
            if (claims != null) {
                val expirationTime = calculateTTL(claims)
                logger.debug("TTL токена: $expirationTime ms (${expirationTime / 1000 / 60} минут)")

                // Сохраняем полный JWT токен в Redis с TTL
                redisTemplate.opsForValue().set(key, token, expirationTime, TimeUnit.MILLISECONDS)

                // Проверяем, что значение действительно сохранено
                val savedValue = redisTemplate.opsForValue().get(key)
                if (savedValue != null && savedValue == token) {
                    logger.info("Токен успешно добавлен в blacklist. Ключ: $key, TTL: $expirationTime ms")
                } else {
                    logger.error("ОШИБКА: Токен не был сохранен в Redis! Ключ: $key, Ожидалось: ${token.take(20)}..., Получено: ${savedValue?.take(20)}...")
                    throw RuntimeException("Токен не был сохранен в Redis")
                }
            } else {
                logger.warn("Не удалось извлечь claims из токена. Сохраняем с TTL 24 часа")
                // Если не удалось извлечь claims, сохраняем на 24 часа
                // Сохраняем полный JWT токен в Redis
                redisTemplate.opsForValue().set(key, token, 24, TimeUnit.HOURS)

                // Проверяем, что значение действительно сохранено
                val savedValue = redisTemplate.opsForValue().get(key)
                if (savedValue != null && savedValue == token) {
                    logger.info("Токен добавлен в blacklist с TTL 24 часа. Ключ: $key")
                } else {
                    logger.error("ОШИБКА: Токен не был сохранен в Redis! Ключ: $key")
                    throw RuntimeException("Токен не был сохранен в Redis")
                }
            }
        } catch (e: RuntimeException) {
            // Пробрасываем RuntimeException дальше
            throw e
        } catch (e: Exception) {
            logger.error("Ошибка при добавлении токена в blacklist: ${e.message}", e)
            logger.error("Стек ошибки: ${e.stackTraceToString()}")
            throw RuntimeException("Не удалось добавить токен в blacklist: ${e.message}", e)
        }
    }

    /**
     * Проверяет, находится ли токен в blacklist
     * @param token JWT токен для проверки
     * @return true, если токен в blacklist
     */
    fun isTokenBlacklisted(token: String): Boolean {
        return try {
            val key = getBlacklistKey(token)
            val result = redisTemplate.hasKey(key) ?: false

            if (result) {
                logger.debug("Токен найден в blacklist")
            }

            result
        } catch (e: Exception) {
            logger.error("Ошибка при проверке токена в blacklist: ${e.message}", e)
            // В случае ошибки считаем, что токен не в blacklist
            false
        }
    }

    /**
     * Удаляет токен из blacklist (на случай ошибочного добавления)
     * @param token JWT токен для удаления
     */
    fun removeTokenFromBlacklist(token: String) {
        try {
            val key = getBlacklistKey(token)
            redisTemplate.delete(key)
            logger.info("Токен удален из blacklist")
        } catch (e: Exception) {
            logger.error("Ошибка при удалении токена из blacklist: ${e.message}", e)
        }
    }

    /**
     * Возвращает количество токенов в blacklist
     */
    fun getBlacklistSize(): Long {
        return try {
            val keys = redisTemplate.keys("$BLACKLIST_PREFIX*")
            keys?.size?.toLong() ?: 0L
        } catch (e: Exception) {
            logger.error("Ошибка при получении размера blacklist: ${e.message}", e)
            0L
        }
    }

    /**
     * Очищает весь blacklist (использовать осторожно!)
     */
    fun clearBlacklist() {
        try {
            val keys = redisTemplate.keys("$BLACKLIST_PREFIX*")
            if (keys != null && keys.isNotEmpty()) {
                redisTemplate.delete(keys)
                logger.info("Blacklist очищен. Удалено токенов: ${keys.size}")
            }
        } catch (e: Exception) {
            logger.error("Ошибка при очистке blacklist: ${e.message}", e)
        }
    }

    /**
     * Получает полный JWT токен из Redis по токену
     * @param token JWT токен для поиска
     * @return Полный JWT токен из Redis или null, если токен не найден
     */
    fun getTokenFromBlacklist(token: String): String? {
        return try {
            val key = getBlacklistKey(token)
            redisTemplate.opsForValue().get(key)
        } catch (e: Exception) {
            logger.error("Ошибка при получении токена из blacklist: ${e.message}", e)
            null
        }
    }

    /**
     * Проверяет подключение к Redis
     * @return true, если Redis доступен, false в противном случае
     */
    fun isRedisAvailable(): Boolean {
        return try {
            val connection = redisTemplate.connectionFactory?.connection
            if (connection != null) {
                connection.ping()
                connection.close()
                true
            } else {
                logger.error("Redis connectionFactory is null")
                false
            }
        } catch (e: Exception) {
            logger.error("Ошибка при проверке подключения к Redis: ${e.message}", e)
            false
        }
    }

    /**
     * Тестовый метод для проверки сохранения в Redis
     * @return true, если тест прошел успешно
     */
    fun testRedisConnection(): Boolean {
        return try {
            val testKey = "test:connection:${System.currentTimeMillis()}"
            val testValue = "test_value"

            redisTemplate.opsForValue().set(testKey, testValue, 10, TimeUnit.SECONDS)
            val retrievedValue = redisTemplate.opsForValue().get(testKey)

            val success = retrievedValue == testValue
            if (success) {
                logger.info("Тест подключения к Redis: УСПЕШНО")
                redisTemplate.delete(testKey)
            } else {
                logger.error("Тест подключения к Redis: НЕУДАЧА. Ожидалось: $testValue, Получено: $retrievedValue")
            }

            success
        } catch (e: Exception) {
            logger.error("Тест подключения к Redis: ОШИБКА - ${e.message}", e)
            false
        }
    }

    /**
     * Генерирует ключ для Redis на основе токена
     * Использует хеш токена для ключа (для производительности),
     * но полный токен сохраняется в значении
     */
    private fun getBlacklistKey(token: String): String {
        // Используем хеш токена для ключа (для быстрого поиска)
        // Полный токен хранится в значении
        val tokenHash = token.hashCode().toString()
        return "$BLACKLIST_PREFIX$tokenHash"
    }

    /**
     * Вычисляет TTL для токена в миллисекундах
     */
    private fun calculateTTL(claims: Claims): Long {
        val expiration = claims.expiration
        val now = System.currentTimeMillis()
        val expirationTime = expiration.time

        // TTL = время истечения - текущее время
        val ttl = expirationTime - now

        // Возвращаем TTL, но не меньше 1 минуты (на случай если токен уже почти истек)
        return maxOf(ttl, TimeUnit.MINUTES.toMillis(1))
    }
}
