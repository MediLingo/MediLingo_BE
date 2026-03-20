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
 * Lookup strategy (most precise → least precise):
 *   1. NDC lookup:  GET /api/rximage/1/rxbase?ndc={ndc}
 *   2. Name lookup: GET /api/rximage/1/rxbase?name={brandName}
 *
 * Results are cached in-memory to avoid redundant calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RxImageService {

    private final WebClient rxImageWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // cache key → imageUrl ("" sentinel means "no image found")
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Fetch an image URL, trying NDC first then brand name.
     *
     * @param drugName brand or generic name
     * @param ndc      NDC code from OpenFDA (may be null)
     * @return image URL, or null if not found
     */
    public String fetchImageUrl(String drugName, String ndc) {
        // 1. Try NDC — most precise
        if (ndc != null && !ndc.isBlank()) {
            String url = fetchByNdc(ndc);
            if (url != null) return url;
        }
        // 2. Fall back to name
        if (drugName != null && !drugName.isBlank()) {
            return fetchByName(drugName);
        }
        return null;
    }

    /** Kept for backward compatibility. */
    public String fetchImageUrl(String drugName) {
        return fetchImageUrl(drugName, null);
    }

    // -------------------------------------------------------------------------

    private String fetchByNdc(String ndc) {
        String cacheKey = "ndc:" + ndc.trim();
        String cached = cache.get(cacheKey);
        if (cached != null) return cached.isEmpty() ? null : cached;

        try {
            String raw = rxImageWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/rximage/1/rxbase")
                            .queryParam("ndc", ndc.trim())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String url = parseImageUrl(raw);
            log.info("RxImage NDC lookup for '{}': {}", ndc, url);
            cache.put(cacheKey, url != null ? url : "");
            return url;
        } catch (Exception e) {
            log.warn("RxImage NDC lookup failed for '{}': {}", ndc, e.getMessage());
            return null;
        }
    }

    private String fetchByName(String drugName) {
        String cacheKey = "name:" + drugName.trim().toLowerCase();
        String cached = cache.get(cacheKey);
        if (cached != null) return cached.isEmpty() ? null : cached;

        try {
            String raw = rxImageWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/rximage/1/rxbase")
                            .queryParam("name", drugName.trim())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String url = parseImageUrl(raw);
            log.info("RxImage name lookup for '{}': {}", drugName, url);
            cache.put(cacheKey, url != null ? url : "");
            return url;
        } catch (Exception e) {
            log.warn("RxImage name lookup failed for '{}': {}", drugName, e.getMessage());
            return null;
        }
    }

    private String parseImageUrl(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            int imageCount = root.path("replyStatus").path("imageCount").asInt(0);
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
