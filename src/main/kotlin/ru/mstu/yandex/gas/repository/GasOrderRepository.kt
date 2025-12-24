package ru.mstu.yandex.gas.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.mstu.yandex.gas.entity.GasOrder

@Repository
interface GasOrderRepository : JpaRepository<GasOrder, Long> {

    // Методы для получения массивов записей
    override fun findAll(): List<GasOrder>
    fun findByCalcOrderId(calcOrderId: Long): List<GasOrder>
    fun findByGasId(gasId: Long): List<GasOrder>
    fun findByGasIdAndCalcOrderId(gasId: Long, calcOrderId: Long): GasOrder?

    @Query("SELECT go FROM GasOrder go WHERE go.concentration BETWEEN :minConcentration AND :maxConcentration")
    fun findByConcentrationRange(minConcentration: Int, maxConcentration: Int): List<GasOrder>

    @Query("SELECT go FROM GasOrder go WHERE go.temperature BETWEEN :minTemperature AND :maxTemperature")
    fun findByTemperatureRange(minTemperature: Int, maxTemperature: Int): List<GasOrder>

    @Query("SELECT go FROM GasOrder go ORDER BY go.concentration DESC")
    fun findAllOrderByConcentrationDesc(): List<GasOrder>

    @Query("SELECT go FROM GasOrder go ORDER BY go.temperature ASC")
    fun findAllOrderByTemperatureAsc(): List<GasOrder>

    @Query("SELECT go FROM GasOrder go WHERE go.calcOrderId = :calcOrderId ORDER BY go.id ASC")
    fun findByCalcOrderIdOrderById(calcOrderId: Long): List<GasOrder>

    // Метод для удаления записи по gasId и calcOrderId
    @Modifying
    @Query("DELETE FROM GasOrder go WHERE go.gasId = :gasId AND go.calcOrderId = :calcOrderId")
    fun deleteByGasIdAndCalcOrderId(gasId: Long, calcOrderId: Long): Int

    // Метод для проверки существования записи
    @Query("SELECT COUNT(go) FROM GasOrder go WHERE go.gasId = :gasId AND go.calcOrderId = :calcOrderId")
    fun countByGasIdAndCalcOrderId(gasId: Long, calcOrderId: Long): Long

    // Метод для поиска по calcOrderId и gasId (для API)
    @Query("SELECT go FROM GasOrder go WHERE go.calcOrderId = :calcOrderId AND go.gasId = :gasId")
    fun findByCalcOrderIdAndGasId(calcOrderId: Long, gasId: Long): ru.mstu.yandex.gas.entity.GasOrder?

    // Методы для создания записей (наследуются от JpaRepository)
    // save(entity: GasOrder): GasOrder
    // saveAll(entities: Iterable<GasOrder>): List<GasOrder>
}
