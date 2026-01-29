package com.example.medilingo.service;

import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UsOpenFdaNdcService {

    private final WebClient openFdaWebClient; // baseUrl = https://api.fda.gov
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openfda.api-key:}")
    private String apiKey;

    /**
     * ingredientInEnglish example: "dextromethorphan"
     */
    public List<LocalProductDto> findTopProductsByIngredient(String ingredientInEnglish, int limit) {
        if (ingredientInEnglish == null || ingredientInEnglish.isBlank()) return List.of();

        int safeLimit = Math.min(Math.max(limit, 1), 100); // openFDA max 100 :contentReference[oaicite:5]{index=5}
        String ingredient = ingredientInEnglish.trim().toUpperCase(Locale.ROOT);

        // active_ingredients.name exists in Drug NDC dataset :contentReference[oaicite:6]{index=6}
        String search = "finished:true AND active_ingredients.name:\"" + escapeQuotes(ingredient) + "\"";

        try {
            String raw = openFdaWebClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/drug/ndc.json")
                                .queryParam("search", search)
                                .queryParam("limit", safeLimit);
                        if (apiKey != null && !apiKey.isBlank()) {
                            uriBuilder.queryParam("api_key", apiKey); // :contentReference[oaicite:7]{index=7}
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(status -> status.isError(), resp -> resp.createException())
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) return List.of();

            JsonNode root = objectMapper.readTree(raw);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) return List.of();

            // De-dupe by brand_name (NDC often has many package variants)
            Set<String> seen = new HashSet<>();
            List<LocalProductDto> out = new ArrayList<>();

            for (JsonNode r : results) {
                String brandName = textOrNull(r.get("brand_name"));
                if (brandName == null) continue;

                String key = brandName.toLowerCase(Locale.ROOT);
                if (seen.contains(key)) continue;
                seen.add(key);

                out.add(new LocalProductDto(brandName, null, "openfda"));

                if (out.size() >= 3) break; // keep your API consistent with "top 10"
            }

            return out;

        } catch (Exception e) {
            return List.of();
        }
    }

    private String textOrNull(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        if (s == null) return null;
        s = s.trim();
        return s.isBlank() ? null : s;
    }

    private String escapeQuotes(String s) {
        return s.replace("\"", "\\\"");
    }
}
