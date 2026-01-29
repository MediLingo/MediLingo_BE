//package com.example.medilingo.service;
//
//import com.example.medilingo.controller.drug.response.NormalizedDrug;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatusCode;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//public class DrugNormalizeService {
//    private final WebClient openAIWebClient;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    public NormalizedDrug normalize(String koreanDrugText) {
//        String prompt = buildPrompt(koreanDrugText);
//
//        Map<String, Object> body = Map.of(
//                "model", "gpt-4.1-mini",
//                "input", new Object[]{
//                        Map.of(
//                                "role", "user",
//                                "content", new Object[]{
//                                        Map.of("type", "input_text", "text", prompt)
//                                }
//                        )
//                },
//                // 모델이 JSON만 내도록 유도
//                "text", Map.of("format", Map.of("type", "json_object"))
//        );
//
//        try {
//            // 1) Responses API 호출
//            String rawJsonOrError = openAIWebClient.post()
//                    .uri("/responses")
//                    .bodyValue(body)
//                    .retrieve()
//                    .onStatus(HttpStatusCode::isError, resp ->
//                            resp.bodyToMono(String.class)
//                                    .defaultIfEmpty("")
//                                    .flatMap(msg -> Mono.error(new RuntimeException(
//                                            "OpenAI error: " + resp.statusCode() + " " + msg
//                                    )))
//                    )
//                    // JsonNode 말고 String으로 받아서 "응답 구조"도 같이 확인 가능하게
//                    .bodyToMono(String.class)
//                    .timeout(Duration.ofSeconds(20))
//                    .onErrorResume(e -> Mono.just("ERROR::" + e.getMessage()))
//                    .block();
//
//            if (rawJsonOrError == null || rawJsonOrError.isBlank()) {
//                return new NormalizedDrug(null, null, null, "LLM returned empty output (null/blank response)");
//            }
//            if (rawJsonOrError.startsWith("ERROR::")) {
//                return new NormalizedDrug(null, null, null, rawJsonOrError); // 에러 이유 그대로
//            }
//
//// 여기서부터는 응답 JSON 전체가 들어있음.
//// Responses API에서 모델 출력 텍스트를 추출:
//            String extracted = extractTextFromResponsesApi(objectMapper.readTree(rawJsonOrError));
//
//            if (extracted == null || extracted.isBlank()) {
//                // 응답은 왔는데 텍스트를 못 뽑는 경우 -> 응답 일부를 notes로 내려서 구조 확인
//                String snippet = rawJsonOrError.length() > 300 ? rawJsonOrError.substring(0, 300) + "..." : rawJsonOrError;
//                return new NormalizedDrug(null, null, null, "LLM response received but text not extracted. snippet=" + snippet);
//            }
//
//            return parseNormalizedDrug(extracted);
//
//        } catch (Exception e) {
//            // 혹시 남는 예외까지 최종 방어
//            return new NormalizedDrug(null, null, null, "LLM normalize failed: " + e.getMessage());
//        }
//    }
//
//    private String buildPrompt(String koreanDrugText) {
//        return """
//                You are a strict parser. Extract drug information from Korean text.
//
//                Return ONLY valid JSON with these keys:
//                - activeIngredient: string or null (INN / generic ingredient name in English; e.g., acetaminophen, ibuprofen)
//                - dose: string or null (e.g., "500mg", "10mg/5mL")
//                - form: string or null (e.g., "tablet", "capsule", "syrup", "ointment", "patch")
//                - notes: string (short note about uncertainty)
//
//                Rules:
//                - If the ingredient is not explicitly known with high confidence, set activeIngredient to null.
//                - Do NOT guess ingredient from brand name unless you are highly confident. If unsure, use null and explain in notes.
//                - Output JSON only. No markdown. No extra text.
//
//                Input: "%s"
//                """.formatted(koreanDrugText == null ? "" : koreanDrugText.replace("\"", "\\\""));
//    }
//
//    /**
//     * Responses API에서 실제 출력 텍스트(JSON 문자열)를 최대한 견고하게 뽑는다.
//     * - output[*].content[*].text
//     * - output_text 같은 케이스도 대비
//     */
//    private String extractTextFromResponsesApi(JsonNode root) {
//        JsonNode output = root.path("output");
//        if (!output.isArray() || output.isEmpty()) return null;
//
//        StringBuilder sb = new StringBuilder();
//
//        // output이 여러 개인 경우도 합쳐서 처리
//        for (JsonNode out : output) {
//            JsonNode content = out.path("content");
//            if (!content.isArray() || content.isEmpty()) continue;
//
//            for (JsonNode c : content) {
//                String type = c.path("type").asText("");
//                // Responses API에서 흔히 보는 타입들 방어적으로 처리
//                if ("output_text".equals(type) || "text".equals(type)) {
//                    String text = c.path("text").asText("");
//                    if (!text.isBlank()) sb.append(text);
//                }
//            }
//        }
//
//        String result = sb.toString().trim();
//        return result.isBlank() ? null : result;
//    }
//
//    private NormalizedDrug parseNormalizedDrug(String json) {
//        try {
//            JsonNode node = objectMapper.readTree(json);
//
//            String activeIngredient = asNullableText(node.get("activeIngredient"));
//            String dose = asNullableText(node.get("dose"));
//            String form = asNullableText(node.get("form"));
//            String notes = node.path("notes").asText("");
//
//            if (activeIngredient != null) activeIngredient = activeIngredient.trim().toLowerCase();
//            if (dose != null) dose = dose.trim();
//            if (form != null) form = form.trim().toLowerCase();
//
//            return new NormalizedDrug(activeIngredient, dose, form, notes);
//        } catch (Exception e) {
//            return new NormalizedDrug(null, null, null, "Failed to parse JSON from LLM: " + e.getMessage());
//        }
//    }
//
//    private String asNullableText(JsonNode node) {
//        if (node == null || node.isNull()) return null;
//        String s = node.asText();
//        if (s == null) return null;
//        s = s.trim();
//        return s.isBlank() ? null : s;
//    }
//
//}
