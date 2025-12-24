package ru.mstu.yandex.gas.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import ru.mstu.yandex.gas.service.GasService
import ru.mstu.yandex.gas.service.GasCartService
import ru.mstu.yandex.gas.service.GasCalculationService

@Controller
@RequestMapping("/gas-temperature-calculate")
class GasCalculationController(
    private val gasService: GasService,
    private val cartService: GasCartService,
    private val calculationService: GasCalculationService
) {

    // GET - Страница расчета атмосферного давления
    @GetMapping
    fun pressureCalculationPage(model: Model): String {
        val orderGases = cartService.getCartGases(gasService)

        if (orderGases.isEmpty()) {
            model.addAttribute("message", "Заявка пуста. Добавьте газы для расчета атмосферного давления.")
            model.addAttribute("cartCount", cartService.getCartItemsCount())
            model.addAttribute("temperature", null) // Явно устанавливаем null
            return "gas-temperature-calculate-result"
        }

        // Получаем реальные данные из базы для расчета
        val activeCart = cartService.getActiveCartPublic()
        val gasOrders = cartService.getGasOrdersForCart(activeCart.id!!)

        val gasIds = gasOrders.map { it.gasId }
        val concentrations = gasOrders.map { it.concentration } // Концентрация уже в правильном формате
        val temperatures = gasOrders.map { it.temperature }

        val calculatedTemperature = calculationService.calculateTemperature(gasIds, concentrations, temperatures)

        model.addAttribute("gases", orderGases)
        model.addAttribute("calculatedTemperature", calculatedTemperature)
        model.addAttribute("gasesCount", orderGases.size)
        model.addAttribute("cartCount", cartService.getCartItemsCount())

        return "gas-temperature-calculate"
    }

    // POST - Выполнение расчета атмосферного давления
    @PostMapping("/calculate")
    fun calculateAtmosphericPressure(
        @RequestParam gasIds: List<Long>,
        @RequestParam concentrations: List<Double>,
        @RequestParam temperatures: List<Double>,
        model: Model
    ): String {
        // Сохраняем расчет в базу данных
        val calcOrder = calculationService.saveCalculation(
            userId = 4L, // Пользователь с ID = 4
            gasIds = gasIds,
            concentrations = concentrations,
            temperatures = temperatures
        )

        // Очищаем корзину после расчета
        cartService.clearCart()

        model.addAttribute("message", "Расчет атмосферного давления завершен успешно! Результат сохранен в системе.")
        model.addAttribute("calcOrderId", calcOrder.id)
        model.addAttribute("temperature", calcOrder.tempResult)

        return "gas-temperature-calculate-result"
    }
}
