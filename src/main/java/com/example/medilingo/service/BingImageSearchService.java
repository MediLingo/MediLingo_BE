package com.example.medilingo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class BingImageSearchService {
    private final WebClient bingWebClient;

    @Value("${bing.key}")
    private String key;

    private final ObjectMapper om = new ObjectMapper();

    public String findImageUrl(String query) {
        if (query == null || query.isBlank()) return null;

        String raw = bingWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v7.0/images/search")
                        .queryParam("q", query)
                        .queryParam("count", 1)
                        .build())
                .header("Ocp-Apim-Subscription-Key", key)
                .retrieve()
                .onStatus(s -> s.isError(), r -> r.createException())
                .bodyToMono(String.class)
                .block();

        if (raw == null || raw.isBlank()) return null;

        try {
            JsonNode root = om.readTree(raw);
            JsonNode v = root.path("value");
            if (!v.isArray() || v.isEmpty()) return null;
            String url = v.get(0).path("contentUrl").asText(null);
            return sanitize(url);
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitize(String url) {
        if (url == null) return null;
        String s = url.trim();
        if (!(s.startsWith("http://") || s.startsWith("https://"))) return null;
        if (s.length() > 800) return null;
        return s;
    }
}

