package com.example.medilingo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches drug product images from NLM's DailyMed service.
 *
 * DailyMed is the official repository for FDA-submitted drug labeling.
 * Manufacturers are required to submit product images along with their labels,
 * so coverage is far better than RxImage (which only covers pill photographs).
 *
 * Lookup strategy:
 *   1. Use the SPL set ID (from OpenFDA's openfda.spl_set_id) to query:
 *        GET /dailymed/services/v2/spls/{setId}/media.json
 *   2. Find the first image file (.jpg / .png) in the response
 *   3. Construct the public image URL
 *
 * Results are cached in-memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyMedImageService {

    private final WebClient dailyMedWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // cache: splSetId → imageUrl ("" sentinel = no image found)
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Fetch a product image URL from DailyMed using the SPL set ID.
     *
     * @param splSetId the SPL set ID from OpenFDA (e.g. "3507a3a2-…")
     * @return a public image URL, or null if unavailable
     */
    public String fetchImageUrl(String splSetId) {
        if (splSetId == null || splSetId.isBlank()) return null;

        String key = splSetId.trim().toLowerCase();
        String cached = cache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        try {
            String raw = dailyMedWebClient.get()
                    .uri("/dailymed/services/v2/spls/{setId}/media.json", splSetId.trim())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) {
                log.info("DailyMed returned empty response for SPL '{}'", splSetId);
                cache.put(key, "");
                return null;
            }

            String imageUrl = parseFirstImageUrl(raw, splSetId.trim());
            log.info("DailyMed image for SPL '{}': {}", splSetId, imageUrl);
            cache.put(key, imageUrl != null ? imageUrl : "");
            return imageUrl;

        } catch (Exception e) {
            log.warn("DailyMed media lookup failed for SPL '{}': {}", splSetId, e.getMessage());
            // Don't cache errors — allow retry on next request
            return null;
        }
    }

    /**
     * Parse the DailyMed media response and find the first image file.
     *
     * Response format:
     * {
     *   "data": {
     *     "media": [
     *       { "name": "image1.jpg", "mime_type": "image/jpeg", "url": "..." },
     *       ...
     *     ]
     *   }
     * }
     */
    private String parseFirstImageUrl(String json, String splSetId) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Navigate: data → media array
            JsonNode mediaArray = root.path("data").path("media");
            if (!mediaArray.isArray() || mediaArray.isEmpty()) {
                return null;
            }

            for (JsonNode media : mediaArray) {
                String name = media.path("name").asText("");
                String mimeType = media.path("mime_type").asText("");

                // Look for image files
                if (isImageFile(name, mimeType)) {
                    // Check if a direct URL is provided
                    String url = media.path("url").asText(null);
                    if (url != null && !url.isBlank()) {
                        return url;
                    }
                    // Otherwise construct the DailyMed image URL
                    return "https://dailymed.nlm.nih.gov/dailymed/image.cfm?setid=" + splSetId + "&name=" + name;
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse DailyMed media response for SPL '{}': {}", splSetId, e.getMessage());
            return null;
        }
    }

    private boolean isImageFile(String name, String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) return true;
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif");
    }
}

