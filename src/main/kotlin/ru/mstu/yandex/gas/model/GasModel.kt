package ru.mstu.yandex.gas.model

data class NumericalValue(
    val value: String,
    val unit: String,
    val label: String,
    val note: String? = null
)

data class GasModel(
    val id: Long,
    val name: String,
    val fullName: String,
    val concentration: String,
    val temperature: String,
    val image: String,
    val description: String,
    val numericalValues: Map<String, NumericalValue>,
    val properties: List<String>,
    val applications: List<String>
)
