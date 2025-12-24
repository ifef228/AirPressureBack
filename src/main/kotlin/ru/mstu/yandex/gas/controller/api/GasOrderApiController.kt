package ru.mstu.yandex.gas.controller.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.mstu.yandex.gas.dto.*
import ru.mstu.yandex.gas.entity.OrderStatus
import ru.mstu.yandex.gas.entity.GasOrder
import ru.mstu.yandex.gas.repository.GasOrderRepository
import ru.mstu.yandex.gas.repository.CalcOrderRepository
import ru.mstu.yandex.gas.service.GasCartService
import ru.mstu.yandex.gas.service.GasService
import ru.mstu.yandex.gas.util.RoleUtils
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/gas-orders")
@Tag(name = "Orders", description = "API для управления заявками")
class GasOrderApiController(
    private val cartService: GasCartService,
    private val gasService: GasService,
    private val gasOrderRepository: GasOrderRepository,
    private val calcOrderRepository: CalcOrderRepository,
    @Autowired private val roleUtils: RoleUtils
) {

    /**
     * GET /api/gas-orders/cart-icon - Получить иконку корзины
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/cart-icon")
    fun getCartIcon(
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<ApiResponse<CartIconDto>> {
        try {
            val userId = roleUtils.getUserIdFromToken(token)
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }
            val cartCount = cartService.getCartItemsCount(userId)
            val activeCart = cartService.getActiveCart(userId)

            val cartIconDto = CartIconDto(
                orderId = activeCart.id,
                itemsCount = cartCount
            )

            return ResponseEntity.ok(ApiResponse.success(cartIconDto))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении иконки корзины: ${e.message}"))
        }
    }

    @Operation(summary = "Получить список заявок", description = "Получить список заявок: для обычных пользователей - только свои, для модераторов - все")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Список заявок"),
            SwaggerApiResponse(responseCode = "401", description = "Не авторизован"),
            SwaggerApiResponse(responseCode = "403", description = "Доступ запрещен"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    fun getOrders(
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) formedDateFrom: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) formedDateTo: LocalDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PagedResponse<OrderResponseDto>>> {
        try {
            // Проверяем авторизацию
            val authResult = roleUtils.validateTokenAndGetRole(token)

            // Если нет токена или токен невалиден - возвращаем 401
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val filter = OrderFilterDto(status, formedDateFrom?.toString(), formedDateTo?.toString(), page, size)
            val pageable: Pageable = PageRequest.of(page, size)

            // Если модератор или админ - показываем все заявки, иначе только свои
            val orders = if (authResult.role == "MODERATOR" || authResult.role == "ADMIN") {
                cartService.getOrdersWithFilter(filter, pageable)
            } else {
                // Для обычных пользователей показываем только свои заявки
                cartService.getOrdersByUserId(authResult.userId, filter, pageable)
            }

            val total = if (authResult.role == "MODERATOR" || authResult.role == "ADMIN") {
                cartService.getOrdersCount(filter)
            } else {
                cartService.getOrdersCountByUserId(authResult.userId, filter)
            }

            val orderDtos = orders.map { order ->
                val gasOrders = cartService.getGasOrdersForCart(order.id ?: 0L)
                val gasOrderDtos = gasOrders.mapNotNull { gasOrder ->
                    gasService.getGasEntityById(gasOrder.gasId)?.let { gas ->
                        GasOrderResponseDto(
                            id = gasOrder.id ?: 0L,
                            gasId = gas.id ?: 0L,
                            gasName = gas.name,
                            gasFormula = gas.formula,
                            gasImageUrl = gas.imageUrl,
                            concentration = gasOrder.concentration, // Уже в процентах (0-100)
                            temperature = gasOrder.temperature,
                            result = gasOrder.result
                        )
                    }
                }

                // Подсчитываем количество записей м-м с заполненным результатом
                val completedResultsCount = gasOrders.count { it.result != null }

                OrderResponseDto(
                    id = order.id ?: 0L,
                    userId = order.userId ?: 0L,
                    tempResult = order.tempResult,
                    timestamp = order.timestamp.toString(),
                    status = order.status.name,
                    description = order.description,
                    createdAt = null, // TODO: добавить поля даты
                    formedAt = null,
                    completedAt = null,
                    creatorLogin = "user_${order.userId}", // TODO: получить из GasUserService
                    moderatorLogin = extractModeratorLogin(order.description),
                    gases = gasOrderDtos,
                    completedResultsCount = completedResultsCount
                )
            }

            val pagedResponse = PagedResponse(
                items = orderDtos,
                total = total,
                page = page,
                size = size
            )

            return ResponseEntity.ok(ApiResponse.success(pagedResponse))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении списка заявок: ${e.message}"))
        }
    }

    /**
     * GET /api/gas-orders/{id} - Получить одну заявку (только для автора заявки)
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    fun getOrder(
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<ApiResponse<OrderResponseDto>> {
        try {
            // Проверяем авторизацию
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            // Проверяем, что пользователь является автором заявки или модератором/админом
            val isAuthor = order.userId == authResult.userId
            val isModerator = authResult.role == "MODERATOR" || authResult.role == "ADMIN"

            if (!isAuthor && !isModerator) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Доступ запрещен. Вы можете просматривать только свои заявки"))
            }

            val gasOrders = cartService.getGasOrdersForCart(id)
            val gasOrderDtos = gasOrders.mapNotNull { gasOrder ->
                gasService.getGasEntityById(gasOrder.gasId)?.let { gas ->
                    GasOrderResponseDto(
                        id = gasOrder.id ?: 0L,
                        gasId = gas.id ?: 0L,
                        gasName = gas.name,
                        gasFormula = gas.formula,
                        gasImageUrl = gas.imageUrl,
                        concentration = gasOrder.concentration,
                        temperature = gasOrder.temperature,
                        result = gasOrder.result
                    )
                }
            }

            // Подсчитываем количество записей м-м с заполненным результатом
            val completedResultsCount = gasOrders.count { it.result != null }

            val orderDto = OrderResponseDto(
                id = order.id ?: 0L,
                userId = order.userId ?: 0L,
                tempResult = order.tempResult,
                timestamp = order.timestamp.toString(),
                status = order.status.name,
                description = order.description,
                createdAt = null,
                formedAt = null,
                completedAt = null,
                creatorLogin = "user_${order.userId}",
                moderatorLogin = extractModeratorLogin(order.description),
                gases = gasOrderDtos,
                completedResultsCount = completedResultsCount
            )

            return ResponseEntity.ok(ApiResponse.success(orderDto))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении заявки: ${e.message}"))
        }
    }

    /**
     * PUT /api/gas-orders/{id} - Изменить заявку (только для создателя заявки)
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    fun updateOrder(
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?,
        @Valid @RequestBody updateOrderDto: UpdateOrderDto
    ): ResponseEntity<ApiResponse<OrderResponseDto>> {
        try {
            // Проверяем авторизацию
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            // Проверяем, что пользователь может редактировать только свои заявки (если не модератор)
            if (authResult.role != "MODERATOR" && authResult.role != "ADMIN" && order.userId != authResult.userId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Вы можете редактировать только свои заявки"))
            }

            if (order.status != OrderStatus.DRAFT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Можно изменять только черновики заявок"))
            }

            val updatedOrder = cartService.updateOrder(id, updateOrderDto)
            if (updatedOrder == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при обновлении заявки"))
            }

            // Возвращаем обновленную заявку
            return getOrder(id, token)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при обновлении заявки: ${e.message}"))
        }
    }

    /**
     * PUT /api/gas-orders/{id}/form - Сформировать заявку
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}/form")
    fun formOrder(
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<ApiResponse<OrderResponseDto>> {
        try {
            // Проверяем авторизацию
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            // Проверяем, что пользователь может формировать только свои заявки (если не модератор)
            if (authResult.role != "MODERATOR" && authResult.role != "ADMIN" && order.userId != authResult.userId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Вы можете формировать только свои заявки"))
            }

            if (order.status != OrderStatus.DRAFT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Можно формировать только черновики заявок"))
            }

            val gasOrders = cartService.getGasOrdersForCart(id)
            if (gasOrders.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Нельзя сформировать пустую заявку"))
            }

            val formedOrder = cartService.formOrder(id)
            if (formedOrder == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при формировании заявки"))
            }

            return getOrder(id, token)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при формировании заявки: ${e.message}"))
        }
    }

    @Operation(summary = "Завершить заявку", description = "Подтвердить или отклонить заявку (только для администраторов). Администратор может завершать любые заявки, включая свои.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Заявка успешно обработана"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные или статус заявки"),
            SwaggerApiResponse(responseCode = "401", description = "Не авторизован"),
            SwaggerApiResponse(responseCode = "403", description = "Доступ запрещен. Требуется роль администратора"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка не найдена"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}/complete")
    fun completeOrder(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = true)
        @RequestHeader("Authorization") token: String?,
        @Valid @RequestBody completeOrderDto: CompleteOrderDto
    ): ResponseEntity<ApiResponse<OrderResponseDto>> {
        try {
            // Проверяем авторизацию
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            // Проверяем роль администратора (только ADMIN может завершать заявки)
            if (authResult.role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Доступ запрещен. Требуется роль администратора"))
            }

            if (order.status != OrderStatus.FORMED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Можно завершать только сформированные заявки"))
            }

            // Завершаем заявку с логином модератора
            val completedOrder = when (completeOrderDto.action) {
                "APPROVE" -> cartService.approveOrder(id, completeOrderDto.comment, authResult.login)
                "REJECT" -> cartService.rejectOrder(id, completeOrderDto.comment, authResult.login)
                else -> {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Неверное действие. Используйте APPROVE или REJECT"))
                }
            }

            if (completedOrder == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при завершении заявки"))
            }

            return getOrder(id, token)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при завершении заявки: ${e.message}"))
        }
    }

    @Operation(summary = "Удалить заявку", description = "Удалить заявку (только для администраторов)")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Заявка успешно удалена"),
            SwaggerApiResponse(responseCode = "401", description = "Не авторизован"),
            SwaggerApiResponse(responseCode = "403", description = "Доступ запрещен. Требуется роль администратора"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка не найдена"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    fun deleteOrder(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = true)
        @RequestHeader("Authorization") token: String?
    ): ResponseEntity<ApiResponse<String>> {
            try {
            // Проверяем роль модератора
            val moderatorCheck = roleUtils.checkModeratorRole(token)
            if (moderatorCheck != null) {
                @Suppress("UNCHECKED_CAST")
                return moderatorCheck as ResponseEntity<ApiResponse<String>>
            }

            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            if (order.status == OrderStatus.COMPLETED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Нельзя удалять сформированные заявки"))
            }

            // Получаем userId из токена для удаления заявки
            val userId = roleUtils.getUserIdFromToken(token)
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val success = cartService.deleteOrder(id, userId)
            if (!success) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при удалении заявки"))
            }

            return ResponseEntity.ok(ApiResponse.success("Заявка успешно удалена"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при удалении заявки: ${e.message}"))
        }
    }

    @Operation(summary = "Добавить газ в заявку", description = "Добавить газ с указанной концентрацией и температурой в заявку")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Газ успешно добавлен в заявку"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные или статус заявки"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка или газ не найдены"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/gases")
    fun addGasToOrder(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?,
        @Valid @RequestBody addGasDto: AddGasToOrderDto
    ): ResponseEntity<ApiResponse<String>> {
        try {
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }

            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            if (order.status != OrderStatus.DRAFT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Можно добавлять услуги только в черновики заявок"))
            }

            if (order.userId != authResult.userId && authResult.role != "MODERATOR" && authResult.role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Вы можете изменять только свои заявки"))
            }

            val gas = gasService.getGasById(addGasDto.gasId)
            if (gas == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Услуга с ID ${addGasDto.gasId} не найдена"))
            }

            val success = cartService.addGasToOrder(id, addGasDto.gasId, addGasDto.concentration, addGasDto.temperature)
            if (!success) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при добавлении услуги в заявку"))
            }

            return ResponseEntity.ok(ApiResponse.success("Услуга успешно добавлена в заявку"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при добавлении услуги в заявку: ${e.message}"))
        }
    }

    @Operation(summary = "Обновить газ в заявке", description = "Изменить концентрацию и/или температуру газа в заявке")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Газ в заявке успешно обновлен"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные или статус заявки"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка или газ не найдены"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{orderId}/gases/{gasId}")
    fun updateGasInOrder(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable orderId: Long,
        @Parameter(description = "ID газа", required = true)
        @PathVariable gasId: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?,
        @Valid @RequestBody updateGasOrderDto: UpdateGasOrderDto
    ): ResponseEntity<ApiResponse<String>> {
        try {
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }
            val order = cartService.getOrderById(orderId)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $orderId не найдена"))
            }

            if (order.status != OrderStatus.DRAFT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Можно изменять услуги только в черновиках заявок"))
            }

            if (order.userId != authResult.userId && authResult.role != "MODERATOR" && authResult.role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Вы можете изменять только свои заявки"))
            }

            val success = cartService.updateGasInOrder(orderId, gasId, updateGasOrderDto.concentration, updateGasOrderDto.temperature)
            if (!success) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при обновлении услуги в заявке"))
            }

            return ResponseEntity.ok(ApiResponse.success("Услуга в заявке успешно обновлена"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при обновлении услуги в заявке: ${e.message}"))
        }
    }

    @Operation(summary = "Рассчитать температуру заявки", description = "Рассчитать итоговую температуру заявки на основе параметров газов")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Температура рассчитана"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка не найдена"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/calculate-temperature")
    fun calculateOrderTemperature(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable id: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        try {
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }
            val order = cartService.getOrderById(id)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $id не найдена"))
            }

            if (order.userId != authResult.userId && authResult.role != "MODERATOR" && authResult.role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Доступ запрещен. Вы можете просматривать только свои заявки"))
            }

            val calculatedTemperature = cartService.calculateTemperatureForOrderPublic(id)
            val result = mapOf<String, Any>(
                "calculatedTemperature" to calculatedTemperature,
                "unit" to "°C"
            )

            return ResponseEntity.ok(ApiResponse.success(result, "Температура успешно рассчитана"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при расчете температуры: ${e.message}"))
        }
    }

    @Operation(summary = "Получить газ в заявке", description = "Получить информацию о газе в заявке с его параметрами")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Информация о газе в заявке"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка или газ не найдены"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{orderId}/gases/{gasId}")
    fun getGasInOrder(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable orderId: Long,
        @Parameter(description = "ID газа", required = true)
        @PathVariable gasId: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<ApiResponse<GasOrderResponseDto>> {
        try {
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }
            val order = cartService.getOrderById(orderId)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $orderId не найдена"))
            }

            if (order.userId != authResult.userId && authResult.role != "MODERATOR" && authResult.role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Доступ запрещен. Вы можете просматривать только свои заявки"))
            }

            val gasOrder = cartService.getGasOrderInOrder(orderId, gasId)
            if (gasOrder == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Газ с ID $gasId не найден в заявке $orderId"))
            }

            val gas = gasService.getGasEntityById(gasId)
            if (gas == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Газ с ID $gasId не найден"))
            }

            val gasOrderDto = GasOrderResponseDto(
                id = gasOrder.id ?: 0L,
                gasId = gas.id ?: 0L,
                gasName = gas.name,
                gasFormula = gas.formula,
                gasImageUrl = gas.imageUrl,
                concentration = gasOrder.concentration / 100.0, // Конвертируем в проценты (0-100)
                temperature = gasOrder.temperature,
                result = gasOrder.result
            )

            return ResponseEntity.ok(ApiResponse.success(gasOrderDto))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении газа в заявке: ${e.message}"))
        }
    }

    @Operation(summary = "Удалить газ из заявки", description = "Удалить газ из заявки")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Газ успешно удален из заявки"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные или статус заявки"),
            SwaggerApiResponse(responseCode = "404", description = "Заявка или газ не найдены"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{orderId}/gases/{gasId}")
    fun removeGasFromOrder(
        @Parameter(description = "ID заявки", required = true)
        @PathVariable orderId: Long,
        @Parameter(description = "ID газа", required = true)
        @PathVariable gasId: Long,
        @Parameter(description = "JWT токен авторизации", required = false)
        @RequestHeader("Authorization", required = false) token: String?
    ): ResponseEntity<ApiResponse<String>> {
        try {
            val authResult = roleUtils.validateTokenAndGetRole(token)
            if (authResult == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Требуется авторизация"))
            }
            val order = cartService.getOrderById(orderId)
            if (order == null || order.status == OrderStatus.DELETED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Заявка с ID $orderId не найдена"))
            }

            if (order.status != OrderStatus.DRAFT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Можно удалять услуги только из черновиков заявок"))
            }

            if (order.userId != authResult.userId && authResult.role != "MODERATOR" && authResult.role != "ADMIN") {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Вы можете изменять только свои заявки"))
            }

            val success = cartService.removeGasFromOrder(orderId, gasId)
            if (!success) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка при удалении услуги из заявки"))
            }

            return ResponseEntity.ok(ApiResponse.success("Услуга успешно удалена из заявки"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при удалении услуги из заявки: ${e.message}"))
        }
    }

    /**
     * POST /api/gas-orders/async-results - Принять результаты от асинхронного сервиса
     * Требует токен авторизации (8 байт) в заголовке X-Async-Token
     */
    @Operation(summary = "Принять результаты от асинхронного сервиса", description = "Endpoint для получения результатов расчетов от асинхронного сервиса. Требует токен авторизации.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Результаты успешно приняты"),
            SwaggerApiResponse(responseCode = "401", description = "Неверный токен авторизации"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @PostMapping("/async-results")
    @org.springframework.transaction.annotation.Transactional
    fun receiveAsyncResults(
        @Parameter(description = "Токен авторизации асинхронного сервиса (8 байт)", required = true)
        @RequestHeader("X-Async-Token") asyncToken: String?,
        @RequestBody resultsDto: AsyncResultsDto
    ): ResponseEntity<ApiResponse<String>> {
        println("=== GasOrderApiController: receiveAsyncResults START ===")
        println("GasOrderApiController: receiveAsyncResults - received token: $asyncToken")
        println("GasOrderApiController: receiveAsyncResults - results count: ${resultsDto.results.size}")

        try {
            // Проверка токена (8 байт = 16 символов в hex или 8 символов в ASCII)
            val validToken = "async123" // 8 байт токен
            if (asyncToken != validToken) {
                println("GasOrderApiController: receiveAsyncResults - ERROR: Invalid token")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Неверный токен авторизации"))
            }
            println("GasOrderApiController: receiveAsyncResults - token validated")

            // Обновляем результаты в GasOrder
            var updatedCount = 0
            val calcOrderIds = mutableSetOf<Long>()

            for (result in resultsDto.results) {
                println("GasOrderApiController: receiveAsyncResults - processing result: calcOrderId=${result.calcOrderId}, gasId=${result.gasId}, result=${result.result}")
                val gasOrder = gasOrderRepository.findByCalcOrderIdAndGasId(result.calcOrderId, result.gasId)
                if (gasOrder != null) {
                    println("GasOrderApiController: receiveAsyncResults - found gasOrder: id=${gasOrder.id}, updating result from ${gasOrder.result} to ${result.result}")
                    val updatedGasOrder = gasOrder.copy(result = result.result)
                    gasOrderRepository.save(updatedGasOrder)
                    updatedCount++
                    calcOrderIds.add(result.calcOrderId)
                    println("GasOrderApiController: receiveAsyncResults - gasOrder updated successfully")
                } else {
                    println("GasOrderApiController: receiveAsyncResults - WARNING: gasOrder not found for calcOrderId=${result.calcOrderId}, gasId=${result.gasId}")
                }
            }
            println("GasOrderApiController: receiveAsyncResults - updated $updatedCount gasOrders, calcOrderIds: $calcOrderIds")

            // Пересчитываем tempResult для каждого CalcOrder на основе результатов в GasOrder
            // Это происходит только после получения результатов от асинхронного сервиса
            for (calcOrderId in calcOrderIds) {
                println("GasOrderApiController: receiveAsyncResults - recalculating tempResult for calcOrderId=$calcOrderId")
                val calcOrder = cartService.getOrderById(calcOrderId)
                if (calcOrder != null) {
                    println("GasOrderApiController: receiveAsyncResults - found calcOrder: id=${calcOrder.id}, current tempResult=${calcOrder.tempResult}")
                    val gasOrders = cartService.getGasOrdersForCart(calcOrderId)
                    println("GasOrderApiController: receiveAsyncResults - gasOrders count: ${gasOrders.size}")

                    // Проверяем, все ли результаты получены
                    val totalGasOrders = gasOrders.size
                    val resultsReceived = gasOrders.count { it.result != null }
                    println("GasOrderApiController: receiveAsyncResults - results received: $resultsReceived/$totalGasOrders")

                    // Рассчитываем tempResult только если есть хотя бы один результат
                    val tempResult = if (resultsReceived > 0) {
                        println("GasOrderApiController: receiveAsyncResults - calculating tempResult from async service results")
                        // Используем результаты от асинхронного сервиса для расчета
                        calculateTemperatureFromResults(gasOrders)
                    } else {
                        println("GasOrderApiController: receiveAsyncResults - WARNING: no results received, using standard formula")
                        // Если результатов нет (не должно происходить), используем стандартную формулу
                        cartService.calculateTemperatureForOrderPublic(calcOrderId)
                    }
                    println("GasOrderApiController: receiveAsyncResults - calculated tempResult: $tempResult")

                    val updatedCalcOrder = calcOrder.copy(tempResult = tempResult)
                    val savedCalcOrder = calcOrderRepository.save(updatedCalcOrder)
                    println("GasOrderApiController: receiveAsyncResults - SUCCESS: saved calcOrder with tempResult: ${savedCalcOrder.tempResult}")
                } else {
                    println("GasOrderApiController: receiveAsyncResults - WARNING: calcOrder not found for id=$calcOrderId")
                }
            }

            println("=== GasOrderApiController: receiveAsyncResults END ===")
            return ResponseEntity.ok(ApiResponse.success("Принято результатов: $updatedCount, обновлено заявок: ${calcOrderIds.size}"))
        } catch (e: Exception) {
            println("GasOrderApiController: receiveAsyncResults - ERROR: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при приеме результатов: ${e.message}"))
        }
    }

    /**
     * Рассчитать температуру на основе результатов от асинхронного сервиса
     * Go сервис возвращает температуру для каждого газа с учетом концентрации
     * Итоговая температура = базовая (15°C) + сумма парниковых эффектов от всех газов
     *
     * Поскольку Go сервис уже рассчитал температуру с учетом концентрации для каждого газа,
     * мы используем средневзвешенное значение результатов по концентрациям
     */
    private fun calculateTemperatureFromResults(gasOrders: List<GasOrder>): Double {
        println("GasOrderApiController: calculateTemperatureFromResults - gasOrders count: ${gasOrders.size}")
        val gasOrdersWithResults = gasOrders.filter { it.result != null }
        println("GasOrderApiController: calculateTemperatureFromResults - gasOrders with results: ${gasOrdersWithResults.size}")

        return if (gasOrdersWithResults.isNotEmpty()) {
            // Go сервис возвращает температуру для каждого газа (baseTemp + greenhouseEffect для этого газа)
            // Для итоговой температуры используем средневзвешенное по концентрациям
            val weightedSum = gasOrdersWithResults.sumOf { gasOrder ->
                val result = gasOrder.result ?: 0.0
                val concentration = gasOrder.concentration / 100.0 // Конвертируем в доли
                val weighted = result * concentration
                println("GasOrderApiController: calculateTemperatureFromResults - gasId=${gasOrder.gasId}, result=$result, concentration=$concentration, weighted=$weighted")
                weighted
            }
            val totalConcentration = gasOrdersWithResults.sumOf { it.concentration / 100.0 }
            println("GasOrderApiController: calculateTemperatureFromResults - weightedSum=$weightedSum, totalConcentration=$totalConcentration")

            val finalTemp = if (totalConcentration > 0) {
                weightedSum / totalConcentration
            } else {
                // Если концентрация равна нулю, используем простое среднее результатов
                gasOrdersWithResults.mapNotNull { it.result }.average()
            }
            println("GasOrderApiController: calculateTemperatureFromResults - finalTemp: $finalTemp")
            finalTemp
        } else {
            // Если нет результатов, используем стандартную формулу
            println("GasOrderApiController: calculateTemperatureFromResults - WARNING: no results, using standard formula")
            gasOrders.map { it.temperature }.average()
        }
    }

    /**
     * Извлечь логин модератора из описания заявки
     */
    private fun extractModeratorLogin(description: String?): String? {
        if (description == null) return null
        val moderatorMatch = Regex("Модератор:\\s*(\\S+)").find(description)
        return moderatorMatch?.groupValues?.get(1)
    }
}
