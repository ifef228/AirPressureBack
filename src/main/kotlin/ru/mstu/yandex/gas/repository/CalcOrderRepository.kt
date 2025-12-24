package ru.mstu.yandex.gas.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.mstu.yandex.gas.entity.CalcOrder
import ru.mstu.yandex.gas.entity.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface CalcOrderRepository : JpaRepository<CalcOrder, Long> {

    // Методы для получения массивов записей
    override fun findAll(): List<CalcOrder>
    fun findByUserId(userId: Long): List<CalcOrder>
    fun findByUserIdAndStatus(userId: Long, status: OrderStatus): List<CalcOrder>
    fun findByUserIdAndStatusOrderByTimestampDesc(userId: Long, status: OrderStatus): List<CalcOrder>

    // Поиск активной корзины (черновик)
    fun findByUserIdAndStatusAndTempResultIsNull(userId: Long, status: OrderStatus): CalcOrder?
    // Безопасный вариант: взять самый поздний черновик, если их несколько
    fun findTopByUserIdAndStatusAndTempResultIsNullOrderByTimestampDesc(userId: Long, status: OrderStatus): CalcOrder?
    // Поиск активной корзины без учета tempResult (для случаев, когда tempResult может быть установлен)
    fun findTopByUserIdAndStatusOrderByTimestampDesc(userId: Long, status: OrderStatus): CalcOrder?

    @Query("SELECT co FROM CalcOrder co WHERE co.tempResult BETWEEN :minTemp AND :maxTemp")
    fun findByTempResultRange(minTemp: BigDecimal, maxTemp: BigDecimal): List<CalcOrder>

    @Query("SELECT co FROM CalcOrder co WHERE co.timestamp BETWEEN :startDate AND :endDate")
    fun findByTimestampRange(startDate: LocalDateTime, endDate: LocalDateTime): List<CalcOrder>

    @Query("SELECT co FROM CalcOrder co ORDER BY co.timestamp DESC")
    fun findAllOrderByTimestampDesc(): List<CalcOrder>

    @Query("SELECT co FROM CalcOrder co ORDER BY co.tempResult DESC")
    fun findAllOrderByTempResultDesc(): List<CalcOrder>

    @Query("SELECT co FROM CalcOrder co WHERE co.userId = :userId ORDER BY co.timestamp DESC")
    fun findByUserIdOrderByTimestampDesc(userId: Long): List<CalcOrder>

    // Raw SQL запрос для логического удаления заявки
    @Modifying
    @Query(value = "UPDATE calc_order SET status = 'DELETED' WHERE id = :orderId AND user_id = :userId", nativeQuery = true)
    fun deleteOrderByIdAndUserId(orderId: Long, userId: Long): Int

    // Методы для API с пагинацией
    fun findByStatusOrderByTimestampDesc(status: OrderStatus, pageable: Pageable): org.springframework.data.domain.Page<CalcOrder>
    fun findByStatusNotOrderByTimestampDesc(status: OrderStatus, pageable: Pageable): org.springframework.data.domain.Page<CalcOrder>

    // Методы для получения заявок пользователя с пагинацией
    fun findByUserIdAndStatusOrderByTimestampDesc(userId: Long, status: OrderStatus, pageable: Pageable): org.springframework.data.domain.Page<CalcOrder>
    fun findByUserIdAndStatusNotOrderByTimestampDesc(userId: Long, status: OrderStatus, pageable: Pageable): org.springframework.data.domain.Page<CalcOrder>

    // Методы для подсчета
    fun countByStatus(status: OrderStatus): Long
    fun countByStatusNot(status: OrderStatus): Long

    // Методы для подсчета заявок пользователя
    fun countByUserIdAndStatus(userId: Long, status: OrderStatus): Long
    fun countByUserIdAndStatusNot(userId: Long, status: OrderStatus): Long

    // Методы для фильтрации по дате и статусу
    @Query("SELECT co FROM CalcOrder co WHERE co.status = :status AND co.timestamp BETWEEN :startDate AND :endDate ORDER BY co.timestamp DESC")
    fun findByStatusAndTimestampBetweenOrderByTimestampDesc(status: OrderStatus, startDate: LocalDateTime, endDate: LocalDateTime, pageable: Pageable): org.springframework.data.domain.Page<CalcOrder>

    @Query("SELECT co FROM CalcOrder co WHERE co.status != :status AND co.timestamp BETWEEN :startDate AND :endDate ORDER BY co.timestamp DESC")
    fun findByStatusNotAndTimestampBetweenOrderByTimestampDesc(status: OrderStatus, startDate: LocalDateTime, endDate: LocalDateTime, pageable: Pageable): org.springframework.data.domain.Page<CalcOrder>

    @Query("SELECT COUNT(co) FROM CalcOrder co WHERE co.status = :status AND co.timestamp BETWEEN :startDate AND :endDate")
    fun countByStatusAndTimestampBetween(status: OrderStatus, startDate: LocalDateTime, endDate: LocalDateTime): Long

    @Query("SELECT COUNT(co) FROM CalcOrder co WHERE co.status != :status AND co.timestamp BETWEEN :startDate AND :endDate")
    fun countByStatusNotAndTimestampBetween(status: OrderStatus, startDate: LocalDateTime, endDate: LocalDateTime): Long

    // Методы для создания записей (наследуются от JpaRepository)
    // save(entity: CalcOrder): CalcOrder
    // saveAll(entities: Iterable<CalcOrder>): List<CalcOrder>
}
