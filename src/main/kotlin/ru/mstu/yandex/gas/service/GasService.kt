package ru.mstu.yandex.gas.service

import org.springframework.stereotype.Service
import ru.mstu.yandex.gas.entity.Gas
import ru.mstu.yandex.gas.repository.GasRepository
import ru.mstu.yandex.gas.model.SimpleGasModel
import ru.mstu.yandex.gas.model.GasModel
import ru.mstu.yandex.gas.model.NumericalValue

@Service
class GasService(
    private val gasRepository: GasRepository
) {

    fun getAllGases(): List<SimpleGasModel> {
        return gasRepository.findAll().map { gas ->
            SimpleGasModel(
                id = gas.id ?: 0L,
                name = gas.name,
                concentration = getConcentrationForGas(gas.id ?: 0L),
                temperature = "15°C",
                image = gas.imageUrl ?: ""
            )
        }
    }

    fun getGasById(id: Long): GasModel? {
        val gas = gasRepository.findById(id).orElse(null) ?: return null

        return GasModel(
            id = gas.id ?: 0L,
            name = gas.name,
            fullName = getFullNameForGas(gas.id ?: 0L),
            concentration = getConcentrationForGas(gas.id ?: 0L),
            temperature = "15°C",
            image = gas.imageUrl ?: "",
            description = gas.detailedDescription,
            numericalValues = getNumericalValuesForGas(gas.id ?: 0L),
            properties = getPropertiesForGas(gas.id ?: 0L),
            applications = getApplicationsForGas(gas.id ?: 0L)
        )
    }

    fun getGasEntityById(id: Long): ru.mstu.yandex.gas.entity.Gas? {
        return gasRepository.findById(id).orElse(null)
    }

    fun searchGases(query: String): List<SimpleGasModel> {
        if (query.isBlank()) return getAllGases()

        return gasRepository.findByNameContainingIgnoreCase(query).map { gas ->
            SimpleGasModel(
                id = gas.id ?: 0L,
                name = gas.name,
                concentration = getConcentrationForGas(gas.id ?: 0L),
                temperature = "15°C",
                image = gas.imageUrl ?: ""
            )
        }
    }

    private fun getConcentrationForGas(gasId: Long): String {
        return when (gasId) {
            1L -> "0.04%" // CO₂
            2L -> "20.95%" // O₂
            3L -> "0.93%" // Ar
            4L -> "78.08%" // N₂
            5L -> "0-4%" // H₂O
            else -> "0%"
        }
    }


    private fun getFullNameForGas(gasId: Long): String {
        return when (gasId) {
            1L -> "Диоксид углерода"
            2L -> "Молекулярный кислород"
            3L -> "Аргон"
            4L -> "Молекулярный азот"
            5L -> "Водяной пар"
            else -> "Неизвестный газ"
        }
    }

    private fun getNumericalValuesForGas(gasId: Long): Map<String, NumericalValue> {
        return when (gasId) {
            1L -> mapOf(
                "molarMass" to NumericalValue("44.01", "г/моль", "Молярная масса"),
                "density" to NumericalValue("1.977", "г/л", "Плотность", "при 0°C"),
                "boilingPoint" to NumericalValue("-78.5", "°C", "Температура кипения")
            )
            2L -> mapOf(
                "molarMass" to NumericalValue("32.00", "г/моль", "Молярная масса"),
                "density" to NumericalValue("1.429", "г/л", "Плотность", "при 0°C"),
                "boilingPoint" to NumericalValue("-183", "°C", "Температура кипения")
            )
            3L -> mapOf(
                "atomicMass" to NumericalValue("39.95", "г/моль", "Атомная масса"),
                "density" to NumericalValue("1.784", "г/л", "Плотность", "при 0°C"),
                "boilingPoint" to NumericalValue("-185.8", "°C", "Температура кипения")
            )
            4L -> mapOf(
                "molarMass" to NumericalValue("28.01", "г/моль", "Молярная масса"),
                "density" to NumericalValue("1.251", "г/л", "Плотность", "при 0°C"),
                "boilingPoint" to NumericalValue("-195.8", "°C", "Температура кипения")
            )
            5L -> mapOf(
                "molarMass" to NumericalValue("18.02", "г/моль", "Молярная масса"),
                "density" to NumericalValue("0.804", "г/л", "Плотность", "при 100°C"),
                "boilingPoint" to NumericalValue("100", "°C", "Температура кипения")
            )
            else -> emptyMap()
        }
    }

    private fun getPropertiesForGas(gasId: Long): List<String> {
        return when (gasId) {
            1L -> listOf("Молекулярная формула: CO₂", "Растворимость в воде: высокая")
            2L -> listOf("Молекулярная формула: O₂", "Цвет: бесцветный")
            3L -> listOf("Атомная формула: Ar", "Инертный газ")
            4L -> listOf("Молекулярная формула: N₂", "Инертный при обычных условиях")
            5L -> listOf("Молекулярная формула: H₂O", "Парниковый газ")
            else -> emptyList()
        }
    }

    private fun getApplicationsForGas(gasId: Long): List<String> {
        return when (gasId) {
            1L -> listOf(
                "Пищевая промышленность (газированные напитки)",
                "Огнетушители",
                "Сварочные работы",
                "Холодильная техника",
                "Сельское хозяйство (парниковый эффект)"
            )
            2L -> listOf(
                "Медицина (дыхательная терапия)",
                "Металлургия (сталеплавильное производство)",
                "Акваланги и космические корабли",
                "Химическая промышленность",
                "Очистка сточных вод"
            )
            3L -> listOf(
                "Сварочные работы (защитная атмосфера)",
                "Лампы накаливания",
                "Криогенные применения",
                "Научные исследования",
                "Пищевая промышленность (упаковка)"
            )
            4L -> listOf(
                "Производство аммиака и удобрений",
                "Пищевая промышленность (упаковка)",
                "Электроника (защитная атмосфера)",
                "Медицина (криотерапия)",
                "Автомобильная промышленность"
            )
            5L -> listOf(
                "Климатические системы",
                "Пищевая промышленность",
                "Фармацевтика",
                "Энергетика (паровые турбины)",
                "Сельское хозяйство"
            )
            else -> emptyList()
        }
    }

    // Метод для обновления URL изображения газа
    fun updateGasImageUrl(gasId: Long, imageUrl: String): Boolean {
        return try {
            val gas = gasRepository.findById(gasId).orElse(null)
            if (gas != null) {
                val updatedGas = gas.copy(imageUrl = imageUrl)
                gasRepository.save(updatedGas)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("GasService: updateGasImageUrl($gasId, $imageUrl) - error: ${e.message}")
            false
        }
    }

    // Метод для получения всех газов с их изображениями
    fun getAllGasesWithImages(): List<Map<String, Any>> {
        return gasRepository.findAll().map { gas ->
            mapOf(
                "id" to (gas.id ?: 0L),
                "name" to gas.name,
                "formula" to gas.formula,
                "imageUrl" to (gas.imageUrl ?: ""),
                "hasCustomImage" to (gas.imageUrl != null)
            )
        }
    }

    // Методы для API
    fun saveGas(gas: ru.mstu.yandex.gas.entity.Gas): ru.mstu.yandex.gas.entity.Gas {
        return gasRepository.save(gas)
    }

    fun deleteGas(gasId: Long) {
        gasRepository.deleteById(gasId)
    }

    fun getAllGasesWithFilter(filter: ru.mstu.yandex.gas.dto.GasFilterDto, pageable: org.springframework.data.domain.Pageable): List<ru.mstu.yandex.gas.entity.Gas> {
        return when {
            !filter.name.isNullOrBlank() && !filter.formula.isNullOrBlank() -> {
                gasRepository.findByNameContainingIgnoreCaseAndFormulaContainingIgnoreCase(filter.name, filter.formula, pageable).content
            }
            !filter.name.isNullOrBlank() -> {
                gasRepository.findByNameContainingIgnoreCase(filter.name, pageable).content
            }
            !filter.formula.isNullOrBlank() -> {
                gasRepository.findByFormulaContainingIgnoreCase(filter.formula, pageable).content
            }
            else -> {
                gasRepository.findAll(pageable).content
            }
        }
    }

    fun getGasesCount(filter: ru.mstu.yandex.gas.dto.GasFilterDto): Long {
        return when {
            !filter.name.isNullOrBlank() && !filter.formula.isNullOrBlank() -> {
                gasRepository.countByNameContainingIgnoreCaseAndFormulaContainingIgnoreCase(filter.name, filter.formula)
            }
            !filter.name.isNullOrBlank() -> {
                gasRepository.countByNameContainingIgnoreCase(filter.name)
            }
            !filter.formula.isNullOrBlank() -> {
                gasRepository.countByFormulaContainingIgnoreCase(filter.formula)
            }
            else -> {
                gasRepository.count()
            }
        }
    }
}
