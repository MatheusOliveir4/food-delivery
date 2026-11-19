package com.food_delivery.paymentservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.food_delivery.paymentservice.dtos.response.PaymentResponse;
import com.food_delivery.paymentservice.dtos.response.PaymentTransactionResponse;
import com.food_delivery.paymentservice.entities.Payment;
import com.food_delivery.paymentservice.entities.PaymentTransaction;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {
    PaymentResponse mapToPaymentResponse(Payment payment);

    PaymentTransactionResponse mapToTransactionResponse(PaymentTransaction transaction);
}
