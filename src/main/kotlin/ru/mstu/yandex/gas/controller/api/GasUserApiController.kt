package ru.mstu.yandex.gas.controller.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.HttpClientErrorException
import org.slf4j.LoggerFactory
import ru.mstu.yandex.gas.dto.*
import ru.mstu.yandex.gas.entity.User
import ru.mstu.yandex.gas.service.GasUserService
import ru.mstu.yandex.gas.util.JwtUtils

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "API для управления пользователями")
class GasUserApiController(
    private val userService: GasUserService,
    private val jwtUtils: JwtUtils,
    private val tokenBlacklistService: ru.mstu.yandex.gas.service.TokenBlacklistService
) {
    private val logger = LoggerFactory.getLogger(GasUserApiController::class.java)

    @Operation(summary = "Регистрация пользователя", description = "Создание нового пользователя в системе")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные"),
            SwaggerApiResponse(responseCode = "409", description = "Пользователь уже существует"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @CrossOrigin(origins = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody registerUserDto: RegisterUserDto): ResponseEntity<ApiResponse<UserResponseDto>> {
        println("[GasUserApiController] registerUser called with: $registerUserDto")
        try {
            // Проверяем, не существует ли уже пользователь с таким логином
            val existingUser = userService.findByLogin(registerUserDto.login)
            if (existingUser != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Пользователь с логином '${registerUserDto.login}' уже существует"))
            }

            val user = User(
                login = registerUserDto.login,
                password = registerUserDto.password, // В реальном приложении нужно хешировать пароль
                email = registerUserDto.email,
                firstName = registerUserDto.firstName,
                lastName = registerUserDto.lastName,
                role = registerUserDto.role ?: "USER" // Устанавливаем значение по умолчанию "USER" если роль не указана
            )

            val savedUser = userService.saveUser(user)

            val userDto = UserResponseDto(
                id = savedUser.id ?: 0L,
                login = savedUser.login,
                email = savedUser.email,
                firstName = savedUser.firstName,
                lastName = savedUser.lastName,
                role = savedUser.role ?: "USER",
                createdAt = null // TODO: добавить поле даты создания
            )

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userDto, "Пользователь успешно зарегистрирован"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при регистрации пользователя: ${e.message}"))
        }
    }

    @Operation(summary = "Аутентификация пользователя", description = "Вход пользователя в систему")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Успешная аутентификация"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные"),
            SwaggerApiResponse(responseCode = "401", description = "Неверные учетные данные"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @CrossOrigin(origins = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
    @PostMapping("/login")
    fun loginUser(@Valid @RequestBody loginDto: LoginDto): ResponseEntity<ApiResponse<AuthResponseDto>> {
        try {
            val user = userService.findByLogin(loginDto.login)
            if (user == null || user.password != loginDto.password) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Неверный логин или пароль"))
            }

            val userDto = UserResponseDto(
                id = user.id ?: 0L,
                login = user.login,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                role = user.role ?: "USER",
                createdAt = null
            )

            // Генерируем JWT токен
            val token = jwtUtils.generateToken(
                userId = user.id ?: 0L,
                login = user.login,
                role = user.role
            )

            val authResponse = AuthResponseDto(
                token = token,
                user = userDto
            )

            return ResponseEntity.ok(ApiResponse.success(authResponse, "Успешная аутентификация"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при аутентификации: ${e.message}"))
        }
    }

    @Operation(summary = "Выход из системы", description = "Деавторизация пользователя с добавлением токена в blacklist")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Успешная деавторизация"),
            SwaggerApiResponse(responseCode = "401", description = "Токен не предоставлен или неверный"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    fun logoutUser(
        @Parameter(description = "JWT токен авторизации", required = true)
        @RequestHeader("Authorization") authHeader: String?
    ): ResponseEntity<ApiResponse<String>> {
        try {
            // Проверяем наличие токена
            if (authHeader.isNullOrBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Токен авторизации не предоставлен"))
            }

            // Извлекаем токен из заголовка
            val token = jwtUtils.extractTokenFromHeader(authHeader)
            if (token.isNullOrBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Неверный формат токена"))
            }

            // Проверяем валидность токена перед добавлением в blacklist
            // Используем validateTokenWithoutBlacklist, так как токен еще не в blacklist
            if (!jwtUtils.validateTokenWithoutBlacklist(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Токен недействителен или истек"))
            }

            // Добавляем токен в blacklist
            try {
                tokenBlacklistService.addTokenToBlacklist(token)
                logger.info("Токен успешно добавлен в blacklist через logout endpoint")
            } catch (e: Exception) {
                logger.error("Ошибка при добавлении токена в blacklist: ${e.message}", e)
                logger.error("Стек ошибки: ${e.stackTraceToString()}")
                // Пробрасываем ошибку дальше, чтобы вернуть правильный статус
                throw e
            }

            return ResponseEntity.ok(ApiResponse.success("Успешная деавторизация. Токен добавлен в blacklist."))
        } catch (e: RuntimeException) {
            logger.error("RuntimeException при деавторизации: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при деавторизации: ${e.message}"))
        } catch (e: Exception) {
            logger.error("Exception при деавторизации: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при деавторизации: ${e.message}"))
        }
    }

    @Operation(summary = "Получить профиль пользователя", description = "Получить данные текущего пользователя")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Профиль пользователя"),
            SwaggerApiResponse(responseCode = "401", description = "Не авторизован"),
            SwaggerApiResponse(responseCode = "403", description = "Неверный токен"),
            SwaggerApiResponse(responseCode = "404", description = "Пользователь не найден"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile")
    fun getUserProfile(
        @Parameter(description = "JWT токен авторизации", required = true)
        @RequestHeader("Authorization") token: String?
    ): ResponseEntity<ApiResponse<UserResponseDto>> {
        try {
            // Проверяем наличие токена
            if (token.isNullOrBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Токен авторизации не предоставлен"))
            }

            // Валидация JWT токена и извлечение ID пользователя
            val cleanToken = jwtUtils.extractTokenFromHeader(token)
            if (cleanToken == null || !jwtUtils.validateToken(cleanToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Неверный или истекший токен авторизации"))
            }

            val userId = jwtUtils.getUserIdFromToken(cleanToken)
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Не удалось извлечь ID пользователя из токена"))
            }

            val user = userService.findById(userId)
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Пользователь не найден"))
            }

            val userDto = UserResponseDto(
                id = user.id ?: 0L,
                login = user.login,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                role = user.role,
                createdAt = null
            )

            return ResponseEntity.ok(ApiResponse.success(userDto))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении профиля пользователя: ${e.message}"))
        }
    }

    @Operation(summary = "Обновить профиль пользователя", description = "Обновить данные текущего пользователя")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Профиль обновлен"),
            SwaggerApiResponse(responseCode = "400", description = "Неверные данные"),
            SwaggerApiResponse(responseCode = "401", description = "Не авторизован"),
            SwaggerApiResponse(responseCode = "403", description = "Неверный токен"),
            SwaggerApiResponse(responseCode = "404", description = "Пользователь не найден"),
            SwaggerApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/profile")
    fun updateUserProfile(
        @Parameter(description = "JWT токен авторизации", required = true)
        @RequestHeader("Authorization") token: String?,
        @Valid @RequestBody updateUserDto: UpdateUserDto
    ): ResponseEntity<ApiResponse<UserResponseDto>> {
        try {
            // Проверяем наличие токена
            if (token.isNullOrBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Токен авторизации не предоставлен"))
            }

            // Валидация JWT токена и извлечение ID пользователя
            val cleanToken = jwtUtils.extractTokenFromHeader(token)
            if (cleanToken == null || !jwtUtils.validateToken(cleanToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Неверный или истекший токен авторизации"))
            }

            val userId = jwtUtils.getUserIdFromToken(cleanToken)
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Не удалось извлечь ID пользователя из токена"))
            }

            val user = userService.findById(userId)
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Пользователь не найден"))
            }

            val updatedUser = user.copy(
                email = updateUserDto.email ?: user.email,
                firstName = updateUserDto.firstName ?: user.firstName,
                lastName = updateUserDto.lastName ?: user.lastName,
                role = updateUserDto.role ?: user.role
            )

            val savedUser = userService.saveUser(updatedUser)

            val userDto = UserResponseDto(
                id = savedUser.id ?: 0L,
                login = savedUser.login,
                email = savedUser.email,
                firstName = savedUser.firstName,
                lastName = savedUser.lastName,
                role = savedUser.role ?: "USER",
                createdAt = null
            )

            return ResponseEntity.ok(ApiResponse.success(userDto, "Профиль пользователя успешно обновлен"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при обновлении профиля пользователя: ${e.message}"))
        }
    }

}
