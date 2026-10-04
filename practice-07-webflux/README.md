# Практическая работа №7

Реактивный HTTP сервис доставки

Кашпирев Михаил Дмитриевич

Группа ИКБО-11-23

Связь с курсом: **Текущий прогресс BootcampLabs**.


## Реализация

Spring WebFlux, Reactor Netty, модель Delivery из №4: id, orderId, product, address, status. Хранилище — ConcurrentHashMap, поток изменений — Sinks с ограниченным replay.

| HTTP | Путь | Результат |
|---|---|---|
| GET | /api/deliveries | Flux доступных доставок |
| GET | /api/deliveries/{id} | Mono одной доставки |
| POST | /api/deliveries | Создание, 201 |
| PUT | /api/deliveries/{id} | Проверенный переход состояния |
| DELETE | /api/deliveries/{id} | Удаление, 204 |
| GET | /api/deliveries/{id}/stream | SSE с именем delivery-status |
| GET | /api/deliveries/{id}/order | Внешний WebClient GET заказа |

Используются map, filter, flatMap, switchIfEmpty и onErrorResume. В обработчиках нет block и Thread.sleep. Конкурирующие обновления проверяются сравнением старой записи: конфликт возвращает 409. Отсутствующая доставка — 404, некорректные данные — 400, недоступный внешний сервис — 502.

```powershell
docker compose up -d --build --wait
# Order Service включён в этот compose.yaml:
.\demo.ps1
```

Порт 8177; PORT и ORDER_URL меняются через окружение. Начальная доставка 1 соответствует заказу 101. Каждый запуск demo.ps1 создаёт новую доставку, открывает её SSE, выполняет PUT ACCEPTED → IN_TRANSIT → DELIVERED и записывает поток в sse-latest.log. Таким образом, скрипт можно запускать повторно. Исходный подтверждённый запуск сохранён в run.log и sse.log; повторная проверка — repeat-demo.log. SSE публикует реальные изменения из PUT, а не независимый заранее подготовленный список статусов. `curl -N` отключает буферизацию вывода.

## Отличия WebFlux и RSocket

1. WebFlux API здесь использует HTTP, URL и методы GET/POST/PUT/DELETE; №4 — бинарный RSocket поверх TCP.
2. Одному объекту соответствуют HTTP GET + Mono и RSocket Request-Response.
3. Поток WebFlux передаётся как text/event-stream (SSE); RSocket — через Request-Stream.
4. SSE передаёт события от сервера клиенту; RSocket Channel обменивается двумя потоками в одном взаимодействии.
5. Fire-and-Forget и Channel — модели RSocket. HTTP POST имеет HTTP-ответ и сам по себе не является Fire-and-Forget; общего с RSocket использования Mono/Flux недостаточно, чтобы считать протоколы одинаковыми.

Проверено: HTTP CRUD, WebClient к отдельному Order Service и все четыре реальных состояния в SSE. Дополнительные проверки в verification.log подтверждают 201, 204, 404, 409 и 400. Повторный запуск demo.ps1 также выполнен успешно.


## BootcampLabs

![Ваш модуль BootcampLabs](screenshots/bootcamp.jpg)

Обязательные задания соответствующего модуля зачтены; общий прогресс курса 100%. Необязательные вводные задания на странице модуля могут оставаться «Не начато».

## Сборка образов

Основные Dockerfile используют готовые `target/app.jar`, приложенные к проекту. Для запуска через Compose достаточно Docker Desktop. Исходники и pom.xml сохранены; для пересборки нужен JDK 21 и Maven 3.9: `mvn package` в каждом каталоге сервиса. Dockerfile.source также содержит вариант полной сборки из исходников в контейнере.
