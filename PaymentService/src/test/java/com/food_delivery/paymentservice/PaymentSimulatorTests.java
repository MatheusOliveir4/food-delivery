package com.food_delivery.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.food_delivery.paymentservice.entities.enums.PaymentMethod;
import com.food_delivery.paymentservice.simulation.PaymentSimulator;
import com.food_delivery.paymentservice.simulation.PaymentSimulator.Decision;

class PaymentSimulatorTests {
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");

    @Test
    void rateOneAlwaysApproves() {
        PaymentSimulator simulator = new PaymentSimulator(1.0, seeded(1));
        assertThat(decisions(simulator, 200)).allMatch(Decision::approved);
    }

    @Test
    void rateZeroAlwaysRejectsWithReason() {
        PaymentSimulator simulator = new PaymentSimulator(0.0, seeded(1));
        assertThat(decisions(simulator, 200))
            .allMatch(d -> !d.approved() && PaymentSimulator.REJECTION_REASON.equals(d.reason()));
    }

    @Test
    void sameSeedGivesTheSameDecisions() {
        List<Boolean> first = decisions(new PaymentSimulator(0.5, seeded(42)), 50).stream().map(Decision::approved).toList();
        List<Boolean> second = decisions(new PaymentSimulator(0.5, seeded(42)), 50).stream().map(Decision::approved).toList();

        assertThat(first).isEqualTo(second).contains(true, false);
    }

    @Test
    void approvalRateIsRespectedOnAverage() {
        long approved = decisions(new PaymentSimulator(0.8, seeded(7)), 10_000).stream().filter(Decision::approved).count();
        assertThat(approved).isBetween(7_700L, 8_300L);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        PaymentSimulator simulator = new PaymentSimulator(1.0, seeded(1));
        assertThat(simulator.decide(BigDecimal.ZERO, PaymentMethod.PIX).approved()).isFalse();
        assertThat(simulator.decide(new BigDecimal("-1"), PaymentMethod.PIX).approved()).isFalse();
        assertThat(simulator.decide(null, PaymentMethod.PIX).approved()).isFalse();
    }

    @Test
    void invalidRateFailsFast() {
        assertThatThrownBy(() -> new PaymentSimulator(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentSimulator(-0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentSimulator(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Decision> decisions(PaymentSimulator simulator, int count) {
        return IntStream.range(0, count).mapToObj(i -> simulator.decide(AMOUNT, PaymentMethod.CREDIT_CARD)).toList();
    }

    private static RandomGenerator seeded(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }
}
