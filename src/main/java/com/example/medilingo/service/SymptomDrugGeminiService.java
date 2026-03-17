package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SymptomDrugGeminiService {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    public NormalizedDrug recommendFromSymptoms(SymptomDrugMappingRequest req) {
        String prompt = buildPrompt(req);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2
                )
        );

        try {
            String raw = geminiWebClient.post()
                    .uri("/models/{model}:generateContent?key={key}", model, geminiApiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) {
                return NormalizedDrug.ofSingle(null, null, null, "LLM returned empty output");
            }

            String text = extractTextFromGemini(raw);
            return parseNormalizedDrug(text);

        } catch (Exception e) {
            return NormalizedDrug.ofSingle(null, null, null, "LLM recommend failed: " + e.getMessage());
        }
    }

    private String buildPrompt(SymptomDrugMappingRequest req) {
        return """
            You are a strict JSON generator.
            
            Task:
            Given a user's symptoms (and optional patient/constraints), suggest ONE common OVER-THE-COUNTER (OTC) active ingredient
            that is reasonably appropriate for symptomatic relief in the target country.
            This is NOT a diagnosis. Do NOT suggest prescription-only drugs.
            
            Return ONLY a valid JSON object with exactly these keys:
            - activeIngredient: string or null
              * Use the INN / generic ingredient name in English (lowercase), e.g. "acetaminophen", "ibuprofen", "loratadine", "dextromethorphan".
              * If you cannot suggest a safe/common OTC ingredient with reasonable confidence, output null.
            - dose: string or null
              * If you can state a typical OTC adult strength/dose form factor, provide a short value like "500mg" or "10mg".
              * If age is missing or patient is a minor, or you are unsure, output null.
            - form: string or null
              * Choose ONLY from: "tablet", "capsule", "syrup", "ointment", "patch", "spray", "drops", or null.
            - notes: string
              * Short reasoning (1–3 sentences) explaining symptom→ingredient choice and key cautions.
              * Mention if constraints affected the choice (allergies/current meds/pregnancy).
              * If activeIngredient is null, explain why and suggest what extra info is needed.
            
            HARD RULES (must follow):
            1) Output JSON only (no markdown, no code fences, no extra text).
            2) Use null (not empty string) when a value is missing.
            3) OTC ONLY. If unsure whether OTC in the target country, be conservative.
            4) Do NOT recommend an ingredient that appears in allergies (case-insensitive).
            5) If pregnant=true, be conservative; avoid risky meds and mention pregnancy in notes.
            6) Do NOT output multiple active ingredients (no combo products). Choose ONE or null.
            
            Target countryCode: "%s"
            
            User input JSON:
            %s
            
            Example output format (do not copy content, only format):
            {"activeIngredient":"acetaminophen","dose":"500mg","form":"tablet","notes":"..."}
            """
                .formatted(
                        safe(req.countryCode()).toUpperCase(),
                        toJson(req)
                );
    }

//    public List<LocalProductDto> recommendLocalProducts(String countryCode, NormalizedDrug normalized) {
//        if (normalized == null || isBlank(normalized.activeIngredient())) {
//            return List.of();
//        }
//
//        String prompt = buildLocalProductsPrompt(countryCode, normalized);
//
//        Map<String, Object> body = Map.of(
//                "contents", List.of(
//                        Map.of("parts", List.of(Map.of("text", prompt)))
//                ),
//                "generationConfig", Map.of(
//                        "responseMimeType", "application/json",
//                        "temperature", 0.2
//                )
//        );
//
//        try {
//            String raw = geminiWebClient.post()
//                    .uri("/models/{model}:generateContent?key={key}", model, geminiApiKey)
//                    .bodyValue(body)
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .block();
//
//            if (raw == null || raw.isBlank()) return List.of();
//
//            String text = extractTextFromGemini(raw);
//            if (text == null || text.isBlank()) return List.of();
//
//            return parseLocalProducts(text);
//
//        } catch (Exception e) {
//            return List.of();
//        }
//    }

    private String buildLocalProductsPrompt(String countryCode, NormalizedDrug normalized) {
        return """
            You are a strict JSON generator.
            
            Task:
            Given a target country and a normalized active ingredient (generic/INN), suggest up to 3 plausible OTC product names
            commonly sold in that country that match the ingredient (or commonly contain it as the primary active ingredient).
            
            Return ONLY valid JSON as an array of objects.
            Each object MUST have exactly these keys:
            - name: string
            - imageUrl: null
            - source: string  (MUST be exactly "gemini")
            
            HARD RULES:
            1) Output JSON only (no markdown, no code fences, no extra text).
            2) Output MUST be a JSON array (even if empty).
            3) imageUrl:
               - Use a publicly accessible image URL if you are reasonably confident.
               - Prefer official product pages or major retailers.
               - If unsure, set imageUrl to null.
            4) source MUST be exactly "gemini".
            5) If activeIngredient is null/blank, return [].
            6) Prefer real consumer-facing product names in the language/script used in that country.
            7) Do NOT include prescription-only products. If unsure, omit.
            8) Do NOT include combo cold/flu products unless you are confident the ingredient is the primary one.
            
            Target countryCode: "%s"
            
            NormalizedDrug JSON:
            {"activeIngredient": %s, "dose": %s, "form": %s, "notes": %s}
            
            Example output format:
            [
              {"name":"<product name 1>", "imageUrl":"https://...", "source":"gemini"},
              {"name":"<product name 2>", "imageUrl":null, "source":"gemini"}
            ]
            """
                .formatted(
                        safe(countryCode).toUpperCase(),
                        jsonStringOrNull(normalized.activeIngredient()),
                        jsonStringOrNull(normalized.dose()),
                        jsonStringOrNull(normalized.form()),
                        jsonStringOrNull(normalized.notes())
                );
    }

//    private List<LocalProductDto> parseLocalProducts(String jsonArray) throws Exception {
//        JsonNode arr = objectMapper.readTree(jsonArray);
//        if (!arr.isArray()) return List.of();
//
//        List<LocalProductDto> out = new java.util.ArrayList<>();
//        for (JsonNode p : arr) {
//            String name = asNullable(p.get("name"));
//            if (isBlank(name)) continue;
//
//            // Enforce MVP rules even if Gemini deviates
//            out.add(new LocalProductDto(
//                    name,
//                    sanitizeImageUrl(asNullable(p.get("imageUrl"))),
//                    "gemini"));
//        }
//        return out;
//    }

    private String sanitizeImageUrl(String url) {
        if (url == null) return null;

        String s = url.trim();
        if (s.isBlank()) return null;

        // only allow http(s)
        if (!(s.startsWith("http://") || s.startsWith("https://"))) {
            return null;
        }

        // optional: basic length guard
        if (s.length() > 500) return null;

        return s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isBlank();
    }

    private String jsonStringOrNull(String s) {
        if (s == null || s.trim().isBlank()) return "null";
        // escape quotes minimally
        return "\"" + s.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }


    // helpers you likely already have (or add)
    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    private String extractTextFromGemini(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode parts = root.path("candidates").get(0).path("content").path("parts");
        return parts.get(0).path("text").asText(null);
    }

    private NormalizedDrug parseNormalizedDrug(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return NormalizedDrug.ofSingle(
                asNullable(node.get("activeIngredient")),
                asNullable(node.get("dose")),
                asNullable(node.get("form")),
                node.path("notes").asText("")
        );
    }

    private String asNullable(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isBlank() ? null : s;
    }
}

