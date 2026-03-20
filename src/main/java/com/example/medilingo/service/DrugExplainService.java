package com.example.medilingo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugExplainService {

	private final WebClient geminiWebClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${gemini.api-key}")
	private String geminiApiKey;

	@Value("${gemini.model:gemini-2.5-flash}")
	private String model;

	@Cacheable(
		cacheNames = "drugExplanations",
		key = "#ingredient.toLowerCase() + ':' + (#form != null ? #form.toLowerCase() : 'unknown') + ':' + #lang.toLowerCase()"
	)
	public String explain(String ingredient, String form, String lang) {
		log.info("Cache MISS — calling Gemini for: {}:{}:{}", ingredient, form, lang);
		return callGemini(ingredient, form, lang);
	}

	private String callGemini(String ingredient, String form, String lang) {
		String prompt = """
                In %s, explain the following to a traveler in 2-3 simple sentences:
                - What %s (%s) is commonly used for
                - One or two key warnings they should know
                - Whether they typically need a prescription or can buy it OTC
                Keep it simple, friendly, and non-clinical. No bullet points, just plain sentences.
                """.formatted(lang, ingredient, form != null ? form : "tablet");

		Map<String, Object> body = Map.of(
			"contents", List.of(
				Map.of("parts", List.of(
					Map.of("text", prompt)
				))
			),
			"generationConfig", Map.of(
				"temperature", 0.2,
				"maxOutputTokens", 2048
			)
		);

		try {
			String raw = geminiWebClient.post()
				.uri("/models/{model}:generateContent?key={key}", model, geminiApiKey)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(String.class)
				.block();

			log.info("Raw Gemini response: {}", raw);
			if (raw == null || raw.isBlank()) return null;

			JsonNode root = objectMapper.readTree(raw);
			JsonNode parts = root.path("candidates").get(0)
				.path("content").path("parts");

			if (!parts.isArray() || parts.isEmpty()) return null;

			return parts.get(0).path("text").asText(null);

		} catch (Exception e) {
			log.warn("Gemini explain call failed for {}: {}", ingredient, e.getMessage());
			return null;
		}
	}
}