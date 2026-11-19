package com.food_delivery.restaurantservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Restaurant Service API",
        version = "1.0",
        description = "Cadastro de restaurantes, horário de funcionamento e disponibilidade"))
public class OpenApiConfig {
}