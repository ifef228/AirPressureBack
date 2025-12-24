package ru.mstu.yandex.gas.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import ru.mstu.yandex.gas.service.GasService
import ru.mstu.yandex.gas.service.GasCartService

@Controller
@RequestMapping("/")
class GasHomeController(
    private val gasService: GasService,
    private val cartService: GasCartService
) {

    // GET - Поиск и получение газов для расчета атмосферного давления
    @GetMapping
    fun getAtmosphericGases(
        @RequestParam(required = false) gas_search: String?,
        model: Model
    ): String {
        val gases = if (gas_search.isNullOrBlank()) {
            gasService.getAllGases()
        } else {
            gasService.searchGases(gas_search)
        }

        // Добавляем информацию о состоянии корзины для каждого газа
        val gasesWithCartStatus = gases.map { gas ->
            mapOf(
                "gas" to gas,
                "isInCart" to cartService.isInCart(gas.id)
            )
        }

        model.addAttribute("gasesWithCartStatus", gasesWithCartStatus)
        model.addAttribute("searchQuery", gas_search ?: "")
        model.addAttribute("cartCount", cartService.getCartItemsCount())

        return "home"
    }
}
