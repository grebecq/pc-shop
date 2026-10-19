# «Сборка» — интернет-магазин комплектующих для ПК

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

## Что это

Полноценный интернет-магазин: сайт, Telegram-бот и админка работают на одном бэкенде.
Покупатель выбирает детали, собирает ПК в конфигураторе с проверкой совместимости, оформляет и оплачивает заказ
на сайте или прямо в Telegram. Владелец ведёт каталог, остатки и заказы в админке и получает уведомления в Telegram.

## Зачем

Проект для портфолио: показывает, что я могу сделать магазин «под ключ», как у настоящего бизнеса.
Работает как живой магазин, но оплата тестовая и деньги не списываются. Для реального заказчика
подключаются его ЮKassa, почта, домен и реквизиты, код при этом не меняется.

## Что умеет

- каталог с фильтрами, поиском, отзывами, избранным и сравнением товаров;
- конфигуратор ПК: подбирает совместимые детали и предупреждает о проблемах;
- корзина, промокоды, скидки, самовывоз и доставка курьером;
- оплата через ЮKassa (карта, СБП, SberPay) с возвратом денег при отмене;
- личный кабинет, подтверждение почты, письма о каждом шаге заказа;
- Telegram-бот: каталог, корзина, оплата, «Мои заказы»;
- админка: заказы, товары, категории, остатки, промокоды, реквизиты магазина;
- оферта, политика данных, sitemap, robots.txt, превью ссылок, Яндекс Метрика по согласию на cookie.

**Стек:** Java 21, Spring Boot 4, PostgreSQL 16, Flyway, Thymeleaf, Spring Security, TelegramBots, Testcontainers.

## Как запустить

Нужны JDK 21 и Docker Desktop.

1. Скопируйте `.env.example` в `.env` и задайте `ADMIN_PASSWORD` (от 8 символов).
2. Запустите `PcShopApplication` в IDEA или `./gradlew bootRun`. База и почтовый сервер для тестов поднимутся в Docker сами.

Или всё в Docker одной командой: `docker compose up -d --build`.

- Сайт: http://localhost:8080
- Админка: http://localhost:8080/admin (логин и пароль из `.env`)
- Письма: http://localhost:8025
- API: http://localhost:8080/swagger-ui.html

Бот, ЮKassa, настоящая почта и Метрика включаются, если вписать их ключи в `.env`. Без них всё работает в тестовом режиме.

Тесты: `./gradlew test` (нужен запущенный Docker).

## Деплой

- **Render, бесплатно:** Dashboard → New → Blueprint → этот репозиторий, всё описано в `render.yaml`.
- **Свой сервер:** Docker, `git clone`, файл `.env`, `docker compose up -d --build`, Caddy для https.

## Лицензия

[MIT](LICENSE)
