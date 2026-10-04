// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title DeliveryLedger — Кашпирев Михаил Дмитриевич, ИКБО-11-23
/// @notice Учебный реестр доставки. price — справочное значение, переводов ETH нет.
contract DeliveryLedger {
    enum Status { Created, Accepted, InTransit, Delivered, Cancelled }
    struct Delivery { uint256 orderId; address customer; address courier; uint256 price; Status status; }
    address public immutable operator;
    uint256 public nextId = 1;
    mapping(uint256 => Delivery) private deliveries;
    mapping(uint256 => bool) public orderRegistered;
    error Unauthorized();
    error UnknownDelivery(uint256 id);
    error InvalidTransition(Status actual, Status expected);
    event DeliveryCreated(uint256 indexed deliveryId, uint256 indexed orderId, address indexed customer, uint256 price);
    event CourierAssigned(uint256 indexed deliveryId, address indexed courier);
    event DeliveryStatusChanged(uint256 indexed deliveryId, Status previous, Status current);
    constructor() { operator = msg.sender; }
    modifier exists(uint256 id) { if(deliveries[id].customer == address(0)) revert UnknownDelivery(id); _; }
    modifier onlyCourier(uint256 id) { if(msg.sender != deliveries[id].courier) revert Unauthorized(); _; }
    function createDelivery(uint256 orderId, uint256 price) external returns(uint256 id) {
        require(orderId > 0 && price > 0, "Positive order and price required");
        require(!orderRegistered[orderId], "Order already registered");
        id = nextId++;
        deliveries[id] = Delivery(orderId, msg.sender, address(0), price, Status.Created);
        orderRegistered[orderId] = true;
        emit DeliveryCreated(id, orderId, msg.sender, price);
    }
    function getDelivery(uint256 id) external view exists(id) returns(Delivery memory) { return deliveries[id]; }
    function assignCourier(uint256 id, address courier) external exists(id) {
        if(msg.sender != operator && msg.sender != deliveries[id].customer) revert Unauthorized();
        require(courier != address(0) && courier != deliveries[id].customer, "Invalid courier");
        expect(id, Status.Created);
        deliveries[id].courier = courier;
        emit CourierAssigned(id, courier);
        change(id, Status.Accepted);
    }
    function startDelivery(uint256 id) external exists(id) onlyCourier(id) { expect(id, Status.Accepted); change(id, Status.InTransit); }
    function completeDelivery(uint256 id) external exists(id) onlyCourier(id) { expect(id, Status.InTransit); change(id, Status.Delivered); }
    function cancelDelivery(uint256 id) external exists(id) {
        Delivery storage d = deliveries[id];
        if(msg.sender != d.customer && msg.sender != operator) revert Unauthorized();
        require(d.status == Status.Created || d.status == Status.Accepted, "Delivery already started");
        change(id, Status.Cancelled);
    }
    function expect(uint256 id, Status expected) private view {
        if(deliveries[id].status != expected) revert InvalidTransition(deliveries[id].status, expected);
    }
    function change(uint256 id, Status current) private {
        Status previous=deliveries[id].status; deliveries[id].status=current;
        emit DeliveryStatusChanged(id,previous,current);
    }
}
