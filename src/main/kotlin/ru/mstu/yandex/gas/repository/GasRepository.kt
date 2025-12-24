package ru.mstu.yandex.gas.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.mstu.yandex.gas.entity.Gas

@Repository
interface GasRepository : JpaRepository<Gas, Long> {

    // Методы для получения массивов записей
    override fun findAll(): List<Gas>
    fun findByNameContainingIgnoreCase(name: String): List<Gas>
    fun findByFormula(formula: String): List<Gas>
    fun findByDetailedDescriptionContainingIgnoreCase(description: String): List<Gas>

    @Query("SELECT g FROM Gas g ORDER BY g.name ASC")
    fun findAllOrderByName(): List<Gas>

    @Query("SELECT g FROM Gas g ORDER BY g.formula ASC")
    fun findAllOrderByFormula(): List<Gas>

    // Методы для API с пагинацией
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): org.springframework.data.domain.Page<Gas>
    fun findByFormulaContainingIgnoreCase(formula: String, pageable: Pageable): org.springframework.data.domain.Page<Gas>
    fun findByNameContainingIgnoreCaseAndFormulaContainingIgnoreCase(
        name: String,
        formula: String,
        pageable: Pageable
    ): org.springframework.data.domain.Page<Gas>

    // Методы для подсчета
    fun countByNameContainingIgnoreCase(name: String): Long
    fun countByFormulaContainingIgnoreCase(formula: String): Long
    fun countByNameContainingIgnoreCaseAndFormulaContainingIgnoreCase(name: String, formula: String): Long

    // Методы для создания записей (наследуются от JpaRepository)
    // save(entity: Gas): Gas
    // saveAll(entities: Iterable<Gas>): List<Gas>
}
