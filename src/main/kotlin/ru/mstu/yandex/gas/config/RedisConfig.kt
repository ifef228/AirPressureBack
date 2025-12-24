package ru.mstu.yandex.gas.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
class RedisConfig {

    @Value("\${spring.data.redis.host:localhost}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port:6379}")
    private var redisPort: Int = 6379

    @Value("\${spring.data.redis.password:}")
    private var redisPassword: String? = null

    @Value("\${spring.data.redis.timeout:60000ms}")
    private lateinit var timeout: String

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val config = RedisStandaloneConfiguration(redisHost, redisPort)

        // Устанавливаем пароль, если он есть
        if (!redisPassword.isNullOrBlank()) {
            config.setPassword(redisPassword)
        }

        // Настройка Lettuce клиента
        val lettuceClientConfiguration = LettuceClientConfiguration.builder()
            .commandTimeout(parseDuration(timeout))
            .build()

        return LettuceConnectionFactory(config, lettuceClientConfiguration)
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory

        // Используем StringRedisSerializer для ключей и значений
        val stringSerializer = StringRedisSerializer()
        template.keySerializer = stringSerializer
        template.valueSerializer = stringSerializer
        template.hashKeySerializer = stringSerializer
        template.hashValueSerializer = stringSerializer

        template.afterPropertiesSet()
        return template
    }

    /**
     * Парсит строку с длительностью в Duration
     * Поддерживает форматы: "60000ms", "60s", "1m" и т.д.
     */
    private fun parseDuration(durationStr: String): Duration {
        return try {
            when {
                durationStr.endsWith("ms") -> {
                    val millis = durationStr.removeSuffix("ms").toLong()
                    Duration.ofMillis(millis)
                }
                durationStr.endsWith("s") -> {
                    val seconds = durationStr.removeSuffix("s").toLong()
                    Duration.ofSeconds(seconds)
                }
                durationStr.endsWith("m") -> {
                    val minutes = durationStr.removeSuffix("m").toLong()
                    Duration.ofMinutes(minutes)
                }
                else -> {
                    // По умолчанию считаем миллисекунды
                    Duration.ofMillis(durationStr.toLong())
                }
            }
        } catch (e: Exception) {
            // Если не удалось распарсить, возвращаем 60 секунд по умолчанию
            Duration.ofSeconds(60)
        }
    }
}
