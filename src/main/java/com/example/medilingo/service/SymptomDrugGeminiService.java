package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.example.medilingo.controller.drug.response.SymptomIngredientMatch;
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
public class SymptomDrugGeminiService {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    // Bundles the parsed ingredient list (as NormalizedDrug) with the
    // per-ingredient match details (symptomsCovered + reason) needed for
    // building recommendation reasons and coverage warnings downstream.
    public record SymptomMappingResult(
        NormalizedDrug normalizedDrug,
        List<SymptomIngredientMatch> matches
    ) {}

    public SymptomMappingResult recommendFromSymptoms(SymptomDrugMappingRequest req) {
        String prompt = buildPrompt(req);

        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.2,
                "maxOutputTokens", 8192
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
                return emptyResult("LLM returned empty output");
            }

            String text = extractTextFromGemini(raw);
            if (text == null || text.isBlank()) {
                return emptyResult("LLM returned empty text");
            }
            try {
                return parseResult(text);
            } catch (Exception parseEx) {
                return emptyResult("LLM response could not be parsed (possibly truncated): " + parseEx.getMessage());
            }

        } catch (Exception e) {
            return emptyResult("LLM recommend failed: " + e.getMessage());
        }
    }

    private String buildPrompt(SymptomDrugMappingRequest req) {
        return """
            You are a strict JSON generator.

            Task:
            Given a user's symptoms (and optional constraints), suggest common OTC active ingredients
            for symptomatic relief in the target country. This is NOT a diagnosis.

            Return ONLY a valid JSON object with exactly these keys:
            - matches: array of objects or empty array []
              * Each object has:
                - ingredient: string (INN/generic name, lowercase, e.g. "acetaminophen")
                - symptomsCovered: string (comma-separated symptoms this ingredient addresses, in the user's language)
                - reason: string (1 sentence explaining why this ingredient, in the user's language)
              * Order from PRIMARY to SECONDARY ingredient.
              * Consolidate where possible — if acetaminophen covers both headache and fever, list both in symptomsCovered.
              * Return empty [] if no safe OTC ingredient can be identified.
            - dose: string or null
            - form: string or null (only: "tablet","capsule","syrup","ointment","patch","spray","drops")
            - notes: string (overall cautions, constraints applied, etc.)

            HARD RULES:
            1) Output JSON only (no markdown, no code fences, no extra text).
            2) Use null for missing scalars. Use [] for missing matches.
            3) OTC ONLY. Conservative if unsure.
            4) Do NOT recommend ingredients listed in allergies.
            5) If pregnant=true, be conservative and mention in notes.

            Target countryCode: "%s"
            User input JSON: %s

            Example output:
            {
              "matches": [
                {"ingredient":"acetaminophen","symptomsCovered":"두통, 발열","reason":"acetaminophen은 두통과 발열에 효과적인 OTC 진통해열제입니다."},
                {"ingredient":"pseudoephedrine","symptomsCovered":"코막힘","reason":"pseudoephedrine은 코막힘 완화에 사용되는 충혈완화제입니다."}
              ],
              "dose": "500mg",
              "form": "tablet",
              "notes": "..."
            }
            """
            .formatted(
                safe(req.countryCode()).toUpperCase(),
                toJson(req)
            );
    }

    // Parses the matches array from Gemini's response into SymptomMappingResult.
    // activeIngredients in NormalizedDrug is derived from the matches array
    // so both structures stay in sync — no risk of them diverging.
    private SymptomMappingResult parseResult(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);

        List<SymptomIngredientMatch> matches = new ArrayList<>();
        List<String> activeIngredients = new ArrayList<>();

        JsonNode matchesNode = node.get("matches");
        if (matchesNode != null && matchesNode.isArray()) {
            for (JsonNode m : matchesNode) {
                String ingredient = asNullable(m.get("ingredient"));
                String symptomsCovered = asNullable(m.get("symptomsCovered"));
                String reason = asNullable(m.get("reason"));
                if (ingredient != null) {
                    ingredient = ingredient.toLowerCase();
                    activeIngredients.add(ingredient);
                    matches.add(new SymptomIngredientMatch(ingredient, symptomsCovered, reason));
                }
            }
        }

        String dose  = asNullable(node.get("dose"));
        String form  = asNullable(node.get("form"));
        String notes = node.path("notes").asText("");

        if (dose != null) dose = dose.trim();
        if (form != null) form = form.trim().toLowerCase();

        return new SymptomMappingResult(
            new NormalizedDrug(activeIngredients, dose, form, notes),
            matches
        );
    }

    private SymptomMappingResult emptyResult(String reason) {
        return new SymptomMappingResult(
            new NormalizedDrug(Collections.emptyList(), null, null, reason),
            List.of()
        );
    }

    private String extractTextFromGemini(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode parts = root.path("candidates").get(0).path("content").path("parts");
        return parts.get(0).path("text").asText(null);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    private String asNullable(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isBlank() ? null : s;
    }
}