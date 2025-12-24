package ru.mstu.yandex.gas.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

    @Bean
    @Order(1) // Убеждаемся, что CORS фильтр выполняется первым
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()

        // ВАЖНО: allowCredentials = true несовместим с addAllowedOriginPattern("*")
        // Поэтому используем allowCredentials = false для большей гибкости
        config.allowCredentials = false // Отключаем для упрощения CORS

        // Для разработки разрешаем все origin'ы (так как allowCredentials = false)
        // В production нужно будет указать конкретные origin'ы
        config.addAllowedOriginPattern("*") // Разрешаем все origin'ы для разработки

        // Явно разрешаем GitHub Pages origin
        config.addAllowedOrigin("https://ifef228.github.io")
        config.addAllowedOriginPattern("https://*.github.io") // Разрешаем все GitHub Pages поддомены

        // Разрешаем ngrok (для туннелирования локального бэкенда)
        config.addAllowedOriginPattern("https://*.ngrok.io") // Все ngrok домены
        config.addAllowedOriginPattern("https://*.ngrok-free.app") // Новые ngrok домены

        // Явно разрешаем Tauri origin'ы (на случай, если паттерн не сработает)
        // Tauri может использовать null origin или tauri://localhost
        config.addAllowedOrigin("null") // Для Tauri приложений
        config.addAllowedOriginPattern("tauri://*") // Для Tauri протокола

        // Разрешаем все заголовки и методы
        config.addAllowedHeader("*")
        config.addAllowedMethod("*")

        // Явно разрешаем важные заголовки для авторизации
        config.addAllowedHeader("Authorization")
        config.addAllowedHeader("Content-Type")
        config.addAllowedHeader("Accept")
        config.addAllowedHeader("X-Requested-With")

        // Разрешаем заголовки, которые будут доступны клиенту
        config.addExposedHeader("Authorization")
        config.addExposedHeader("Content-Type")
        config.addExposedHeader("Access-Control-Allow-Origin")

        // Максимальное время кеширования preflight запросов
        config.maxAge = 3600L

        source.registerCorsConfiguration("/**", config)

        return CorsFilter(source)
    }
}
