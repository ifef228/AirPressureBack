package ru.mstu.yandex.gas.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.mstu.yandex.gas.entity.User

@Repository
interface UserRepository : JpaRepository<User, Long> {

    fun findByLogin(login: String): User?
    fun findByEmail(email: String): User?
    fun existsByLogin(login: String): Boolean
    fun existsByEmail(email: String): Boolean
}
