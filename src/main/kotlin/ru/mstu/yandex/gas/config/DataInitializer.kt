package ru.mstu.yandex.gas.config

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import ru.mstu.yandex.gas.entity.Gas
import ru.mstu.yandex.gas.entity.User
import ru.mstu.yandex.gas.repository.GasRepository
import ru.mstu.yandex.gas.repository.UserRepository

@Component
class DataInitializer(
    private val gasRepository: GasRepository,
    private val userRepository: UserRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        println("DataInitializer: Starting initialization...")

        // Проверяем, есть ли уже данные в базе
        if (gasRepository.count() == 0L) {
            val gases = listOf(
                Gas(
                    name = "CO₂ (Углекислый газ)",
                    formula = "CO₂",
                    detailedDescription = "Углекислый газ - это бесцветный газ без запаха, который является важной частью углеродного цикла Земли. Он играет ключевую роль в парниковом эффекте и является основным продуктом сгорания органических веществ."
                ),
                Gas(
                    name = "O₂ (Кислород)",
                    formula = "O₂",
                    detailedDescription = "Кислород - это химический элемент, жизненно необходимый для большинства живых организмов. Он составляет около 21% атмосферы Земли и является ключевым компонентом для дыхания."
                ),
                Gas(
                    name = "Ar (Аргон)",
                    formula = "Ar",
                    detailedDescription = "Аргон - это благородный газ, который составляет около 1% атмосферы Земли. Он инертен и не вступает в химические реакции при обычных условиях, что делает его полезным для различных промышленных применений."
                ),
                Gas(
                    name = "N₂ (Азот)",
                    formula = "N₂",
                    detailedDescription = "Азот - это самый распространенный газ в атмосфере Земли, составляющий около 78% её объёма. Он является основным компонентом белков и ДНК, что делает его жизненно важным для всех живых организмов."
                ),
                Gas(
                    name = "H₂O (Водяной пар)",
                    formula = "H₂O",
                    detailedDescription = "Водяной пар - это газообразное состояние воды, которое играет важную роль в климатических процессах. Его концентрация в атмосфере сильно варьируется в зависимости от температуры и влажности."
                )
            )

            gasRepository.saveAll(gases)
            println("✅ Тестовые данные газов загружены в базу данных")
        } else {
            println("ℹ️ Данные газов уже существуют в базе данных, count: ${gasRepository.count()}")
        }

        // Создаем пользователя с ID = 4 если его нет
        if (!userRepository.existsById(4L)) {
            val user = User(
                id = 4L,
                login = "default_user",
                password = "password123",
                role = "USER"
            )
            userRepository.save(user)
            println("✅ Пользователь с ID = 4 создан")
        } else {
            println("ℹ️ Пользователь с ID = 4 уже существует")
        }

        println("DataInitializer: Initialization completed.")
    }
}
