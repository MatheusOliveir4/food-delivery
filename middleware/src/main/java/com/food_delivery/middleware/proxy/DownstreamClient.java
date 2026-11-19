package com.food_delivery.middleware.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.food_delivery.middleware.config.MiddlewareProperties;
import com.food_delivery.middleware.observability.CorrelationIdFilter;

@Component
public class DownstreamClient {
    private static final Set<String> SKIPPED_HEADERS = Set.of(
        "connection", "content-length", "date", "expect", "from", "host", "upgrade", "via",
        "warning", "keep-alive", "transfer-encoding", "te", "trailer", "proxy-connection",
        "x-correlation-id"
    );

    private final HttpClient httpClient;

    public DownstreamClient(MiddlewareProperties properties) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public HttpResponse<byte[]> send(String baseUrl, DownstreamRequest request, Duration readTimeout)
            throws IOException, InterruptedException {

        String target = baseUrl + request.path() + (request.query() == null ? "" : "?" + request.query());

        HttpRequest.BodyPublisher body = request.body().length == 0
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(request.body());

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
            .timeout(readTimeout)
            .method(request.method(), body);

        for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
            if (!SKIPPED_HEADERS.contains(header.getKey().toLowerCase())) {
                header.getValue().forEach(value -> builder.header(header.getKey(), value));
            }
        }

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            builder.header(CorrelationIdFilter.HEADER_NAME, correlationId);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    public static boolean isForwardableResponseHeader(String name) {
        return !SKIPPED_HEADERS.contains(name.toLowerCase());
    }
}
