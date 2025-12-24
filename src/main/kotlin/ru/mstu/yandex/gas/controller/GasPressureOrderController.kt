package ru.mstu.yandex.gas.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import ru.mstu.yandex.gas.service.GasCartService
import ru.mstu.yandex.gas.service.GasService

@Controller
@RequestMapping("/gas-pressure-orders")
class GasPressureOrderController(
    private val cartService: GasCartService,
    private val gasService: GasService
) {

    // GET - Просмотр текущей заявки (корзины)
    @GetMapping("/current")
    fun getCurrentOrder(model: Model): String {
        val activeCart = cartService.getActiveCartPublic()
        val cartItems = cartService.getCartItems()
        val cartGases = cartService.getCartGases(gasService)

        model.addAttribute("order", activeCart)
        model.addAttribute("cartItems", cartItems)
        model.addAttribute("cartGases", cartGases)
        model.addAttribute("cartCount", cartService.getCartItemsCount())

        return "pressure-order-current"
    }

    // GET - Просмотр заявки по ID
    @GetMapping("/{orderId}")
    fun getOrderById(@PathVariable orderId: Long, model: Model): String {
        val order = cartService.getOrderById(orderId)

        if (order == null) {
            model.addAttribute("error", "Заявка не найдена")
            return "error"
        }

        val gasOrders = cartService.getGasOrdersForCart(orderId)
        val gases = gasOrders.map { gasOrder ->
            gasService.getGasById(gasOrder.gasId)
        }.filterNotNull()

        model.addAttribute("order", order)
        model.addAttribute("gasOrders", gasOrders)
        model.addAttribute("gases", gases)

        return "pressure-order-detail"
    }

    // GET - Список всех заявок пользователя
    @GetMapping
    fun getAllOrders(model: Model): String {
        val orders = cartService.getUserOrders()
        model.addAttribute("orders", orders)
        return "pressure-orders-list"
    }

    // POST - Удаление заявки (логическое удаление через raw SQL)
    @PostMapping("/{orderId}/delete")
    fun deleteOrder(@PathVariable orderId: Long, model: Model): String {
        val success = cartService.deleteOrder(orderId)

        if (success) {
            model.addAttribute("message", "Заявка успешно удалена")
        } else {
            model.addAttribute("error", "Ошибка при удалении заявки")
        }

        return "redirect:/gas-pressure-orders"
    }
}
