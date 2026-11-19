package com.food_delivery.CatalogService.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_ID =
            Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_NAME);

        // Reutiliza o ID recebido se ele for válido.
        // Caso contrário, gera um novo.
        if (correlationId == null ||
                !VALID_ID.matcher(correlationId).matches()) {

            correlationId = UUID.randomUUID().toString();
        }

        String previousId = MDC.get(MDC_KEY);

        try {
            // Disponibiliza o ID para os logs.
            MDC.put(MDC_KEY, correlationId);

            // Devolve o ID ao cliente.
            response.setHeader(HEADER_NAME, correlationId);

            // Continua o processamento da requisição.
            filterChain.doFilter(request, response);

        } finally {

            // Evita deixar dados de uma requisição
            // na thread utilizada pela próxima requisição.
            if (previousId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousId);
            }
        }
    }
}