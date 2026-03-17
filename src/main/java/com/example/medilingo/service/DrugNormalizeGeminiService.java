package com.example.medilingo.service;

import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DrugNormalizeGeminiService {

    private final WebClient geminiWebClient;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public NormalizedDrug normalize(String koreanDrugText) {
        String prompt = buildPrompt(koreanDrugText);

        // Gemini 요청 포맷
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                // 모델이 JSON만 내도록 유도: responseMimeType
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.1
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
                return new NormalizedDrug(Collections.emptyList(), null, null, "LLM call failed or returned empty output");
            }

            String text = extractTextFromGemini(raw);
            if (text == null || text.isBlank()) {
                return new NormalizedDrug(Collections.emptyList(), null, null, "LLM call failed or returned empty output");
            }

            return parseNormalizedDrug(text);

        } catch (Exception e) {
            return new NormalizedDrug(Collections.emptyList(), null, null, "LLM normalize failed: " + e.getMessage());
        }
    }

    private String buildPrompt(String koreanDrugText) {
        return """
            You are a strict information extraction engine for OTC/prescription drug mentions in Korean text.
            Your job is to normalize the drug into generic ingredient names when (and only when) you are confident.
    
            Return ONLY a valid JSON object with exactly these keys:
            - activeIngredients: array of strings or empty array []
              * List ALL active ingredients using INN / generic names in English (lowercase), e.g. ["acetaminophen", "pseudoephedrine"].
              * Order them from PRIMARY (most therapeutically significant) to SECONDARY.
              * If no ingredient can be identified with high confidence, return an empty array [].
            - dose: string or null
              * Extract the strength/dose if explicitly present, e.g. "500mg", "10 mg/5 mL".
              * If multiple doses exist for multiple ingredients, list them comma-separated.
            - form: string or null
              * Extract dosage form ONLY if explicitly indicated, e.g. "tablet", "capsule", "syrup".
              * If not explicitly stated, output null.
            - notes: string
              * Explain what you did and any uncertainty.
              * Mention whether ingredients were explicit vs inferred, whether this looks like a combination product, and any ambiguity.
    
            HARD RULES (must follow):
            1) Output JSON only (no markdown, no code fences, no extra text).
            2) Do NOT guess any ingredient from a brand name unless you are highly confident.
               If not sure, omit that ingredient entirely.
            3) For combination products (e.g. cold/flu multi-symptom), list ALL ingredients you can identify with confidence.
               Only omit ingredients you are uncertain about.
            4) If the input contains conflicting strengths/forms, mention it in notes and keep dose/form null.
            5) Use null (not empty string) for missing scalar values. Use [] (not null) for missing ingredients.
    
            Examples of Korean cues:
            - Dose cues: "mg", "g", "mcg/μg", "mL", "%%", "정", "캡슐", "시럽", "연고", "패치"
            - Brand cues: product/brand names without an explicit ingredient
    
            Input Korean text: "%s"
            """.formatted(koreanDrugText.replace("\"", "\\\""));
    }


    private String extractTextFromGemini(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        // candidates[0].content.parts[0].text
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return null;

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) return null;

        String text = parts.get(0).path("text").asText(null);
        return (text == null) ? null : text.trim();
    }

    private NormalizedDrug parseNormalizedDrug(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<String> activeIngredients = new ArrayList<>();
            JsonNode ingredientsNode = node.get("activeIngredients");
            if (ingredientsNode != null && ingredientsNode.isArray()) {
                for (JsonNode ingredient : ingredientsNode) {
                    String val = ingredient.asText(null);
                    if (val != null && !val.isBlank()) {
                        activeIngredients.add(val.trim().toLowerCase());
                    }
                }
            }

            String dose = asNullableText(node.get("dose"));
            String form = asNullableText(node.get("form"));
            String notes = node.path("notes").asText("");

            if (dose != null) dose = dose.trim();
            if (form != null) form = form.trim().toLowerCase();

            return new NormalizedDrug(activeIngredients, dose, form, notes);

        } catch (Exception e) {
            return new NormalizedDrug(Collections.emptyList(), null, null,
                "Failed to parse JSON from LLM: " + e.getMessage());
        }
    }

    private String asNullableText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String s = node.asText();
        if (s == null) return null;
        s = s.trim();
        return s.isBlank() ? null : s;
    }
}
