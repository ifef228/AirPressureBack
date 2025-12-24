package ru.mstu.yandex.gas

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GasApplication

fun main(args: Array<String>) {
	runApplication<GasApplication>(*args)
}
