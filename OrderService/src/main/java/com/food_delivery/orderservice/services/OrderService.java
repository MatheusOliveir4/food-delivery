package com.food_delivery.orderservice.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.orderservice.clients.CatalogClient;
import com.food_delivery.orderservice.clients.RestaurantClient;
import com.food_delivery.orderservice.clients.dto.ProductInfo;
import com.food_delivery.orderservice.clients.dto.RestaurantInfo;
import com.food_delivery.orderservice.dtos.request.CheckoutRequest;
import com.food_delivery.orderservice.dtos.request.RejectOrderRequest;
import com.food_delivery.orderservice.dtos.request.UpdatePreparationRequest;
import com.food_delivery.orderservice.dtos.response.OrderResponse;
import com.food_delivery.orderservice.entities.Cart;
import com.food_delivery.orderservice.entities.CartItem;
import com.food_delivery.orderservice.entities.Order;
import com.food_delivery.orderservice.entities.OrderItem;
import com.food_delivery.orderservice.entities.enums.OrderStatus;
import com.food_delivery.orderservice.events.PaymentEventPublisher;
import com.food_delivery.orderservice.events.PaymentRequestedEvent;
import com.food_delivery.orderservice.events.RefundRequestedEvent;
import com.food_delivery.orderservice.exceptions.BusinessException;
import com.food_delivery.orderservice.exceptions.InvalidOrderStateException;
import com.food_delivery.orderservice.exceptions.ResourceNotFoundException;
import com.food_delivery.orderservice.mappers.OrderMapper;
import com.food_delivery.orderservice.repositories.CartRepository;
import com.food_delivery.orderservice.repositories.OrderRepository;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CatalogClient catalogClient;

    @Autowired
    private RestaurantClient restaurantClient;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @Autowired
    private OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        Order entity = findEntityById(id);

        return orderMapper.mapToOrderResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(orderMapper::mapToOrderResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findByRestaurant(UUID restaurantId, OrderStatus status) {
        List<Order> orders = (status == null)
            ? orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            : orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status);

        return orders.stream()
            .map(orderMapper::mapToOrderResponse)
            .toList();
    }

    @Transactional
    public OrderResponse checkout(Long customerId, CheckoutRequest request) {
        Cart cart = cartRepository.findByCustomerId(customerId)
            .filter(c -> !c.getItems().isEmpty())
            .orElseThrow(() -> new BusinessException("O carrinho está vazio"));

        RestaurantInfo restaurant = restaurantClient.findRestaurant(cart.getRestaurantId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Restaurante não encontrado: " + cart.getRestaurantId()
                )
            );

        if (!Boolean.TRUE.equals(restaurant.open())) {
            throw new BusinessException("O restaurante está fechado: " + restaurant.name());
        }

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setRestaurantId(cart.getRestaurantId());
        order.setPaymentMethod(request.paymentMethod());
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            ProductInfo product = catalogClient.findProduct(cartItem.getProductId())
                .orElseThrow(() ->
                    new BusinessException(
                        "O produto não existe mais no cardápio: " + cartItem.getProductName()
                    )
                );

            if (!Boolean.TRUE.equals(product.available())) {
                throw new BusinessException("Produto indisponível: " + product.name());
            }

            if (!cart.getRestaurantId().equals(product.restaurantId())) {
                throw new BusinessException(
                    "O produto não pertence ao restaurante do pedido: " + product.name()
                );
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.id());
            orderItem.setProductName(product.name());
            orderItem.setUnitPrice(product.price());
            orderItem.setQuantity(cartItem.getQuantity());
            order.addItem(orderItem);

            total = total.add(orderItem.getTotal());
        }

        order.setTotal(total);
        order = orderRepository.saveAndFlush(order);

        cart.getItems().clear();
        cart.setRestaurantId(null);
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);

        paymentEventPublisher.publishPaymentRequested(
            new PaymentRequestedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getTotal(),
                order.getPaymentMethod(),
                LocalDateTime.now()
            )
        );

        return orderMapper.mapToOrderResponse(order);
    }

    @Transactional
    public void handlePaymentApproved(UUID orderId) {
        Order order = findEntityById(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("PaymentApproved ignorado: pedido {} está em {}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PENDING_RESTAURANT);
        orderRepository.save(order);
    }

    @Transactional
    public void handlePaymentRejected(UUID orderId, String reason) {
        Order order = findEntityById(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("PaymentRejected ignorado: pedido {} está em {}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_REJECTED);
        order.setRejectionReason(reason);
        orderRepository.save(order);
    }

    @Transactional
    public OrderResponse accept(UUID id) {
        Order order = findEntityById(id);

        changeStatus(order, OrderStatus.ACCEPTED);

        return save(order);
    }

    @Transactional
    public OrderResponse reject(UUID id, RejectOrderRequest request) {
        Order order = findEntityById(id);

        changeStatus(order, OrderStatus.REJECTED);
        order.setRejectionReason(request != null ? request.reason() : null);

        OrderResponse response = save(order);

        paymentEventPublisher.publishRefundRequested(
            new RefundRequestedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getTotal(),
                order.getRejectionReason(),
                LocalDateTime.now()
            )
        );

        return response;
    }

    @Transactional
    public OrderResponse updatePreparation(UUID id, UpdatePreparationRequest request) {
        Order order = findEntityById(id);

        changeStatus(order, OrderStatus.valueOf(request.status().name()));

        return save(order);
    }

    private void changeStatus(Order order, OrderStatus target) {
        if (!order.getStatus().canTransitionTo(target)) {
            throw new InvalidOrderStateException(
                "Não é possível mudar o pedido de " + order.getStatus() + " para " + target
            );
        }
        order.setStatus(target);
    }

    private OrderResponse save(Order order) {
        order = orderRepository.saveAndFlush(order);

        return orderMapper.mapToOrderResponse(order);
    }

    private Order findEntityById(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Pedido não encontrado: " + id
                )
            );
    }
}
