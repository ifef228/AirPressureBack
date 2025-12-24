package ru.mstu.yandex.gas.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import ru.mstu.yandex.gas.service.GasService
import ru.mstu.yandex.gas.service.GasCartService

@Controller
@RequestMapping("/gas")
class GasDetailController(
    private val gasService: GasService,
    private val cartService: GasCartService
) {

    @GetMapping("/{id}")
    fun gasDetail(@PathVariable id: Long, model: Model): String {
        val gas = gasService.getGasById(id)
        if (gas == null) {
            model.addAttribute("errorMessage", "Газ с ID $id не найден.")
            return "error"
        }

        model.addAttribute("gas", gas)
        model.addAttribute("isInCart", cartService.isInCart(id))
        model.addAttribute("cartCount", cartService.getCartItemsCount())

        return "gas-detail"
    }
}
