#!/bin/bash

# Скрипт для генерации SSL сертификата для Spring Boot
# Создает самоподписанный сертификат для локальной разработки

CERT_DIR="src/main/resources/certs"
KEYSTORE_FILE="$CERT_DIR/keystore.p12"
KEYSTORE_PASSWORD="changeit"
KEY_ALIAS="server"
VALIDITY_DAYS=365

# IP адрес сервера (из ifconfig)
SERVER_IP="192.168.1.13"

echo "🔐 Генерация SSL сертификата для Spring Boot..."
echo ""

# Создаем директорию, если её нет
mkdir -p "$CERT_DIR"

# Удаляем старый keystore, если существует
if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  Удаление старого keystore..."
    rm "$KEYSTORE_FILE"
fi

# Генерируем новый keystore с самоподписанным сертификатом
echo "📝 Создание нового keystore..."
keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE_FILE" \
    -validity "$VALIDITY_DAYS" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -dname "CN=localhost, OU=Development, O=Gas Calculator, L=Moscow, ST=Moscow, C=RU" \
    -ext "SAN=IP:127.0.0.1,IP:$SERVER_IP,IP:0.0.0.0,DNS:localhost,DNS:*.localhost"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Сертификат успешно создан!"
    echo ""
    echo "📋 Детали:"
    echo "   • Файл: $KEYSTORE_FILE"
    echo "   • Пароль: $KEYSTORE_PASSWORD"
    echo "   • Alias: $KEY_ALIAS"
    echo "   • Срок действия: $VALIDITY_DAYS дней"
    echo "   • IP адреса: 127.0.0.1, $SERVER_IP, 0.0.0.0"
    echo ""
    echo "🚀 Теперь можно запустить бэкенд с HTTPS:"
    echo "   cd gas"
    echo "   ./gradlew bootRun"
    echo ""
    echo "📌 Бэкенд будет доступен по адресу:"
    echo "   • https://localhost:8080"
    echo "   • https://$SERVER_IP:8080"
    echo ""
    echo "⚠️  ВАЖНО: Браузер покажет предупреждение о небезопасном сертификате."
    echo "   Это нормально для самоподписанного сертификата."
    echo "   Нажмите 'Дополнительно' → 'Перейти на сайт' (или 'Advanced' → 'Proceed')"
else
    echo ""
    echo "❌ Ошибка при создании сертификата!"
    exit 1
fi
