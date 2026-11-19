package com.food_delivery.middleware.proxy;

import java.util.List;
import java.util.Map;

public record DownstreamRequest(
    String method,
    String path,
    String query,
    Map<String, List<String>> headers,
    byte[] body
) {
}
