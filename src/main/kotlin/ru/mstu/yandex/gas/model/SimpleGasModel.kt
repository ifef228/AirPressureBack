package ru.mstu.yandex.gas.model

data class SimpleGasModel(
    val id: Long,
    val name: String,
    val concentration: String,
    val temperature: String,
    val image: String
)
