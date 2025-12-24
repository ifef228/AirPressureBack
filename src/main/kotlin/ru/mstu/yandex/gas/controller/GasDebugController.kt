package ru.mstu.yandex.gas.controller

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import ru.mstu.yandex.gas.service.GasCartService
import ru.mstu.yandex.gas.service.GasService

@Controller
@RequestMapping("/debug")
class GasDebugController(
    private val cartService: GasCartService,
    private val gasService: GasService
) {

    @GetMapping("/cart/status")
    @ResponseBody
    fun getCartStatus(): ResponseEntity<Map<String, Any>> {
        val cartItems = cartService.getCartItems()
        val cartCount = cartService.getCartItemsCount()
        val allGases = gasService.getAllGases()

        val gasStatus = allGases.map { gas ->
            mapOf(
                "id" to gas.id,
                "name" to gas.name,
                "isInCart" to cartService.isInCart(gas.id)
            )
        }

        return ResponseEntity.ok(mapOf(
            "cartItems" to cartItems,
            "cartCount" to cartCount,
            "gasStatus" to gasStatus
        ))
    }

    @PostMapping("/cart/clear")
    @ResponseBody
    fun clearCart(): ResponseEntity<Map<String, Any>> {
        cartService.clearCart()
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Корзина очищена"
        ))
    }

    @PostMapping("/cart/add/{gasId}")
    @ResponseBody
    fun addToCart(@PathVariable gasId: Long): ResponseEntity<Map<String, Any>> {
        cartService.addToCart(gasId)
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Газ $gasId добавлен в корзину",
            "cartCount" to cartService.getCartItemsCount()
        ))
    }
}
