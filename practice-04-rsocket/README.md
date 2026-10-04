# Практическая работа №4

Четыре модели взаимодействия RSocket

Кашпирев Михаил Дмитриевич

Группа ИКБО-11-23

Связь с курсом: **Front-end АС Поставщиков**.


## Реализация

RSocket работает через настоящий TCP-транспорт Netty. Модель Delivery связывает заказ поставщика, товар и адрес доставки: id, orderId, product, address, status. Эту же модель использует практика №7, что позволяет сравнить протоколы на одинаковом сценарии.

| Модель | Запрос | Ответ |
|---|---|---|
| Request-Response | Идентификатор 1 | JSON доставки |
| Request-Stream | Идентификатор 1 | CREATED → ACCEPTED → IN_TRANSIT → DELIVERED, шаг 350 ms |
| Fire-and-Forget | DeliveryViewed:1 | Запись события сервером без прикладного ответа |
| Channel | courier-ready, position, delivered | Поток подтверждений ack |

Запрос идентификатора 999 возвращает ошибку сервера; клиент перехватывает её через onErrorResume и продолжает демонстрацию. Payload освобождается после чтения, сетевые ресурсы закрываются в finally. Клиент обрабатывает входящие элементы Project Reactor: map, filter, doOnNext.

```powershell
.\run.ps1 demo
# Для двух отдельных процессов:
.\run.ps1 server
# Во втором терминале:
.\run.ps1 client
```

Порт по умолчанию 7104; переменная окружения RSOCKET_PORT меняет его. `block` использован в main для ожидания результатов консольной демонстрации, а обработчики RSocket возвращают Mono/Flux.

Проверенный запуск выполнен в Linux контейнере: `docker compose up --build --abort-on-container-exit --exit-code-from rsocket-demo`. Все четыре модели завершились успешно, код выхода 0; полный вывод — `run.log`, скриншоты — `screenshots`. На этом Windows прямой запуск JDK сталкивается с ошибкой селектора; для воспроизводимой демонстрации используйте Docker. Dockerfile использует приложенные target/classes и target/dependency. Для сборки исходников отдельно сохранён Dockerfile.source; также можно выполнить Maven package dependency:copy-dependencies.

Документация: https://rsocket.io/ и https://github.com/rsocket/rsocket-java


## BootcampLabs

![Ваш модуль BootcampLabs](screenshots/bootcamp.jpg)

Обязательные задания соответствующего модуля зачтены; общий прогресс курса 100%. Необязательные вводные задания на странице модуля могут оставаться «Не начато».
