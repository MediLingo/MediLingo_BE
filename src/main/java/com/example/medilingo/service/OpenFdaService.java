package com.example.medilingo.service;

import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Queries the OpenFDA Drug Labeling API (/drug/label.json) to find US drug
 * products matching a given active ingredient.
 *
 * We prefer OTC products first (product_type:"OTC"), then fall back to all
 * labeling records if OTC returns nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenFdaService {

    private final WebClient openFdaWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openfda.api-key:}")
    private String apiKey;

    /**
     * A richer result entry that includes the raw brand name (used for RxImage lookup)
     * alongside the formatted display name.
     */
    public record DrugEntry(String brandName, String displayName) {}

    /**
     * Search OpenFDA label.json for drug products containing the given active ingredient.
     * Tries OTC-only first; falls back to all product types if OTC yields nothing.
     */
    public List<DrugEntry> searchByIngredientRich(String activeIngredient, int limit) {
        if (activeIngredient == null || activeIngredient.isBlank()) {
            return List.of();
        }

        // 1st attempt: OTC only
        List<DrugEntry> results = queryLabel(activeIngredient, true, limit);

        // 2nd attempt: all product types (includes Rx) if OTC returned nothing
        if (results.isEmpty()) {
            log.debug("No OTC results for '{}', retrying without OTC filter", activeIngredient);
            results = queryLabel(activeIngredient, false, limit);
        }

        return results;
    }

    /** Convenience wrapper — imageUrl is always null here (filled in by RxImageService). */
    public List<LocalProductDto> searchByIngredient(String activeIngredient, int limit) {
        return searchByIngredientRich(activeIngredient, limit)
                .stream()
                .map(e -> new LocalProductDto(null, e.displayName(), null, "OpenFDA"))
                .toList();
    }

    // -------------------------------------------------------------------------

    private List<DrugEntry> queryLabel(String activeIngredient, boolean otcOnly, int limit) {
        try {
            // OpenFDA uses Lucene query syntax. The URI builder will percent-encode spaces/quotes.
            String search = "openfda.substance_name:\"" + activeIngredient.toUpperCase() + "\"";
            if (otcOnly) {
                search += " AND openfda.product_type:\"OTC\"";
            }
            final String finalSearch = search;

            String raw = openFdaWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/drug/label.json")
                                .queryParam("search", finalSearch)
                                .queryParam("limit", Math.min(limit, 100));
                        if (apiKey != null && !apiKey.isBlank()) {
                            builder.queryParam("api_key", apiKey);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) {
                log.warn("OpenFDA label.json returned empty response for '{}' (otcOnly={})", activeIngredient, otcOnly);
                return List.of();
            }

            return parseEntries(raw);

        } catch (WebClientResponseException.NotFound e) {
            log.info("No OpenFDA label results for '{}' (otcOnly={})", activeIngredient, otcOnly);
            return List.of();
        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("OpenFDA rate limit exceeded");
            return List.of();
        } catch (Exception e) {
            log.error("OpenFDA label.json call failed for '{}': {}", activeIngredient, e.getMessage());
            return List.of();
        }
    }

    private List<DrugEntry> parseEntries(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode results = root.path("results");
            if (!results.isArray()) return List.of();

            List<DrugEntry> entries = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            for (JsonNode result : results) {
                JsonNode openfdaNode = result.path("openfda");

                // label.json brand_name / generic_name are under openfda.*
                String brandName   = firstArrayValue(openfdaNode, "brand_name");
                String genericName = firstArrayValue(openfdaNode, "generic_name");
                String manufacturer = firstArrayValue(openfdaNode, "manufacturer_name");

                // Strength and form live at the top level in label.json
                String strength   = firstTextValue(result, "dosage_and_administration_table",
                                                   "active_ingredient"); // best-effort
                String dosageForm = firstArrayValue(openfdaNode, "dosage_form");
                if (dosageForm == null) {
                    dosageForm = firstTextValue(result, "dosage_forms_and_strengths");
                }

                // For label.json the strength is often embedded inside active_ingredient section
                String activeIngText = firstTextValue(result, "active_ingredient");
                String parsedStrength = parseStrengthFromText(activeIngText);

                String displayName = buildDisplayName(
                        brandName, genericName,
                        parsedStrength != null ? parsedStrength : strength,
                        dosageForm, manufacturer);

                if (displayName == null || !seen.add(displayName.toLowerCase())) {
                    continue;
                }

                String nameForImage = (brandName != null && !brandName.isBlank()) ? brandName : genericName;
                entries.add(new DrugEntry(nameForImage, displayName));
            }

            return entries;
        } catch (Exception e) {
            log.error("Failed to parse OpenFDA label response", e);
            return List.of();
        }
    }

    /**
     * Extracts a short strength string (e.g. "500 mg") from the active_ingredient
     * label text if present, e.g. "Active ingredient (in each tablet)\nAcetaminophen 500 mg"
     */
    private String parseStrengthFromText(String text) {
        if (text == null || text.isBlank()) return null;
        // Match a number followed by mg/mcg/g/mL
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|g|mL|%|IU))", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String buildDisplayName(String brandName, String genericName,
                                     String strength, String dosageForm, String manufacturer) {
        StringBuilder sb = new StringBuilder();
        if (brandName != null && !brandName.isBlank()) {
            sb.append(brandName);
        } else if (genericName != null && !genericName.isBlank()) {
            sb.append(genericName);
        } else {
            return null;
        }
        if (strength != null && !strength.isBlank()) {
            sb.append(" (").append(strength).append(")");
        }
        if (dosageForm != null && !dosageForm.isBlank()) {
            sb.append(" — ").append(dosageForm);
        }
        if (manufacturer != null && !manufacturer.isBlank()) {
            sb.append(" [").append(manufacturer).append("]");
        }
        return sb.toString();
    }

    private String firstArrayValue(JsonNode node, String fieldName) {
        JsonNode arr = node.path(fieldName);
        if (arr.isArray() && !arr.isEmpty()) {
            String val = arr.get(0).asText(null);
            return (val != null && !val.isBlank()) ? val : null;
        }
        return null;
    }

    /** Reads the first element of a top-level string array field (label.json style). */
    private String firstTextValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode n = node.path(fieldName);
            if (n.isArray() && !n.isEmpty()) {
                String val = n.get(0).asText(null);
                if (val != null && !val.isBlank()) return val;
            } else if (n.isTextual()) {
                String val = n.asText(null);
                if (val != null && !val.isBlank()) return val;
            }
        }
        return null;
    }
}
