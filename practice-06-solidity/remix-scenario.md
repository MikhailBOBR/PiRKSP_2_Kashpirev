# Проверка в Remix IDE
Кашпирев Михаил Дмитриевич, ИКБО-11-23.

1. Открыть https://remix.ethereum.org/ и создать contracts/DeliveryLedger.sol из прилагаемого файла.
2. Выбрать Solidity 0.8.24 или более новую 0.8.x, Compile DeliveryLedger.sol.
3. Deploy and Run: Remix VM, аккаунт 0 — operator/customer, value 0; Deploy.
4. createDelivery(101, 63200), getDelivery(1): status 0 (Created).
5. assignCourier(1, адрес аккаунта 1): status 1 (Accepted).
6. С аккаунта 0 выполнить completeDelivery(1): revert Unauthorized.
7. Переключить Account на аккаунт 1. Попробовать completeDelivery(1) до startDelivery: revert InvalidTransition(1,2).
8. startDelivery(1): status 2 (InTransit). completeDelivery(1): status 3 (Delivered).
9. getDelivery(1) возвращает orderId, customer, courier, price, status; в консоли видны события.

Выполнять только в Remix VM; тест не использует реальную сеть и оплату транзакций.
