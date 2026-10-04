# Практическая работа №6 — Solidity

**Кашпирев Михаил Дмитриевич, ИКБО-11-23.**

DeliveryLedger реализован и проверен в Remix IDE 2.6.5, компилятор 0.8.34, среда Remix VM Osaka. Полный отчёт: [DOCX](../Отчёты/Практика_06_Кашпирев_МД.docx). Проверка выполнена 4 октября 2026 года.

## Контракт

Исходник: [DeliveryLedger.sol](contracts/DeliveryLedger.sol). Solidity ^0.8.24, без внешних библиотек. Данные: orderId, customer, courier, price, status. deliveryId — ключ mapping; nextId выдаёт номера с единицы. price — справочная стоимость в условных денежных единицах; контракт не принимает и не переводит ETH.

| Операция | Кто и когда может выполнить |
|---|---|
| createDelivery | Любой клиент; положительная стоимость и новый orderId |
| assignCourier | Оператор или заказчик; только Created, курьер не нулевой и не заказчик |
| startDelivery | Назначенный курьер; только Accepted |
| completeDelivery | Назначенный курьер; только InTransit |
| cancelDelivery | Оператор или заказчик; Created или Accepted |
| getDelivery | Любой адрес; существующая запись |

```text
Created → Accepted → InTransit → Delivered
   └────────┴──→ Cancelled
```

Модификаторы exists и onlyCourier, require, ошибки Unauthorized, UnknownDelivery и InvalidTransition контролируют условия. События: DeliveryCreated, CourierAssigned и DeliveryStatusChanged. Повторная регистрация orderId запрещена.

## Реальная проверка Remix

| Проверка | Фактический результат |
|---|---|
| Account 1 создаёт заказ 101, price 63200 | getDelivery(1) возвращает status 0 |
| Account 1 назначает Account 2 | Два события, состояние Accepted |
| Account 1 пытается завершить | revert Unauthorized |
| Account 2 пытается завершить до начала | revert InvalidTransition actual 1 expected 2 |
| Account 2 выполняет startDelivery и completeDelivery | Обе транзакции успешны, status 3 Delivered |
| Account 1 отменяет завершённую доставку | revert Delivery already started |

Сводка фактических параметров: [run.log](run.log). Состояния интерфейса: remix-created.txt, remix-unauthorized.txt, remix-invalid-transition.txt, remix-delivered.txt, remix-checks.txt. Хеши в сводке сокращены так же, как на экране. Инструкция повторения: [remix-scenario.md](remix-scenario.md).

![Успешное завершение в Remix](screenshots/05-delivered.jpg)

## BootcampLabs

Предметная область — «Доступы и front-end АС сервиса доставки». Модуль завершён на целевом уровне 3.0: обязательные задания 2.0 — 4/4, задания 3.0 — 2/2, опыт 100/130. Общий прогресс личного курса — 100%. Вводное задание и расширение 4.0 являются необязательными.

![Зачтённые задания уровня 3](screenshots/bootcamp-level3.jpg)

Собственный репозиторий: https://github.com/MikhailBOBR/PiRKSP_2_Kashpirev.
