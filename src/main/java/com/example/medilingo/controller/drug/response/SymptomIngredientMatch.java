package com.example.medilingo.controller.drug.response;

public record SymptomIngredientMatch(
	String ingredient,       // e.g. "acetaminophen"
	String symptomsCovered,  // e.g. "두통, 발열"
	String reason            // e.g. "acetaminophen은 두통과 발열에 효과적인 OTC 진통해열제입니다"
) {}