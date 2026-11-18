package com.food_delivery.orderservice.clients.stub;

import java.math.BigDecimal;
import java.util.UUID;

import com.food_delivery.orderservice.clients.dto.ProductInfo;
import com.food_delivery.orderservice.clients.dto.RestaurantInfo;

public final class StubData {
    private StubData() {}

    public static final UUID RESTAURANT_OPEN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID RESTAURANT_CLOSED_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    public static final UUID BURGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    public static final UUID FRIES_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    public static final UUID SOLD_OUT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    public static final UUID OTHER_RESTAURANT_PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b4");

    public static final RestaurantInfo RESTAURANT_OPEN =
        new RestaurantInfo(RESTAURANT_OPEN_ID, "Lanchonete Aberta", true);

    public static final RestaurantInfo RESTAURANT_CLOSED =
        new RestaurantInfo(RESTAURANT_CLOSED_ID, "Lanchonete Fechada", false);

    public static final ProductInfo BURGER =
        new ProductInfo(BURGER_ID, "X-Burger", new BigDecimal("25.90"), true, RESTAURANT_OPEN_ID);

    public static final ProductInfo FRIES =
        new ProductInfo(FRIES_ID, "Batata Frita", new BigDecimal("12.50"), true, RESTAURANT_OPEN_ID);

    public static final ProductInfo SOLD_OUT =
        new ProductInfo(SOLD_OUT_ID, "Milkshake (esgotado)", new BigDecimal("15.00"), false, RESTAURANT_OPEN_ID);

    public static final ProductInfo OTHER_RESTAURANT_PRODUCT =
        new ProductInfo(OTHER_RESTAURANT_PRODUCT_ID, "Marmita", new BigDecimal("30.00"), true, RESTAURANT_CLOSED_ID);
}
