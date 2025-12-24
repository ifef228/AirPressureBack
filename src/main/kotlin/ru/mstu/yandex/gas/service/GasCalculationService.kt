package ru.mstu.yandex.gas.service

import org.springframework.stereotype.Service
import ru.mstu.yandex.gas.entity.CalcOrder
import ru.mstu.yandex.gas.entity.GasOrder
import ru.mstu.yandex.gas.entity.OrderStatus
import ru.mstu.yandex.gas.repository.CalcOrderRepository
import ru.mstu.yandex.gas.repository.GasOrderRepository
import ru.mstu.yandex.gas.repository.GasRepository
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class GasCalculationService(
    private val calcOrderRepository: CalcOrderRepository,
    private val gasOrderRepository: GasOrderRepository,
    private val gasRepository: GasRepository
) {

    fun calculateTemperature(gasIds: List<Long>, concentrations: List<Double>, temperatures: List<Double>): Double {
        // Базовая температура атмосферы (15°C)
        var atmosphericTemperature = 15.0

        // Парниковый эффект от газов
        var greenhouseEffect = 0.0

        gasIds.forEachIndexed { index, gasId ->
            val concentration = concentrations.getOrNull(index) ?: 0.0
            val gasTemperature = temperatures.getOrNull(index) ?: 15

            when (gasId) {
                1L -> { // CO₂ - основной парниковый газ
                    // CO₂ имеет сильный парниковый эффект
                    greenhouseEffect += concentration * 0.8
                }
                2L -> { // O₂ - незначительный парниковый эффект
                    // Кислород практически не влияет на температуру
                    greenhouseEffect += concentration * 0.01
                }
                3L -> { // Ar - инертный газ, не влияет на температуру
                    // Аргон не участвует в парниковом эффекте
                    greenhouseEffect += 0.0
                }
                4L -> { // N₂ - незначительный парниковый эффект
                    // Азот имеет минимальный парниковый эффект
                    greenhouseEffect += concentration * 0.02
                }
                5L -> { // H₂O - сильный парниковый газ
                    // Водяной пар - самый сильный парниковый газ
                    greenhouseEffect += concentration * 1.2
                }
            }
        }

        // Рассчитываем итоговую температуру атмосферы
        // Парниковый эффект увеличивает температуру
        val finalTemperature = atmosphericTemperature + greenhouseEffect

        // Ограничиваем разумными пределами (-50°C до +50°C)
        val clampedTemperature = when {
            finalTemperature < -50.0 -> -50.0
            finalTemperature > 50.0 -> 50.0
            else -> finalTemperature
        }

        // Округляем до 1 знака после запятой
        return Math.round(clampedTemperature * 10) / 10.0
    }

    fun saveCalculation(
        userId: Long?,
        gasIds: List<Long>,
        concentrations: List<Double>,
        temperatures: List<Double>
    ): CalcOrder {
        // Создаем заказ на расчет
        val calcOrder = CalcOrder(
            userId = userId ?: 4L, // По умолчанию пользователь с ID = 4
            tempResult = calculateTemperature(gasIds, concentrations, temperatures),
            timestamp = LocalDateTime.now(),
            status = OrderStatus.COMPLETED,
            description = "Расчет атмосферного давления от ${LocalDateTime.now().toLocalDate()}"
        )

        val savedCalcOrder = calcOrderRepository.save(calcOrder)

        // Создаем записи для каждого газа
        gasIds.forEachIndexed { index, gasId ->
            val concentration = concentrations.getOrNull(index) ?: 0.0
            val temperature = temperatures.getOrNull(index) ?: 15.0

            val gasOrder = GasOrder(
                gasId = gasId,
                calcOrderId = savedCalcOrder.id!!, // Теперь ID точно не null
                concentration = concentration,
                temperature = temperature
            )

            gasOrderRepository.save(gasOrder)
        }

        return savedCalcOrder
    }

    fun getCalculationHistory(userId: Long?): List<CalcOrder> {
        return if (userId != null) {
            calcOrderRepository.findByUserIdOrderByTimestampDesc(userId)
        } else {
            calcOrderRepository.findAllOrderByTimestampDesc()
        }
    }

    fun getCalculationDetails(calcOrderId: Long): List<GasOrder> {
        return gasOrderRepository.findByCalcOrderId(calcOrderId)
    }
}
