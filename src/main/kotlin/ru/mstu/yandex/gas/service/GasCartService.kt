package ru.mstu.yandex.gas.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.mstu.yandex.gas.entity.GasOrder
import ru.mstu.yandex.gas.entity.OrderStatus
import ru.mstu.yandex.gas.repository.GasOrderRepository
import ru.mstu.yandex.gas.repository.CalcOrderRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import com.fasterxml.jackson.databind.ObjectMapper

@Service
class GasCartService(
    private val gasOrderRepository: GasOrderRepository,
    private val calcOrderRepository: CalcOrderRepository,
    @Value("\${async.service.url:http://localhost:8081}") private val asyncServiceUrl: String,
    @Value("\${async.service.enabled:true}") private val asyncServiceEnabled: Boolean,
    @Autowired private val objectMapper: ObjectMapper
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /**
     * Проверить доступность асинхронного сервиса
     */
    private fun checkAsyncServiceHealth(): Boolean {
        return try {
            val healthUrl = "$asyncServiceUrl/api/health"
            println("CartService: checkAsyncServiceHealth - checking $healthUrl")
            val request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val isHealthy = response.statusCode() == 200
            println("CartService: checkAsyncServiceHealth - service is ${if (isHealthy) "available" else "unavailable"} (status=${response.statusCode()})")
            isHealthy
        } catch (e: Exception) {
            println("CartService: checkAsyncServiceHealth - ERROR: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    companion object {
        private const val DEFAULT_USER_ID = 4L // Для обратной совместимости со старыми HTML контроллерами
    }

    // Перегрузки для обратной совместимости со старыми HTML контроллерами
    @Transactional
    @Deprecated("Используйте addToCart(gasId, userId) с реальным userId из токена", ReplaceWith("addToCart(gasId, DEFAULT_USER_ID)"))
    fun addToCart(gasId: Long) {
        addToCart(gasId, DEFAULT_USER_ID)
    }

    @Transactional
    fun addToCart(gasId: Long, userId: Long) {
        // Получаем активную корзину (всегда с ID из базы)
        val activeCart = getActiveCart(userId)

        // Проверяем, не добавлен ли уже этот газ в активную корзину
        val existingGasOrder = gasOrderRepository.findByGasIdAndCalcOrderId(gasId, activeCart.id!!)
        if (existingGasOrder == null) {
            val gasOrder = GasOrder(
                gasId = gasId,
                calcOrderId = activeCart.id,
                concentration = getDefaultConcentration(gasId).toDouble(),
                temperature = 15.0
            )
            gasOrderRepository.save(gasOrder)
        }
    }

    // Перегрузки для обратной совместимости со старыми HTML контроллерами
    @Transactional
    @Deprecated("Используйте removeFromCart(gasId, userId) с реальным userId из токена", ReplaceWith("removeFromCart(gasId, DEFAULT_USER_ID)"))
    fun removeFromCart(gasId: Long) {
        removeFromCart(gasId, DEFAULT_USER_ID)
    }

    @Transactional
    fun removeFromCart(gasId: Long, userId: Long) {
        println("CartService: removeFromCart($gasId) - starting for user ID: $userId")
        val activeCart = getActiveCart(userId)
        println("CartService: removeFromCart($gasId) - active cart ID: ${activeCart.id}, user ID: ${activeCart.userId}")

        // Проверяем все записи в корзине
        val allGasOrders = gasOrderRepository.findByCalcOrderId(activeCart.id!!)
        println("CartService: removeFromCart($gasId) - all gas orders in cart count: ${allGasOrders.size}")

        // Проверяем все записи с этим gasId
        val allGasOrdersWithThisId = gasOrderRepository.findByGasId(gasId)
        println("CartService: removeFromCart($gasId) - all gas orders with gasId $gasId count: ${allGasOrdersWithThisId.size}")

        // Проверяем, существует ли запись перед удалением
        val recordCount = gasOrderRepository.countByGasIdAndCalcOrderId(gasId, activeCart.id!!)
        println("CartService: removeFromCart($gasId) - record count before delete: $recordCount")

        // Используем новый метод удаления напрямую
        println("CartService: removeFromCart($gasId) - attempting direct delete with gasId=$gasId, calcOrderId=${activeCart.id}")
        val deletedCount = gasOrderRepository.deleteByGasIdAndCalcOrderId(gasId, activeCart.id!!)
        println("CartService: removeFromCart($gasId) - deleteByGasIdAndCalcOrderId returned: $deletedCount")

        if (deletedCount > 0) {
            println("CartService: removeFromCart($gasId) - successfully deleted $deletedCount record(s)")

            // Проверяем, что запись действительно удалена
            val remainingGasOrders = gasOrderRepository.findByCalcOrderId(activeCart.id!!)
            println("CartService: removeFromCart($gasId) - remaining gas orders in cart count: ${remainingGasOrders.size}")
        } else {
            println("CartService: removeFromCart($gasId) - no records were deleted")
            println("CartService: removeFromCart($gasId) - searched for gasId=$gasId, calcOrderId=${activeCart.id}")
        }
    }

    // Перегрузки для обратной совместимости со старыми HTML контроллерами
    @Deprecated("Используйте getCartItems(userId) с реальным userId из токена", ReplaceWith("getCartItems(DEFAULT_USER_ID)"))
    fun getCartItems(): List<Long> {
        return getCartItems(DEFAULT_USER_ID)
    }

    fun getCartItems(userId: Long): List<Long> {
        val activeCart = getActiveCart(userId)
        return gasOrderRepository.findByCalcOrderId(activeCart.id!!).map { it.gasId }
    }

    @Deprecated("Используйте getCartItemsCount(userId) с реальным userId из токена", ReplaceWith("getCartItemsCount(DEFAULT_USER_ID)"))
    fun getCartItemsCount(): Int {
        return getCartItemsCount(DEFAULT_USER_ID)
    }

    fun getCartItemsCount(userId: Long): Int {
        val count = getCartItems(userId).size
        println("CartService: getCartItemsCount() = $count for user $userId")
        return count
    }

    @Deprecated("Используйте isInCart(gasId, userId) с реальным userId из токена", ReplaceWith("isInCart(gasId, DEFAULT_USER_ID)"))
    fun isInCart(gasId: Long): Boolean {
        return isInCart(gasId, DEFAULT_USER_ID)
    }

    fun isInCart(gasId: Long, userId: Long): Boolean {
        val items = getCartItems(userId)
        val isInCart = items.contains(gasId)
        println("CartService: isInCart($gasId) = $isInCart, items = $items")
        return isInCart
    }

    @Deprecated("Используйте clearCart(userId) с реальным userId из токена", ReplaceWith("clearCart(DEFAULT_USER_ID)"))
    fun clearCart() {
        clearCart(DEFAULT_USER_ID)
    }

    fun clearCart(userId: Long) {
        val activeCart = getActiveCart(userId)
        gasOrderRepository.deleteAll(gasOrderRepository.findByCalcOrderId(activeCart.id!!))
    }

    @Deprecated("Используйте getCartGases(gasService, userId) с реальным userId из токена", ReplaceWith("getCartGases(gasService, DEFAULT_USER_ID)"))
    fun getCartGases(gasService: GasService): List<ru.mstu.yandex.gas.model.SimpleGasModel> {
        return getCartGases(gasService, DEFAULT_USER_ID)
    }

    fun getCartGases(gasService: GasService, userId: Long): List<ru.mstu.yandex.gas.model.SimpleGasModel> {
        val cartItemIds = getCartItems(userId)
        val allGases = gasService.getAllGases()
        val cartGases = allGases.filter { gas -> cartItemIds.contains(gas.id) }

        // Получаем реальные данные из базы для каждого газа в корзине
        val activeCart = getActiveCart(userId)
        return cartGases.map { gas ->
            val gasOrder = gasOrderRepository.findByGasIdAndCalcOrderId(gas.id, activeCart.id!!)
            val concentration = gasOrder?.concentration ?: 0.0
            val concentrationPercent = concentration // Уже в процентах (0-100)

            ru.mstu.yandex.gas.model.SimpleGasModel(
                id = gas.id,
                name = gas.name,
                concentration = "${String.format("%.2f", concentrationPercent)}%",
                temperature = "${gasOrder?.temperature ?: 15}°C",
                image = gas.image
            )
        }
    }

    @Deprecated("Используйте getActiveCartPublic(userId) с реальным userId из токена", ReplaceWith("getActiveCartPublic(DEFAULT_USER_ID)"))
    fun getActiveCartPublic(): ru.mstu.yandex.gas.entity.CalcOrder {
        return getActiveCartPublic(DEFAULT_USER_ID)
    }

    fun getActiveCartPublic(userId: Long): ru.mstu.yandex.gas.entity.CalcOrder {
        return getActiveCart(userId)
    }

    fun getGasOrdersForCart(calcOrderId: Long): List<ru.mstu.yandex.gas.entity.GasOrder> {
        return gasOrderRepository.findByCalcOrderId(calcOrderId)
    }

    fun getGasOrderInOrder(orderId: Long, gasId: Long): ru.mstu.yandex.gas.entity.GasOrder? {
        return gasOrderRepository.findByCalcOrderIdAndGasId(orderId, gasId)
    }

    /**
     * Рассчитать температуру для заявки на основе текущих параметров газов
     */
    fun calculateTemperatureForOrderPublic(orderId: Long): Double {
        return calculateTemperatureForOrder(orderId)
    }

    @Deprecated("Используйте deleteOrder(orderId, userId) с реальным userId из токена", ReplaceWith("deleteOrder(orderId, DEFAULT_USER_ID)"))
    @Transactional
    fun deleteOrder(orderId: Long): Boolean {
        return deleteOrder(orderId, DEFAULT_USER_ID)
    }

    @Transactional
    fun deleteOrder(orderId: Long, userId: Long): Boolean {
        return try {
            val deletedCount = calcOrderRepository.deleteOrderByIdAndUserId(orderId, userId)
            deletedCount > 0
        } catch (e: Exception) {
            println("CartService: deleteOrder($orderId) - error: ${e.message}")
            false
        }
    }

    fun getOrderById(orderId: Long): ru.mstu.yandex.gas.entity.CalcOrder? {
        return calcOrderRepository.findById(orderId).orElse(null)
    }

    @Deprecated("Используйте getUserOrders(userId) с реальным userId из токена", ReplaceWith("getUserOrders(DEFAULT_USER_ID)"))
    fun getUserOrders(): List<ru.mstu.yandex.gas.entity.CalcOrder> {
        return getUserOrders(DEFAULT_USER_ID)
    }

    fun getUserOrders(userId: Long): List<ru.mstu.yandex.gas.entity.CalcOrder> {
        return calcOrderRepository.findByUserIdAndStatusOrderByTimestampDesc(userId, OrderStatus.COMPLETED)
    }

    fun getActiveCart(userId: Long): ru.mstu.yandex.gas.entity.CalcOrder {
        // Ищем активную корзину (заказ со статусом DRAFT) для пользователя
        // Ищем самый поздний черновик, независимо от tempResult (так как после обновления концентрации tempResult может быть установлен)
        val activeCart = calcOrderRepository.findTopByUserIdAndStatusOrderByTimestampDesc(
            userId,
            OrderStatus.DRAFT
        )
        println("CartService: getActiveCart() - found active cart: ${activeCart?.id} for user $userId")

        return activeCart ?: run {
            // Создаем новую корзину если нет активной
            val newCart = ru.mstu.yandex.gas.entity.CalcOrder(
                userId = userId,
                tempResult = null,
                timestamp = LocalDateTime.now(),
                status = OrderStatus.DRAFT,
                description = "Корзина для расчета атмосферного давления"
            )
            // Сохраняем в базу и возвращаем с реальным ID
            val savedCart = calcOrderRepository.save(newCart)
            println("CartService: getActiveCart() - created new cart: ${savedCart.id} for user $userId")
            savedCart
        }
    }

    private fun getDefaultConcentration(gasId: Long): Int {
        // По умолчанию концентрация 1% для всех газов
        return 1  // 1% (хранится как 1.0 в базе)
    }

    // Методы для API
    fun updateOrder(orderId: Long, updateOrderDto: ru.mstu.yandex.gas.dto.UpdateOrderDto): ru.mstu.yandex.gas.entity.CalcOrder? {
        return try {
            val order = calcOrderRepository.findById(orderId).orElse(null)
            if (order != null && order.status == OrderStatus.DRAFT) {
                val updatedOrder = order.copy(description = updateOrderDto.description ?: order.description)
                calcOrderRepository.save(updatedOrder)
            } else {
                null
            }
        } catch (e: Exception) {
            println("CartService: updateOrder($orderId) - error: ${e.message}")
            null
        }
    }

    fun formOrder(orderId: Long): ru.mstu.yandex.gas.entity.CalcOrder? {
        return try {
            val order = calcOrderRepository.findById(orderId).orElse(null)
            if (order != null && order.status == OrderStatus.DRAFT) {
                val formedOrder = order.copy(status = OrderStatus.FORMED)
                calcOrderRepository.save(formedOrder)
            } else {
                null
            }
        } catch (e: Exception) {
            println("CartService: formOrder($orderId) - error: ${e.message}")
            null
        }
    }

    fun completeOrder(orderId: Long): ru.mstu.yandex.gas.entity.CalcOrder? {
        return try {
            val order = calcOrderRepository.findById(orderId).orElse(null)
            if (order != null && order.status == OrderStatus.COMPLETED) {
                // Здесь можно добавить расчет стоимости/доставки
                val completedOrder = order.copy(
                    tempResult = calculateTemperatureForOrder(orderId)
                )
                calcOrderRepository.save(completedOrder)
            } else {
                null
            }
        } catch (e: Exception) {
            println("CartService: completeOrder($orderId) - error: ${e.message}")
            null
        }
    }

    fun addGasToOrder(orderId: Long, gasId: Long, concentration: Double, temperature: Double): Boolean {
        return try {
            val order = calcOrderRepository.findById(orderId).orElse(null)
            if (order != null && order.status == OrderStatus.DRAFT) {
                val gasOrder = ru.mstu.yandex.gas.entity.GasOrder(
                    gasId = gasId,
                    calcOrderId = orderId,
                    concentration = concentration, // Хранится как есть (0-100)
                    temperature = temperature
                )
                gasOrderRepository.save(gasOrder)

                // Пересчитываем температуру заявки после добавления газа
                val newTemperature = calculateTemperatureForOrder(orderId)
                val updatedOrder = order.copy(tempResult = newTemperature)
                calcOrderRepository.save(updatedOrder)

                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("CartService: addGasToOrder($orderId, $gasId) - error: ${e.message}")
            false
        }
    }

    fun updateGasInOrder(orderId: Long, gasId: Long, concentration: Double?, temperature: Double?): Boolean {
        return try {
            val gasOrder = gasOrderRepository.findByCalcOrderIdAndGasId(orderId, gasId)
            if (gasOrder != null) {
                val updatedGasOrder = gasOrder.copy(
                    concentration = concentration ?: gasOrder.concentration, // Хранится как есть (0-100)
                    temperature = temperature ?: gasOrder.temperature
                )
                gasOrderRepository.save(updatedGasOrder)

                // Пересчитываем температуру заявки после изменения параметров газа
                val order = calcOrderRepository.findById(orderId).orElse(null)
                if (order != null && order.status == OrderStatus.DRAFT) {
                    val newTemperature = calculateTemperatureForOrder(orderId)
                    val updatedOrder = order.copy(tempResult = newTemperature)
                    calcOrderRepository.save(updatedOrder)
                }

                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("CartService: updateGasInOrder($orderId, $gasId) - error: ${e.message}")
            false
        }
    }

    fun removeGasFromOrder(orderId: Long, gasId: Long): Boolean {
        return try {
            val gasOrder = gasOrderRepository.findByCalcOrderIdAndGasId(orderId, gasId)
            if (gasOrder != null) {
                gasOrderRepository.delete(gasOrder)

                // Пересчитываем температуру заявки после удаления газа
                val order = calcOrderRepository.findById(orderId).orElse(null)
                if (order != null && order.status == OrderStatus.DRAFT) {
                    val newTemperature = calculateTemperatureForOrder(orderId)
                    val updatedOrder = order.copy(tempResult = newTemperature)
                    calcOrderRepository.save(updatedOrder)
                }

                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("CartService: removeGasFromOrder($orderId, $gasId) - error: ${e.message}")
            false
        }
    }

    fun getOrdersWithFilter(filter: ru.mstu.yandex.gas.dto.OrderFilterDto, pageable: org.springframework.data.domain.Pageable): List<ru.mstu.yandex.gas.entity.CalcOrder> {
        return try {
            val statusFilter = when (filter.status) {
                "COMPLETED" -> OrderStatus.COMPLETED
                "FORMED" -> OrderStatus.FORMED
                "CANCELLED" -> OrderStatus.CANCELLED
                "DRAFT" -> OrderStatus.DRAFT
                else -> null
            }

            // Парсим даты (только дата, без времени)
            val dateFrom = filter.formedDateFrom?.let {
                try {
                    java.time.LocalDateTime.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
            val dateTo = filter.formedDateTo?.let {
                try {
                    java.time.LocalDateTime.parse(it)
                } catch (e: Exception) {
                    null
                }
            }

            when {
                // Фильтрация по статусу и дате
                statusFilter != null && dateFrom != null && dateTo != null -> {
                    calcOrderRepository.findByStatusAndTimestampBetweenOrderByTimestampDesc(
                        statusFilter, dateFrom, dateTo, pageable
                    ).content
                }
                // Фильтрация по дате без статуса
                dateFrom != null && dateTo != null -> {
                    calcOrderRepository.findByStatusNotAndTimestampBetweenOrderByTimestampDesc(
                        OrderStatus.DELETED, dateFrom, dateTo, pageable
                    ).content
                }
                // Фильтрация только по статусу
                statusFilter != null -> {
                    calcOrderRepository.findByStatusOrderByTimestampDesc(statusFilter, pageable).content
                }
                // Без фильтров (исключаем удаленные)
                else -> {
                    calcOrderRepository.findByStatusNotOrderByTimestampDesc(OrderStatus.DELETED, pageable).content
                }
            }
        } catch (e: Exception) {
            println("CartService: getOrdersWithFilter - error: ${e.message}")
            emptyList()
        }
    }

    fun getOrdersCount(filter: ru.mstu.yandex.gas.dto.OrderFilterDto): Long {
        return try {
            val statusFilter = when (filter.status) {
                "COMPLETED" -> OrderStatus.COMPLETED
                "FORMED" -> OrderStatus.FORMED
                "CANCELLED" -> OrderStatus.CANCELLED
                "DRAFT" -> OrderStatus.DRAFT
                else -> null
            }

            // Парсим даты
            val dateFrom = filter.formedDateFrom?.let {
                try {
                    java.time.LocalDateTime.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
            val dateTo = filter.formedDateTo?.let {
                try {
                    java.time.LocalDateTime.parse(it)
                } catch (e: Exception) {
                    null
                }
            }

            when {
                // Фильтрация по статусу и дате
                statusFilter != null && dateFrom != null && dateTo != null -> {
                    calcOrderRepository.countByStatusAndTimestampBetween(statusFilter, dateFrom, dateTo)
                }
                // Фильтрация по дате без статуса
                dateFrom != null && dateTo != null -> {
                    calcOrderRepository.countByStatusNotAndTimestampBetween(OrderStatus.DELETED, dateFrom, dateTo)
                }
                // Фильтрация только по статусу
                statusFilter != null -> {
                    calcOrderRepository.countByStatus(statusFilter)
                }
                // Без фильтров
                else -> {
                    calcOrderRepository.countByStatusNot(OrderStatus.DELETED)
                }
            }
        } catch (e: Exception) {
            println("CartService: getOrdersCount - error: ${e.message}")
            0L
        }
    }

    /**
     * Получить заявки по userId с фильтрацией
     */
    fun getOrdersByUserId(userId: Long, filter: ru.mstu.yandex.gas.dto.OrderFilterDto, pageable: org.springframework.data.domain.Pageable): List<ru.mstu.yandex.gas.entity.CalcOrder> {
        return try {
            val statusFilter = when (filter.status) {
                "COMPLETED" -> OrderStatus.COMPLETED
                "FORMED" -> OrderStatus.FORMED
                "CANCELLED" -> OrderStatus.CANCELLED
                else -> null
            }

            if (statusFilter != null) {
                calcOrderRepository.findByUserIdAndStatusOrderByTimestampDesc(userId, statusFilter, pageable).content
            } else {
                // Исключаем удаленные и черновики
                calcOrderRepository.findByUserIdAndStatusNotOrderByTimestampDesc(userId, OrderStatus.DELETED, pageable).content
            }
        } catch (e: Exception) {
            println("CartService: getOrdersByUserId - error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Получить количество заявок по userId с фильтрацией
     */
    fun getOrdersCountByUserId(userId: Long, filter: ru.mstu.yandex.gas.dto.OrderFilterDto): Long {
        return try {
            val statusFilter = when (filter.status) {
                "COMPLETED" -> OrderStatus.COMPLETED
                "FORMED" -> OrderStatus.FORMED
                "CANCELLED" -> OrderStatus.CANCELLED
                else -> null
            }

            if (statusFilter != null) {
                calcOrderRepository.countByUserIdAndStatus(userId, statusFilter)
            } else {
                // Исключаем удаленные и черновики
                calcOrderRepository.countByUserIdAndStatusNot(userId, OrderStatus.DELETED)
            }
        } catch (e: Exception) {
            println("CartService: getOrdersCountByUserId - error: ${e.message}")
            0L
        }
    }

    @Transactional
    fun approveOrder(orderId: Long, comment: String?, moderatorLogin: String?): ru.mstu.yandex.gas.entity.CalcOrder? {
        println("=== CartService: approveOrder START for orderId=$orderId ===")
        return try {
            val order = calcOrderRepository.findById(orderId).orElse(null)
            println("CartService: approveOrder - order found: ${order != null}, status: ${order?.status}")

            if (order != null && order.status == OrderStatus.FORMED) {
                val descriptionWithModerator = if (moderatorLogin != null) {
                    "${order.description ?: ""}\nМодератор: $moderatorLogin"
                } else {
                    order.description
                }

                // Получаем gasOrders для отправки в асинхронный сервис
                val gasOrders = getGasOrdersForCart(orderId)
                println("CartService: approveOrder - gasOrders count: ${gasOrders.size}")
                gasOrders.forEach { go ->
                    println("CartService: approveOrder - gasOrder: id=${go.id}, gasId=${go.gasId}, result=${go.result}")
                }

                // НЕ рассчитываем температуру сразу - она будет рассчитана асинхронным сервисом
                // tempResult остается null или текущим значением до получения результатов
                println("CartService: approveOrder - NOT calculating tempResult immediately, will be calculated by async service")

                val approvedOrder = order.copy(
                    status = OrderStatus.COMPLETED,
                    tempResult = null, // Температура будет рассчитана асинхронным сервисом
                    description = comment?.let { "$descriptionWithModerator\nКомментарий модератора: $it" } ?: descriptionWithModerator
                )
                val savedOrder = calcOrderRepository.save(approvedOrder)
                println("CartService: approveOrder - saved order with tempResult: ${savedOrder.tempResult} (will be updated by async service)")

                // Отправляем задачи в асинхронный сервис для расчета
                println("CartService: approveOrder - checking conditions: asyncServiceEnabled=$asyncServiceEnabled, savedOrder.id=${savedOrder.id}, gasOrders.size=${gasOrders.size}")

                if (asyncServiceEnabled && savedOrder.id != null && gasOrders.isNotEmpty()) {
                    println("CartService: approveOrder - ALL conditions met, sending tasks to async service...")
                    println("CartService: approveOrder - asyncServiceUrl=$asyncServiceUrl")
                    sendTasksToAsyncService(savedOrder.id!!, gasOrders)
                } else {
                    println("CartService: approveOrder - conditions NOT met:")
                    println("  - asyncServiceEnabled=$asyncServiceEnabled")
                    println("  - savedOrder.id=${savedOrder.id}")
                    println("  - gasOrders.size=${gasOrders.size}")

                    // Если асинхронный сервис отключен, рассчитываем температуру стандартным способом
                    if (!asyncServiceEnabled) {
                        println("CartService: approveOrder - async service disabled, calculating tempResult using standard formula")
                        val tempResult = calculateTemperatureForOrder(orderId)
                        val updatedOrder = savedOrder.copy(tempResult = tempResult)
                        calcOrderRepository.save(updatedOrder)
                        println("CartService: approveOrder - calculated and saved tempResult: $tempResult")
                    } else if (savedOrder.id == null) {
                        println("CartService: approveOrder - ERROR: savedOrder.id is null, cannot send tasks")
                    } else if (gasOrders.isEmpty()) {
                        println("CartService: approveOrder - WARNING: gasOrders is empty, no tasks to send")
                    }
                }

                println("=== CartService: approveOrder END for orderId=$orderId ===")
                savedOrder
            } else {
                println("CartService: approveOrder - order is null or status is not FORMED")
                null
            }
        } catch (e: Exception) {
            println("CartService: approveOrder($orderId) - error: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Отправить задачи в асинхронный сервис для расчета
     */
    private fun sendTasksToAsyncService(calcOrderId: Long, gasOrders: List<ru.mstu.yandex.gas.entity.GasOrder>) {
        println("=== CartService: sendTasksToAsyncService START for calcOrderId=$calcOrderId ===")
        println("CartService: sendTasksToAsyncService - asyncServiceUrl=$asyncServiceUrl, enabled=$asyncServiceEnabled")
        println("CartService: sendTasksToAsyncService - gasOrders count: ${gasOrders.size}")

        if (!asyncServiceEnabled) {
            println("CartService: sendTasksToAsyncService - ERROR: async service is DISABLED!")
            return
        }

        if (asyncServiceUrl.isBlank()) {
            println("CartService: sendTasksToAsyncService - ERROR: asyncServiceUrl is BLANK!")
            return
        }

        if (gasOrders.isEmpty()) {
            println("CartService: sendTasksToAsyncService - WARNING: gasOrders list is EMPTY!")
            return
        }

        // Проверяем доступность сервиса перед отправкой задач
        if (!checkAsyncServiceHealth()) {
            println("CartService: sendTasksToAsyncService - ERROR: async service is not available! Skipping task creation.")
            return
        }

        try {
            for (gasOrder in gasOrders) {
                if (gasOrder.id != null) {
                    // Передаем calcOrderId:gasId:concentration для расчета с учетом концентрации
                    // ВАЖНО: используем точный формат для парсинга в Go
                    val dataString = "$calcOrderId:${gasOrder.gasId}:${gasOrder.concentration}"
                    val taskData = mapOf(
                        "type" to "calculate",
                        "data" to dataString
                    )
                    println("CartService: sendTasksToAsyncService - preparing task:")
                    println("  - gasOrder.id=${gasOrder.id}")
                    println("  - calcOrderId=$calcOrderId")
                    println("  - gasId=${gasOrder.gasId}")
                    println("  - concentration=${gasOrder.concentration}")
                    println("  - data string='$dataString'")
                    println("  - data string length=${dataString.length}")

                    val requestBody = objectMapper.writeValueAsString(taskData)
                    println("CartService: sendTasksToAsyncService - request body: $requestBody")

                    val requestUrl = "$asyncServiceUrl/api/tasks"
                    println("CartService: sendTasksToAsyncService - sending POST to $requestUrl")
                    println("CartService: sendTasksToAsyncService - HTTP client timeout: ${httpClient.connectTimeout()}")

                    try {
                        val request = HttpRequest.newBuilder()
                            .uri(URI.create(requestUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .timeout(Duration.ofSeconds(10))
                            .build()

                        println("CartService: sendTasksToAsyncService - request created, sending...")
                        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                        println("CartService: sendTasksToAsyncService - response received!")
                        println("CartService: sendTasksToAsyncService - response status: ${response.statusCode()}")
                        println("CartService: sendTasksToAsyncService - response body: ${response.body()}")

                        if (response.statusCode() != 201) {
                            println("CartService: ERROR - Failed to send task to async service for gasOrder ${gasOrder.id}: status=${response.statusCode()}, body=${response.body()}")
                        } else {
                            println("CartService: SUCCESS - Task sent to async service for gasOrder ${gasOrder.id}, calcOrder $calcOrderId")
                        }
                    } catch (e: java.net.ConnectException) {
                        println("CartService: ERROR - Connection refused! Cannot connect to $requestUrl")
                        println("CartService: ERROR - Is async service running? Check: curl $requestUrl")
                        e.printStackTrace()
                    } catch (e: java.net.UnknownHostException) {
                        println("CartService: ERROR - Unknown host! Cannot resolve hostname in $requestUrl")
                        e.printStackTrace()
                    } catch (e: java.util.concurrent.TimeoutException) {
                        println("CartService: ERROR - Request timeout! Async service did not respond in time")
                        e.printStackTrace()
                    } catch (e: Exception) {
                        println("CartService: ERROR - Exception while sending request: ${e.javaClass.simpleName}: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    println("CartService: sendTasksToAsyncService - WARNING: gasOrder.id is null, skipping")
                }
            }
            println("=== CartService: sendTasksToAsyncService END for calcOrderId=$calcOrderId ===")
        } catch (e: Exception) {
            println("CartService: ERROR - Exception sending tasks to async service: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Рассчитать температуру на основе результатов от асинхронного сервиса
     */
    private fun calculateTemperatureFromResults(gasOrders: List<ru.mstu.yandex.gas.entity.GasOrder>): Double {
        println("=== CartService: calculateTemperatureFromResults START ===")
        println("CartService: calculateTemperatureFromResults - total gasOrders: ${gasOrders.size}")

        val gasOrdersWithResults = gasOrders.filter { it.result != null }
        println("CartService: calculateTemperatureFromResults - gasOrders with results: ${gasOrdersWithResults.size}")

        gasOrdersWithResults.forEach { go ->
            println("CartService: calculateTemperatureFromResults - gasOrder: id=${go.id}, gasId=${go.gasId}, result=${go.result}, concentration=${go.concentration}")
        }

        return if (gasOrdersWithResults.isNotEmpty()) {
            // Используем средневзвешенную температуру на основе результатов и концентраций
            val weightedSum = gasOrdersWithResults.sumOf { gasOrder ->
                val result = gasOrder.result ?: 0.0
                val concentration = gasOrder.concentration / 100.0 // Конвертируем в доли
                val weighted = result * concentration
                println("CartService: calculateTemperatureFromResults - weighted: result=$result * concentration=$concentration = $weighted")
                weighted
            }
            val totalConcentration = gasOrdersWithResults.sumOf { it.concentration / 100.0 }
            println("CartService: calculateTemperatureFromResults - weightedSum=$weightedSum, totalConcentration=$totalConcentration")

            val finalTemp = if (totalConcentration > 0) {
                weightedSum / totalConcentration
            } else {
                // Если концентрация равна нулю, используем простое среднее результатов
                gasOrdersWithResults.mapNotNull { it.result }.average()
            }
            println("CartService: calculateTemperatureFromResults - finalTemp: $finalTemp")
            println("=== CartService: calculateTemperatureFromResults END ===")
            finalTemp
        } else {
            // Если нет результатов, используем стандартную формулу
            println("CartService: calculateTemperatureFromResults - no results, using average temperature")
            val avgTemp = gasOrders.map { it.temperature }.average()
            println("CartService: calculateTemperatureFromResults - average temperature: $avgTemp")
            println("=== CartService: calculateTemperatureFromResults END ===")
            avgTemp
        }
    }

    @Transactional
    fun rejectOrder(orderId: Long, comment: String?, moderatorLogin: String?): ru.mstu.yandex.gas.entity.CalcOrder? {
        return try {
            val order = calcOrderRepository.findById(orderId).orElse(null)
            if (order != null && order.status == OrderStatus.FORMED) {
                val descriptionWithModerator = if (moderatorLogin != null) {
                    "${order.description ?: ""}\nМодератор: $moderatorLogin"
                } else {
                    order.description
                }
                val rejectedOrder = order.copy(
                    status = OrderStatus.CANCELLED,
                    description = comment?.let { "$descriptionWithModerator\nПричина отклонения: $it" } ?: descriptionWithModerator
                )
                calcOrderRepository.save(rejectedOrder)
            } else {
                null
            }
        } catch (e: Exception) {
            println("CartService: rejectOrder($orderId) - error: ${e.message}")
            null
        }
    }

    private fun calculateTemperatureForOrder(orderId: Long): Double {
        // Улучшенная формула расчета температуры на основе газов в заявке
        val gasOrders = getGasOrdersForCart(orderId)
        return if (gasOrders.isNotEmpty()) {
            // Получаем коэффициенты теплоемкости для каждого газа
            val gasHeatCapacities = gasOrders.map { gasOrder ->
                val heatCapacity = getGasHeatCapacity(gasOrder.gasId)
                val concentration = gasOrder.concentration / 100.0 // Конвертируем в доли
                val temperature = gasOrder.temperature

                // Учитываем теплоемкость и концентрацию
                Triple(heatCapacity, concentration, temperature)
            }

            // Рассчитываем средневзвешенную температуру с учетом теплоемкости
            val weightedSum = gasHeatCapacities.sumOf { (heatCapacity, concentration, temperature) ->
                heatCapacity * concentration * temperature
            }
            val totalWeight = gasHeatCapacities.sumOf { (heatCapacity, concentration, _) ->
                heatCapacity * concentration
            }

            if (totalWeight > 0) {
                weightedSum / totalWeight
            } else {
                // Если общий вес равен нулю, используем простое среднее
                gasOrders.map { it.temperature }.average()
            }
        } else {
            15.0 // Базовая температура
        }
    }

    /**
     * Получить коэффициент теплоемкости для газа (Дж/(моль·К))
     * Эти значения приблизительные для стандартных условий
     */
    private fun getGasHeatCapacity(gasId: Long): Double {
        return when (gasId) {
            1L -> 37.11  // CO₂: 37.11 Дж/(моль·К)
            2L -> 29.37  // O₂: 29.37 Дж/(моль·К)
            3L -> 20.79  // Ar: 20.79 Дж/(моль·К)
            4L -> 29.12  // N₂: 29.12 Дж/(моль·К)
            5L -> 33.58  // H₂O: 33.58 Дж/(моль·К)
            else -> 25.0 // Среднее значение для неизвестных газов
        }
    }
}
