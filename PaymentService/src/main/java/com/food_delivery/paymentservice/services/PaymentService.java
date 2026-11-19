package com.food_delivery.paymentservice.services;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.paymentservice.dtos.response.PaymentResponse;
import com.food_delivery.paymentservice.entities.Payment;
import com.food_delivery.paymentservice.entities.PaymentTransaction;
import com.food_delivery.paymentservice.entities.enums.PaymentStatus;
import com.food_delivery.paymentservice.entities.enums.TransactionStatus;
import com.food_delivery.paymentservice.entities.enums.TransactionType;
import com.food_delivery.paymentservice.events.PaymentApprovedEvent;
import com.food_delivery.paymentservice.events.PaymentEventPublisher;
import com.food_delivery.paymentservice.events.PaymentRefundedEvent;
import com.food_delivery.paymentservice.events.PaymentRejectedEvent;
import com.food_delivery.paymentservice.events.PaymentRequestedEvent;
import com.food_delivery.paymentservice.exceptions.InvalidPaymentStateException;
import com.food_delivery.paymentservice.exceptions.ResourceNotFoundException;
import com.food_delivery.paymentservice.mappers.PaymentMapper;
import com.food_delivery.paymentservice.repositories.PaymentRepository;
import com.food_delivery.paymentservice.security.AuthenticatedUser;
import com.food_delivery.paymentservice.simulation.PaymentSimulator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final String DEFAULT_REFUND_REASON = "Estorno solicitado";

    private final PaymentRepository paymentRepository;
    private final PaymentSimulator paymentSimulator;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMapper paymentMapper;

    @Transactional(readOnly = true)
    public PaymentResponse findById(AuthenticatedUser user, UUID id) {
        Payment payment = findEntityById(id);
        checkCanRead(user, payment);
        return paymentMapper.mapToPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByOrderId(AuthenticatedUser user, UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado para o pedido: " + orderId));
        checkCanRead(user, payment);
        return paymentMapper.mapToPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findPaymentIdByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).map(Payment::getId);
    }

    /**
     * Cobra (de forma simulada) o pedido e publica PaymentApproved ou PaymentRejected após o commit.
     *
     * <p>Idempotente por {@code orderId}: se o pedido já tem pagamento, não cobra de novo e
     * reenvia o mesmo resultado (útil se o primeiro envio se perdeu). O Order ignora o repetido.
     */
    @Transactional
    public PaymentResponse processPaymentRequest(PaymentRequestedEvent event) {
        Optional<Payment> existing = paymentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            log.warn("PaymentRequested repetido para o pedido {} (eventId={}): pagamento {} já está {}. "
                + "Sem nova cobrança.", event.orderId(), event.eventId(), payment.getId(), payment.getStatus());
            publishChargeResult(payment);
            return paymentMapper.mapToPaymentResponse(payment);
        }

        PaymentSimulator.Decision decision = paymentSimulator.decide(event.amount(), event.paymentMethod());

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setCustomerId(event.customerId());
        payment.setAmount(event.amount());
        payment.setMethod(event.paymentMethod());
        payment.setStatus(decision.approved() ? PaymentStatus.APPROVED : PaymentStatus.REJECTED);
        payment.addTransaction(new PaymentTransaction(
            TransactionType.CHARGE,
            decision.approved() ? TransactionStatus.SUCCEEDED : TransactionStatus.FAILED,
            event.amount(),
            decision.reason()
        ));

        payment = paymentRepository.saveAndFlush(payment);
        log.info("Pagamento {} do pedido {}: {}", payment.getId(), payment.getOrderId(), payment.getStatus());

        publishChargeResult(payment);
        return paymentMapper.mapToPaymentResponse(payment);
    }

    /**
     * Estorna um pagamento aprovado. Usado tanto por {@code POST /payments/{id}/refund}
     * quanto pelo evento RefundRequested. Publica PaymentRefunded após o commit.
     *
     * @throws InvalidPaymentStateException se o pagamento não estiver APPROVED (recusado ou já estornado)
     */
    @Transactional
    public PaymentResponse refund(UUID paymentId, String reason) {
        Payment payment = findEntityById(paymentId);

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                "Só é possível estornar pagamento APPROVED. O pagamento " + paymentId + " está " + payment.getStatus());
        }

        payment.addTransaction(new PaymentTransaction(
            TransactionType.REFUND,
            TransactionStatus.SUCCEEDED,
            payment.getAmount(),
            reason == null || reason.isBlank() ? DEFAULT_REFUND_REASON : reason
        ));
        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.saveAndFlush(payment);

        log.info("Pagamento {} do pedido {} estornado ({})", payment.getId(), payment.getOrderId(), payment.getAmount());
        paymentEventPublisher.publishPaymentRefunded(
            PaymentRefundedEvent.of(payment.getId(), payment.getOrderId(), payment.getAmount()));

        return paymentMapper.mapToPaymentResponse(payment);
    }

    private void publishChargeResult(Payment payment) {
        PaymentTransaction charge = payment.lastTransaction(TransactionType.CHARGE).orElse(null);

        switch (payment.getStatus()) {
            case APPROVED -> paymentEventPublisher.publishPaymentApproved(
                PaymentApprovedEvent.of(payment.getId(), payment.getOrderId(), charge != null ? charge.getId() : null));
            case REJECTED -> paymentEventPublisher.publishPaymentRejected(
                PaymentRejectedEvent.of(payment.getId(), payment.getOrderId(), charge != null ? charge.getReason() : null));
            // Já estornado: o resultado da cobrança não interessa mais ao Order.
            case REFUNDED -> log.info("Pagamento {} já estornado; nada a reenviar", payment.getId());
        }
    }

    private void checkCanRead(AuthenticatedUser user, Payment payment) {
        boolean admin = "ADMIN".equals(user.getRole());
        if (!admin && !payment.getCustomerId().equals(user.getId())) {
            throw new AccessDeniedException("Acesso negado");
        }
    }

    private Payment findEntityById(UUID id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + id));
    }
}
