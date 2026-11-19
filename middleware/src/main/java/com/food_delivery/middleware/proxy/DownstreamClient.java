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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.food_delivery.middleware.config.MiddlewareProperties;
import com.food_delivery.middleware.observability.CorrelationIdFilter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

@Component
public class DownstreamClient {
    private static final Set<String> SKIPPED_HEADERS = Set.of(
        "connection", "content-length", "date", "expect", "from", "host", "upgrade", "via",
        "warning", "keep-alive", "transfer-encoding", "te", "trailer", "proxy-connection",
        "x-correlation-id",
        "traceparent", "tracestate", "b3", "x-b3-traceid", "x-b3-spanid", "x-b3-parentspanid", "x-b3-sampled"
    );

    private final HttpClient httpClient;
    private final Tracer tracer;
    private final Propagator propagator;

    public DownstreamClient(MiddlewareProperties properties,
                            ObjectProvider<Tracer> tracer,
                            ObjectProvider<Propagator> propagator) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
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

        URI uri = URI.create(target);
        Span span = tracer.nextSpan()
            .name(request.method() + " " + uri.getHost())
            .remoteServiceName(uri.getHost())
            .tag("http.method", request.method())
            .tag("http.path", request.path())
            .start();

        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            propagator.inject(span.context(), builder, HttpRequest.Builder::header);
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            span.tag("http.status_code", String.valueOf(response.statusCode()));
            return response;
        } catch (IOException | InterruptedException | RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public static boolean isForwardableResponseHeader(String name) {
        return !SKIPPED_HEADERS.contains(name.toLowerCase());
    }
}
