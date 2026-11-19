package com.food_delivery.paymentservice.simulation;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.food_delivery.paymentservice.entities.enums.PaymentMethod;

/**
 * Decide se um pagamento simulado é aprovado. Não há gateway real: a decisão é aleatória,
 * com probabilidade {@code app.payments.approval-rate} de aprovar (0.0 a 1.0).
 * Nos testes, a taxa 0.0/1.0 ou um {@link RandomGenerator} com semente fixa tornam o resultado previsível.
 */
@Component
public class PaymentSimulator {
    public static final String REJECTION_REASON = "Pagamento recusado pela operadora (simulado)";

    private final double approvalRate;
    private final RandomGenerator random;

    @Autowired
    public PaymentSimulator(@Value("${app.payments.approval-rate:0.8}") double approvalRate) {
        this(approvalRate, RandomGenerator.getDefault());
    }

    public PaymentSimulator(double approvalRate, RandomGenerator random) {
        if (Double.isNaN(approvalRate) || approvalRate < 0.0 || approvalRate > 1.0) {
            throw new IllegalArgumentException(
                "app.payments.approval-rate deve estar entre 0.0 e 1.0, recebido: " + approvalRate);
        }
        this.approvalRate = approvalRate;
        this.random = random;
    }

    public Decision decide(BigDecimal amount, PaymentMethod method) {
        if (amount == null || amount.signum() <= 0) {
            return Decision.reject("Valor inválido: " + amount);
        }
        // nextDouble() está em [0, 1): taxa 1.0 aprova sempre e 0.0 recusa sempre.
        boolean approved = random.nextDouble() < approvalRate;
        return approved ? Decision.approve() : Decision.reject(REJECTION_REASON);
    }

    public double getApprovalRate() {
        return approvalRate;
    }

    public record Decision(boolean approved, String reason) {
        public static Decision approve() {
            return new Decision(true, null);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }
}
