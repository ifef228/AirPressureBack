// Основной JavaScript файл для AtmosphericTempCalc
console.log('main.js loaded successfully');

// Глобальная корзина с API интеграцией
let cart = {
    items: [],

    addItem: function(gasId) {
        fetch(`/api/cart/add/${gasId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                this.items.push(gasId);
                this.updateCartUI(data.cartCount);
                this.updateCartButton(gasId, true);
                showNotification(data.message);
            }
        })
        .catch(error => {
            console.error('Error adding to cart:', error);
            showNotification('Ошибка при добавлении в корзину');
        });
    },

    removeItem: function(gasId) {
        fetch(`/api/cart/remove/${gasId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                this.items = this.items.filter(id => id !== gasId);
                this.updateCartUI(data.cartCount);
                this.updateCartButton(gasId, false);
                showNotification(data.message);
            }
        })
        .catch(error => {
            console.error('Error removing from cart:', error);
            showNotification('Ошибка при удалении из корзины');
        });
    },

    isInCart: function(gasId) {
        return this.items.includes(gasId);
    },

    getCount: function() {
        return this.items.length;
    },

    loadFromServer: function() {
        fetch('/api/cart/items')
        .then(response => response.json())
        .then(data => {
            this.items = data;
            this.updateCartUI(this.items.length);
        })
        .catch(error => {
            console.error('Error loading cart from server:', error);
        });
    },

    updateCartUI: function(cartCount) {
        const cartBadge = document.getElementById('cart-badge');
        const cartCountElement = document.getElementById('cart-count');
        const cartIcon = document.querySelector('.gas_cart, .gas_cart_empty');

        if (cartBadge && cartCountElement) {
            if (cartCount > 0) {
                cartBadge.classList.remove('hidden');
                cartCountElement.textContent = cartCount;
            } else {
                cartBadge.classList.add('hidden');
            }
        }

        // Обновляем состояние корзины (активная/неактивная)
        if (cartIcon) {
            if (cartCount > 0) {
                // Корзина активна
                cartIcon.className = 'gas_cart w-[80px] h-[80px] rounded-full shadow-lg hover:shadow-xl transition-all duration-300 cursor-pointer flex items-center justify-center hover:scale-110';
                cartIcon.onclick = function() {
                    if (window.location.pathname === '/') {
                        location.href = '/gas-temperature-calculate';
                    } else {
                        location.href = '/';
                    }
                };
            } else {
                // Корзина неактивна
                cartIcon.className = 'gas_cart_empty w-[80px] h-[80px] rounded-full shadow-lg transition-all duration-300 flex items-center justify-center';
                cartIcon.onclick = null;
            }
        }

        // Обновляем кнопки на главной странице
        this.updateCartButtons();
    },

    updateCartButtons: function() {
        this.items.forEach(gasId => {
            const button = document.getElementById(`cart-text-${gasId}`);
            if (button) {
                button.textContent = 'в корзине';
                button.parentElement.classList.add('opacity-75');
            }
        });
    },

    updateCartButton: function(gasId, isInCart) {
        const button = document.getElementById(`cart-text-${gasId}`);
        if (button) {
            button.textContent = isInCart ? 'в корзине' : 'в корзину';
            if (isInCart) {
                button.parentElement.classList.add('opacity-75');
            } else {
                button.parentElement.classList.remove('opacity-75');
            }
        }
    }
};

// Функция для добавления в корзину
function addToCart(gasId) {
    cart.addItem(gasId);
}

// Функция для удаления из корзины
function removeFromCart(gasId) {
    cart.removeItem(gasId);
}

// Функция для удаления из корзины с перезагрузкой страницы
function removeFromCartAndReload(gasId) {
    console.log('=== removeFromCartAndReload START ===');
    console.log('removeFromCartAndReload: starting removal for gasId:', gasId);
    console.log('Button clicked for gas ID:', gasId);
    console.log('gasId type:', typeof gasId);

    // Проверяем, что gasId является числом
    if (!gasId || isNaN(gasId)) {
        console.error('removeFromCartAndReload: invalid gasId:', gasId);
        showNotification('Ошибка: неверный ID газа', 'error');
        return;
    }

    console.log('removeFromCartAndReload: making fetch request to /api/cart/remove/' + gasId);

    fetch(`/api/cart/remove/${gasId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => {
        console.log('removeFromCartAndReload: response received:', response.status, response.statusText);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(data => {
        console.log('removeFromCartAndReload: response data:', data);
        if (data.success) {
            showNotification(data.message);
            console.log('removeFromCartAndReload: success, reloading page in 500ms');
            // Перезагружаем страницу через небольшую задержку
            setTimeout(() => {
                window.location.reload();
            }, 500);
        } else {
            console.error('removeFromCartAndReload: server returned success=false:', data.message);
            showNotification('Ошибка при удалении из корзины: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('removeFromCartAndReload: catch block - error:', error);
        showNotification('Ошибка сети при удалении из корзины: ' + error.message, 'error');
    });

    console.log('=== removeFromCartAndReload END ===');
}

// Функция для очистки всей корзины с перезагрузкой страницы
function clearCartAndReload() {
    console.log('=== clearCartAndReload START ===');

    if (confirm('Вы уверены, что хотите удалить весь заказ? Это действие нельзя отменить.')) {
        console.log('clearCartAndReload: user confirmed, clearing cart');

        fetch('/api/cart/clear', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        })
        .then(response => {
            console.log('clearCartAndReload: response received:', response.status, response.statusText);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log('clearCartAndReload: response data:', data);
            if (data.success) {
                // Обновляем UI корзины
                if (window.AtmosphericTempCalc && window.AtmosphericTempCalc.cart) {
                    window.AtmosphericTempCalc.cart.items = [];
                    window.AtmosphericTempCalc.cart.updateCartUI(0);
                }
                showNotification('Заказ успешно удален');
                console.log('clearCartAndReload: success, reloading page in 500ms');
                // Перезагружаем страницу через небольшую задержку
                setTimeout(() => {
                    window.location.reload();
                }, 500);
            } else {
                console.error('clearCartAndReload: server returned success=false:', data.message);
                showNotification('Ошибка при удалении заказа: ' + data.message, 'error');
            }
        })
        .catch(error => {
            console.error('clearCartAndReload: catch block - error:', error);
            showNotification('Ошибка сети при удалении заказа: ' + error.message, 'error');
        });
    } else {
        console.log('clearCartAndReload: user cancelled');
    }

    console.log('=== clearCartAndReload END ===');
}

// Функция для показа уведомлений
function showNotification(message, type = 'success') {
    // Создаем элемент уведомления
    const notification = document.createElement('div');
    const bgColor = type === 'error' ? 'bg-red-500' : 'bg-green-500';
    notification.className = `fixed top-4 right-4 ${bgColor} text-white px-6 py-3 rounded-lg shadow-lg z-50 transform translate-x-full transition-transform duration-300`;
    notification.textContent = message;

    document.body.appendChild(notification);

    // Анимируем появление
    setTimeout(() => {
        notification.classList.remove('translate-x-full');
    }, 100);

    // Убираем через 3 секунды
    setTimeout(() => {
        notification.classList.add('translate-x-full');
        setTimeout(() => {
            if (document.body.contains(notification)) {
                document.body.removeChild(notification);
            }
        }, 300);
    }, 3000);
}

// Функция для поиска
function setupSearch() {
    const searchForm = document.querySelector('form[method="get"]');
    const searchInput = document.querySelector('input[name="search"]');
    const searchButton = document.querySelector('button[class*="gas_btn"]');

    if (searchForm && searchInput && searchButton) {
        searchButton.addEventListener('click', (e) => {
            e.preventDefault();
            searchForm.submit();
        });

        searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                searchForm.submit();
            }
        });
    }
}

// Функция для анимации появления элементов
function setupAnimations() {
    const animatedElements = document.querySelectorAll('.animate-fade-in');

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
            }
        });
    }, {
        threshold: 0.1
    });

    animatedElements.forEach(element => {
        observer.observe(element);
    });
}

// Функция для обработки кликов по карточкам
function setupCardClicks() {
    const gasCards = document.querySelectorAll('.gas_card');
    gasCards.forEach(card => {
        card.addEventListener('click', function(e) {
            // Если клик был по кнопке, не переходим на страницу газа
            if (e.target.closest('button')) {
                return;
            }

            // Находим ID газа из кнопки
            const button = this.querySelector('button');
            if (button) {
                const onclick = button.getAttribute('onclick');
                if (onclick) {
                    const match = onclick.match(/addToCart\((\d+)\)/);
                    if (match) {
                        const gasId = match[1];
                        window.location.href = `/gas/${gasId}`;
                    }
                }
            }
        });
    });
}

// Функция для инициализации
function init() {
    console.log('init() function called');

    // Загружаем корзину с сервера
    cart.loadFromServer();

    // Настраиваем поиск
    setupSearch();

    // Настраиваем анимации
    setupAnimations();

    // Настраиваем клики по карточкам
    setupCardClicks();

    // Добавляем обработчики для кнопок корзины
    document.addEventListener('click', function(e) {
        if (e.target.closest('.gas_cart')) {
            e.preventDefault();
            window.location.href = '/gas-temperature-calculate';
        }
    });

    console.log('init() function completed');
}

// Запускаем инициализацию когда DOM загружен
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

// Делаем функции глобально доступными
window.addToCart = addToCart;
window.removeFromCart = removeFromCart;
window.removeFromCartAndReload = removeFromCartAndReload;
window.clearCartAndReload = clearCartAndReload;
window.showNotification = showNotification;

console.log('Functions exported to window:', {
    addToCart: typeof window.addToCart,
    removeFromCart: typeof window.removeFromCart,
    removeFromCartAndReload: typeof window.removeFromCartAndReload,
    clearCartAndReload: typeof window.clearCartAndReload,
    showNotification: typeof window.showNotification
});

// Экспортируем функции для использования в других скриптах
window.AtmosphericTempCalc = {
    cart: cart,
    addToCart: addToCart,
    removeFromCart: removeFromCart,
    removeFromCartAndReload: removeFromCartAndReload,
    clearCartAndReload: clearCartAndReload,
    showNotification: showNotification
};

console.log('main.js fully loaded and functions available');
