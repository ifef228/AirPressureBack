package ru.mstu.yandex.gas.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import ru.mstu.yandex.gas.service.GasCartService
import ru.mstu.yandex.gas.util.RoleUtils
import org.springframework.beans.factory.annotation.Autowired

@Controller
@RequestMapping("/api/cart")
class GasCartController(
    private val cartService: GasCartService,
    @Autowired private val roleUtils: RoleUtils
) {

    // POST - Добавление газа в заявку на расчет атмосферного давления
    @PostMapping("/add/{gasId}")
    @ResponseBody
    fun addGasToPressureOrder(
        @PathVariable gasId: Long,
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<Map<String, Any>> {
        val userId = roleUtils.getUserIdFromToken(token)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Требуется авторизация"
            ))
        }
        cartService.addToCart(gasId, userId)
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Газ добавлен в заявку на расчет атмосферного давления",
            "cartCount" to cartService.getCartItemsCount(userId)
        ))
    }

    @PostMapping("/remove/{gasId}")
    @ResponseBody
    fun removeGasFromPressureOrder(
        @PathVariable gasId: Long,
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<Map<String, Any>> {
        println("PressureCartController: removeFromCart($gasId) - request received")
        val userId = roleUtils.getUserIdFromToken(token)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Требуется авторизация"
            ))
        }
        try {
            cartService.removeFromCart(gasId, userId)
            val cartCount = cartService.getCartItemsCount(userId)
            println("PressureCartController: removeFromCart($gasId) - cart count after removal: $cartCount")
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Газ удален из заявки на расчет атмосферного давления",
                "cartCount" to cartCount
            ))
        } catch (e: Exception) {
            println("PressureCartController: removeFromCart($gasId) - error: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.ok(mapOf(
                "success" to false,
                "message" to "Ошибка при удалении: ${e.message}",
                "cartCount" to cartService.getCartItemsCount(userId)
            ))
        }
    }

    @GetMapping("/count")
    @ResponseBody
    fun getPressureOrderCount(
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<Map<String, Any>> {
        val userId = roleUtils.getUserIdFromToken(token)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Требуется авторизация"
            ))
        }
        return ResponseEntity.ok(mapOf("count" to cartService.getCartItemsCount(userId)))
    }

    @GetMapping("/items")
    @ResponseBody
    fun getCartItems(
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<Map<String, Any>> {
        println("[GasCartController] getCartItems - token: ${token?.take(20)}...")
        val userId = roleUtils.getUserIdFromToken(token)
        println("[GasCartController] getCartItems - userId: $userId")
        if (userId == null) {
            println("[GasCartController] getCartItems - userId is null, returning 401")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Требуется авторизация"
            ))
        }
        val items = cartService.getCartItems(userId)
        println("[GasCartController] getCartItems - items: $items")
        return ResponseEntity.ok(mapOf("items" to items))
    }

    @PostMapping("/clear")
    @ResponseBody
    fun clearCart(
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<Map<String, Any>> {
        val userId = roleUtils.getUserIdFromToken(token)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Требуется авторизация"
            ))
        }
        cartService.clearCart(userId)
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Корзина очищена"
        ))
    }

    @GetMapping("/debug")
    @ResponseBody
    fun debugCart(
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<Map<String, Any>> {
        val userId = roleUtils.getUserIdFromToken(token)
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Требуется авторизация"
            ))
        }
        val activeCart = cartService.getActiveCartPublic(userId)
        val cartItems = cartService.getCartItems(userId)
        val cartCount = cartService.getCartItemsCount(userId)

        return ResponseEntity.ok(mapOf(
            "activeCart" to mapOf(
                "id" to activeCart.id,
                "userId" to activeCart.userId,
                "tempResult" to activeCart.tempResult,
                "timestamp" to activeCart.timestamp.toString()
            ),
            "cartItems" to cartItems,
            "cartCount" to cartCount
        ))
    }

    @GetMapping("/test")
    @ResponseBody
    fun testEndpoint(): ResponseEntity<Map<String, Any>> {
        println("CartController: test endpoint called")
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Test endpoint working",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
