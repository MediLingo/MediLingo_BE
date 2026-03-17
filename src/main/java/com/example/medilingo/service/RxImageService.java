package com.example.medilingo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches drug pill images from the NLM RxImage API.
 *
 * Endpoint:
 *   GET https://rximage.nlm.nih.gov/api/rximage/1/rxbase
 *       ?name={drugName}&resolution=600
 *
 * The response JSON has:
 *   replyStatus.imageCount (int)
 *   nlmRxImages[] → each element has imageUrl (String)
 *
 * Results are cached in-memory to avoid redundant calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RxImageService {

    private final WebClient rxImageWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory cache: drugName → imageUrl (or "" for "no image found")
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Fetch an image URL for the given drug name (brand name preferred).
     *
     * @param drugName the brand or generic name to search for
     * @return image URL or null if not found
     */
    public String fetchImageUrl(String drugName) {
        if (drugName == null || drugName.isBlank()) return null;

        String cacheKey = drugName.trim().toLowerCase();

        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : cached; // "" sentinel = no image
        }

        try {
            String raw = rxImageWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/rximage/1/rxbase")
                            .queryParam("name", drugName.trim())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) {
                log.debug("RxImage returned empty body for '{}'", drugName);
                cache.put(cacheKey, "");
                return null;
            }

            log.info("RxImage raw response for '{}': {}", drugName, raw.length() > 300 ? raw.substring(0, 300) : raw);

            String imageUrl = parseImageUrl(raw);
            log.info("RxImage resolved imageUrl for '{}': {}", drugName, imageUrl);
            cache.put(cacheKey, imageUrl != null ? imageUrl : "");
            return imageUrl;

        } catch (Exception e) {
            log.warn("RxImage lookup failed for '{}': {}", drugName, e.getMessage());
            // Don't cache errors — allow retry
            return null;
        }
    }

    private String parseImageUrl(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode replyStatus = root.path("replyStatus");
            int imageCount = replyStatus.path("imageCount").asInt(0);

            if (imageCount == 0) return null;

            JsonNode images = root.path("nlmRxImages");
            if (images.isArray() && !images.isEmpty()) {
                String url = images.get(0).path("imageUrl").asText(null);
                if (url != null && !url.isBlank()) return url;
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse RxImage response: {}", e.getMessage());
            return null;
        }
    }
}

