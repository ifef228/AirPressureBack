package ru.mstu.yandex.gas.service

import org.junit.jupiter.api.Test
import ru.mstu.yandex.gas.service.GasCartService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GasCartServiceTest {

    @Autowired
    private lateinit var cartService: GasCartService

    @Test
    fun `test add to cart creates active cart with real ID`() {
        // Добавляем газ в корзину
        cartService.addToCart(1L)

        // Проверяем, что корзина создалась с реальным ID
        val cartItems = cartService.getCartItems()
        assert(cartItems.contains(1L)) { "Газ должен быть в корзине" }

        val cartCount = cartService.getCartItemsCount()
        assert(cartCount == 1) { "Количество элементов в корзине должно быть 1" }

        val isInCart = cartService.isInCart(1L)
        assert(isInCart) { "Газ должен быть в корзине" }
    }

    @Test
    fun `test remove from cart`() {
        // Добавляем газ в корзину
        cartService.addToCart(1L)
        assert(cartService.getCartItemsCount() == 1)

        // Удаляем газ из корзины
        cartService.removeFromCart(1L)

        // Проверяем, что корзина пуста
        assert(cartService.getCartItemsCount() == 0) { "Корзина должна быть пуста" }
        assert(!cartService.isInCart(1L)) { "Газ не должен быть в корзине" }
    }

    @Test
    fun `test clear cart`() {
        // Добавляем несколько газов в корзину
        cartService.addToCart(1L)
        cartService.addToCart(2L)
        cartService.addToCart(3L)

        assert(cartService.getCartItemsCount() == 3)

        // Очищаем корзину
        cartService.clearCart()

        // Проверяем, что корзина пуста
        assert(cartService.getCartItemsCount() == 0) { "Корзина должна быть пуста" }
    }

    @Test
    fun `test duplicate add to cart`() {
        // Добавляем один и тот же газ дважды
        cartService.addToCart(1L)
        cartService.addToCart(1L)

        // Проверяем, что газ добавился только один раз
        assert(cartService.getCartItemsCount() == 1) { "Газ должен добавиться только один раз" }
    }
}
