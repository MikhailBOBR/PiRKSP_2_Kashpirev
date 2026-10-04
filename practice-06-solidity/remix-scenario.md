# Проверка в Remix IDE
Кашпирев Михаил Дмитриевич, ИКБО-11-23.

1. Открыть https://app.remix.live/ и создать contracts/DeliveryLedger.sol из прилагаемого файла.
2. Выбрать Solidity 0.8.24 или более новую 0.8.x, Compile DeliveryLedger.sol.
3. Deploy and Run: Remix VM, Account 1 (первый аккаунт) — operator/customer, value 0; Deploy.
4. createDelivery(101, 63200), getDelivery(1): status 0 (Created).
5. assignCourier(1, адрес Account 2 (второй аккаунт)): status 1 (Accepted).
6. С Account 1 выполнить completeDelivery(1): revert Unauthorized.
7. Переключить Account на Account 2. Попробовать completeDelivery(1) до startDelivery: revert InvalidTransition(1,2).
8. startDelivery(1): status 2 (InTransit). completeDelivery(1): status 3 (Delivered).
9. getDelivery(1) возвращает orderId, customer, courier, price, status; в консоли видны события.

Выполнять только в Remix VM; тест не использует реальную сеть и оплату транзакций.

10. Вернуться к Account 1 и выполнить cancelDelivery(1): ожидается revert Delivery already started.

Этот сценарий фактически проверен 4 октября 2026 года; снимки и протокол приложены.
