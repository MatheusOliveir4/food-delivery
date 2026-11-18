package com.food_delivery.orderservice.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.orderservice.clients.CatalogClient;
import com.food_delivery.orderservice.clients.dto.ProductInfo;
import com.food_delivery.orderservice.dtos.request.AddCartItemRequest;
import com.food_delivery.orderservice.dtos.request.UpdateCartItemRequest;
import com.food_delivery.orderservice.dtos.response.CartResponse;
import com.food_delivery.orderservice.entities.Cart;
import com.food_delivery.orderservice.entities.CartItem;
import com.food_delivery.orderservice.exceptions.BusinessException;
import com.food_delivery.orderservice.exceptions.ResourceNotFoundException;
import com.food_delivery.orderservice.mappers.CartMapper;
import com.food_delivery.orderservice.repositories.CartRepository;

@Service
public class CartService {
    private static final int MAX_QUANTITY_PER_ITEM = 99;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CatalogClient catalogClient;

    @Autowired
    private CartMapper cartMapper;

    @Transactional(readOnly = true)
    public CartResponse findByCustomerId(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseGet(() -> new Cart(customerId));

        return cartMapper.mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(Long customerId, AddCartItemRequest request) {
        ProductInfo product = catalogClient.findProduct(request.productId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Produto não encontrado: " + request.productId()
                )
            );

        if (!Boolean.TRUE.equals(product.available())) {
            throw new BusinessException("Produto indisponível: " + product.name());
        }

        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseGet(() -> new Cart(customerId));

        if (cart.getItems().isEmpty()) {
            cart.setRestaurantId(product.restaurantId());
        } else if (!product.restaurantId().equals(cart.getRestaurantId())) {
            throw new BusinessException(
                "O carrinho só pode ter itens de um restaurante. Esvazie o carrinho para pedir de outro."
            );
        }

        CartItem existing = cart.getItems().stream()
            .filter(item -> item.getProductId().equals(product.id()))
            .findFirst()
            .orElse(null);

        if (existing != null) {
            int newQuantity = existing.getQuantity() + request.quantity();
            checkQuantity(newQuantity);
            existing.setQuantity(newQuantity);

            existing.setProductName(product.name());
            existing.setUnitPrice(product.price());
        } else {
            CartItem item = new CartItem();
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setUnitPrice(product.price());
            item.setQuantity(request.quantity());
            cart.addItem(item);
        }

        cart.setUpdatedAt(LocalDateTime.now());
        cart = cartRepository.saveAndFlush(cart);

        return cartMapper.mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long customerId, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = findCartByCustomerId(customerId);
        CartItem item = findItem(cart, itemId);

        item.setQuantity(request.quantity());

        cart.setUpdatedAt(LocalDateTime.now());
        cart = cartRepository.saveAndFlush(cart);

        return cartMapper.mapToCartResponse(cart);
    }

    @Transactional
    public void removeItem(Long customerId, UUID itemId) {
        Cart cart = findCartByCustomerId(customerId);
        CartItem item = findItem(cart, itemId);

        cart.getItems().remove(item);

        if (cart.getItems().isEmpty()) {
            cart.setRestaurantId(null);
        }

        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.saveAndFlush(cart);
    }

    private Cart findCartByCustomerId(Long customerId) {
        return cartRepository.findByCustomerId(customerId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Carrinho não encontrado para o cliente: " + customerId
                )
            );
    }

    private CartItem findItem(Cart cart, UUID itemId) {
        return cart.getItems().stream()
            .filter(item -> item.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Item não encontrado no carrinho: " + itemId
                )
            );
    }

    private void checkQuantity(int quantity) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BusinessException(
                "A quantidade máxima por item é " + MAX_QUANTITY_PER_ITEM
            );
        }
    }
}
