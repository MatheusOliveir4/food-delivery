package com.food_delivery.orderservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.food_delivery.orderservice.dtos.response.CartItemResponse;
import com.food_delivery.orderservice.dtos.response.CartResponse;
import com.food_delivery.orderservice.entities.Cart;
import com.food_delivery.orderservice.entities.CartItem;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CartMapper {
    CartResponse mapToCartResponse(Cart cart);

    CartItemResponse mapToCartItemResponse(CartItem item);
}
