package com.example.medilingo.controller.drug.response;

import java.util.Collections;
import java.util.List;

public record NormalizedDrug(
        List<String> activeIngredients,
        String dose,
        String form,
        String notes
) {
	/**
	 * Factory for the symptom-mapping pathway where Gemini returns a single ingredient.
	 */
	public static NormalizedDrug ofSingle(String activeIngredient, String dose, String form, String notes) {
		List<String> list = (activeIngredient == null || activeIngredient.isBlank())
				? Collections.emptyList()
				: List.of(activeIngredient);
		return new NormalizedDrug(list, dose, form, notes);
	}

	/**
	 * Convenience alias – returns the single / primary ingredient, or null.
	 */
	public String activeIngredient() {
		return primaryIngredient();
	}

	// Convenience method for the rest of your pipeline
	public String primaryIngredient() {
		return (activeIngredients != null && !activeIngredients.isEmpty())
			? activeIngredients.get(0)
			: null;
	}

	public boolean isMultiIngredient() {
		return activeIngredients != null && activeIngredients.size() > 1;
	}
}