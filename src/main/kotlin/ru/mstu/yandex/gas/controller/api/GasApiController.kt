package ru.mstu.yandex.gas.controller.api

import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.mstu.yandex.gas.dto.*
import ru.mstu.yandex.gas.entity.Gas
import ru.mstu.yandex.gas.service.GasService
import ru.mstu.yandex.gas.service.GasMinioService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import ru.mstu.yandex.gas.util.RoleUtils

@RestController
@RequestMapping("/api/gases")
class GasApiController(
    private val gasService: GasService,
    private val minioService: GasMinioService,
    private val roleUtils: RoleUtils
) {

    /**
     * GET /api/gases - Получить список услуг с фильтрацией
     */
    @GetMapping
    fun getGases(@RequestParam(required = false) name: String?,
                 @RequestParam(required = false) formula: String?,
                 @RequestParam(defaultValue = "0") page: Int,
                 @RequestParam(defaultValue = "20") size: Int): ResponseEntity<ApiResponse<PagedResponse<GasResponseDto>>> {

        println("[GasApiController] getGases called with: name=$name, formula=$formula, page=$page, size=$size")
        try {
            val filter = GasFilterDto(name, formula, page, size)
            val pageable: Pageable = PageRequest.of(page, size)

            val gases = gasService.getAllGasesWithFilter(filter, pageable)
            val total = gasService.getGasesCount(filter)

            val gasDtos = gases.map { gas ->
                GasResponseDto(
                    id = gas.id ?: 0L,
                    name = gas.name,
                    formula = gas.formula,
                    detailedDescription = gas.detailedDescription,
                    imageUrl = gas.imageUrl,
                    createdAt = null, // TODO: добавить поля даты в entity
                    updatedAt = null
                )
            }

            val pagedResponse = PagedResponse(
                items = gasDtos,
                total = total,
                page = page,
                size = size
            )

            return ResponseEntity.ok(ApiResponse.success(pagedResponse))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении списка услуг: ${e.message}"))
        }
    }

    /**
     * GET /api/gases/{id} - Получить одну услугу
     */
    @GetMapping("/{id}")
    fun getGas(@PathVariable id: Long): ResponseEntity<ApiResponse<GasResponseDto>> {
        try {
            val gas = gasService.getGasEntityById(id)
            if (gas == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Услуга с ID $id не найдена"))
            }

            val gasDto = GasResponseDto(
                id = gas.id ?: 0L,
                name = gas.name,
                formula = gas.formula,
                detailedDescription = gas.detailedDescription,
                imageUrl = gas.imageUrl,
                createdAt = null,
                updatedAt = null
            )

            return ResponseEntity.ok(ApiResponse.success(gasDto))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при получении услуги: ${e.message}"))
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    fun createGas(@RequestHeader("Authorization", required = false) token: String?,
                  @Valid @RequestBody createGasDto: CreateGasDto): ResponseEntity<ApiResponse<GasResponseDto>> {
        try {
            val moderatorCheck = roleUtils.checkModeratorRole(token)
            if (moderatorCheck != null) {
                @Suppress("UNCHECKED_CAST")
                return moderatorCheck as ResponseEntity<ApiResponse<GasResponseDto>>
            }
            val gas = Gas(
                name = createGasDto.name,
                formula = createGasDto.formula,
                detailedDescription = createGasDto.detailedDescription,
                imageUrl = createGasDto.imageUrl
            )

            val savedGas = gasService.saveGas(gas)

            val gasDto = GasResponseDto(
                id = savedGas.id ?: 0L,
                name = savedGas.name,
                formula = savedGas.formula,
                detailedDescription = savedGas.detailedDescription,
                imageUrl = savedGas.imageUrl,
                createdAt = null,
                updatedAt = null
            )

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(gasDto, "Услуга успешно создана"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при создании услуги: ${e.message}"))
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    fun updateGas(@PathVariable id: Long,
                  @RequestHeader("Authorization", required = false) token: String?,
                  @Valid @RequestBody updateGasDto: UpdateGasDto): ResponseEntity<ApiResponse<GasResponseDto>> {
        try {
            val moderatorCheck = roleUtils.checkModeratorRole(token)
            if (moderatorCheck != null) {
                @Suppress("UNCHECKED_CAST")
                return moderatorCheck as ResponseEntity<ApiResponse<GasResponseDto>>
            }
            val existingGas = gasService.getGasEntityById(id)
            if (existingGas == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Услуга с ID $id не найдена"))
            }

            val updatedGas = existingGas.copy(
                name = updateGasDto.name ?: existingGas.name,
                formula = updateGasDto.formula ?: existingGas.formula,
                detailedDescription = updateGasDto.detailedDescription ?: existingGas.detailedDescription,
                imageUrl = updateGasDto.imageUrl ?: existingGas.imageUrl
            )

            val savedGas = gasService.saveGas(updatedGas)

            val gasDto = GasResponseDto(
                id = savedGas.id ?: 0L,
                name = savedGas.name,
                formula = savedGas.formula,
                detailedDescription = savedGas.detailedDescription,
                imageUrl = savedGas.imageUrl,
                createdAt = null,
                updatedAt = null
            )

            return ResponseEntity.ok(ApiResponse.success(gasDto, "Услуга успешно обновлена"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при обновлении услуги: ${e.message}"))
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    fun deleteGas(@PathVariable id: Long,
                  @RequestHeader("Authorization", required = false) token: String?): ResponseEntity<ApiResponse<String>> {
        try {
            val moderatorCheck = roleUtils.checkModeratorRole(token)
            if (moderatorCheck != null) {
                @Suppress("UNCHECKED_CAST")
                return moderatorCheck as ResponseEntity<ApiResponse<String>>
            }
            val gas = gasService.getGasEntityById(id)
            if (gas == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Услуга с ID $id не найдена"))
            }

            // Удаляем изображение из Minio, если оно есть
            gas.imageUrl?.let { imageUrl ->
                val fileName = minioService.extractFileNameFromUrl(imageUrl)
                fileName?.let { minioService.deleteImage(it) }
            }

            gasService.deleteGas(id)

            return ResponseEntity.ok(ApiResponse.success("Услуга успешно удалена"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при удалении услуги: ${e.message}"))
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/image")
    fun addGasImageUrl(@PathVariable id: Long,
                       @RequestHeader("Authorization", required = false) token: String?,
                       @Valid @RequestBody addImageUrlDto: AddImageUrlDto): ResponseEntity<ApiResponse<String>> {
        try {
            val moderatorCheck = roleUtils.checkModeratorRole(token)
            if (moderatorCheck != null) {
                @Suppress("UNCHECKED_CAST")
                return moderatorCheck as ResponseEntity<ApiResponse<String>>
            }
            val gas = gasService.getGasEntityById(id)
            if (gas == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Услуга с ID $id не найдена"))
            }

            // Обновляем услугу с новым URL изображения
            val updatedGas = gas.copy(imageUrl = addImageUrlDto.imageUrl)
            gasService.saveGas(updatedGas)

            return ResponseEntity.ok(ApiResponse.success(addImageUrlDto.imageUrl, "URL изображения успешно добавлен"))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ошибка при добавлении URL изображения: ${e.message}"))
        }
    }
}
