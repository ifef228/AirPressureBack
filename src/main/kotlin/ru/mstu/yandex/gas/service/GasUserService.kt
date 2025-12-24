package ru.mstu.yandex.gas.service

import org.springframework.stereotype.Service
import ru.mstu.yandex.gas.entity.User
import ru.mstu.yandex.gas.repository.UserRepository

@Service
class GasUserService(
    private val userRepository: UserRepository
) {

    fun saveUser(user: User): User {
        return userRepository.save(user)
    }

    fun findById(id: Long): User? {
        return userRepository.findById(id).orElse(null)
    }

    fun findByLogin(login: String): User? {
        return userRepository.findByLogin(login)
    }

    fun findAll(): List<User> {
        return userRepository.findAll()
    }

    fun deleteUser(id: Long) {
        userRepository.deleteById(id)
    }
}
