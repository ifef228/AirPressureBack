package ru.mstu.yandex.gas.entity

import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "login", nullable = false, unique = true, length = 50)
    val login: String,

    @Column(name = "password", nullable = false, length = 255)
    val password: String,

    @Column(name = "email", length = 100)
    val email: String? = null,

    @Column(name = "first_name", length = 100)
    val firstName: String? = null,

    @Column(name = "last_name", length = 100)
    val lastName: String? = null,

    @Column(name = "role", nullable = false, length = 128)
    val role: String = "USER" // Значение по умолчанию "USER"
)
