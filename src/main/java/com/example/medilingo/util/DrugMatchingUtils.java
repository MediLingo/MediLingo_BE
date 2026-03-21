package com.example.medilingo.util;

import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.SymptomIngredientMatch;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DrugMatchingUtils {

	private DrugMatchingUtils() {}

	// ── Core fuzzy-match predicate ───────────────────────────────────────
	/**
	 * Returns {@code true} when two ingredient strings are "close enough".
	 * Handles salt-form mismatches, e.g. "cetirizine" vs "cetirizine hydrochloride"
	 * by checking if either string contains the other (case-insensitive).
	 */
	private static boolean ingredientMatches(String a, String b) {
		if (a == null || b == null) return false;
		String al = a.toLowerCase().trim();
		String bl = b.toLowerCase().trim();
		return al.equals(bl) || al.contains(bl) || bl.contains(al);
	}

	/**
	 * Counts how many of the target ingredients appear in the candidate's ingredient list.
	 * Uses fuzzy matching to handle salt forms (e.g. "cetirizine" ↔ "cetirizine hydrochloride").
	 */
	public static int countOverlap(
		List<String> candidateIngredients,
		List<String> targetIngredients) {
		if (candidateIngredients == null || candidateIngredients.isEmpty()) return 0;
		return (int) targetIngredients.stream()
			.filter(ti -> candidateIngredients.stream()
				.anyMatch(ci -> ingredientMatches(ci, ti)))
			.count();
	}

	/**
	 * Builds a warning message listing which target ingredients are missing from the candidate.
	 * Returns null if total target ingredients &lt;= 1 (no partial match concept for single ingredient)
	 * or if all ingredients are covered.
	 * warningContext is the prefix of the warning message — differs between translate and symptom flows.
	 */
	public static String buildCoverageWarning(
		List<String> candidateIngredients,
		List<String> targetIngredients,
		String warningContext) {
		if (targetIngredients.size() <= 1) return null;
		List<String> missing = targetIngredients.stream()
			.filter(ti -> candidateIngredients == null || candidateIngredients.stream()
				.noneMatch(ci -> ingredientMatches(ci, ti)))
			.toList();
		if (missing.isEmpty()) return null;
		return warningContext + String.join(", ", missing) + "이(가) 포함되지 않아요. 추가 약이 필요할 수 있어요.";
	}

	/**
	 * Builds a human-readable reason string from which symptom matches this candidate covers.
	 * Only used in the symptom mapping flow — null in the translate flow.
	 */
	public static String buildReasonFromMatches(
		List<String> candidateIngredients,
		List<SymptomIngredientMatch> matches) {
		if (candidateIngredients == null || candidateIngredients.isEmpty()) return null;
		if (matches == null || matches.isEmpty()) return null;
		String result = matches.stream()
			.filter(m -> candidateIngredients.stream()
				.anyMatch(ci -> ingredientMatches(ci, m.ingredient())))
			.map(m -> m.symptomsCovered() + " (" + m.reason() + ")")
			.collect(Collectors.joining(" / "));
		return result.isBlank() ? null : result;
	}

	/**
	 * Builds a human-readable reason for the translate flow using pre-fetched
	 * Gemini explanations for each ingredient. For each target ingredient that
	 * the candidate product contains, appends "ingredientName — explanation".
	 * Returns null if no overlap or no explanations available.
	 */
	public static String buildReasonFromExplanations(
		List<String> candidateIngredients,
		List<String> targetIngredients,
		Map<String, String> explanationsByIngredient) {
		if (candidateIngredients == null || candidateIngredients.isEmpty()) return null;
		if (targetIngredients == null || targetIngredients.isEmpty()) return null;
		String result = targetIngredients.stream()
			.filter(ti -> candidateIngredients.stream()
				.anyMatch(ci -> ingredientMatches(ci, ti)))
			.map(ti -> {
				String explanation = explanationsByIngredient != null
					? explanationsByIngredient.get(ti.toLowerCase())
					: null;
				return explanation != null
					? ti + " — " + explanation.trim()
					: ti;
			})
			.collect(Collectors.joining(" / "));
		return result.isBlank() ? null : result;
	}

	/**
	 * Returns {@code true} if the product covers at least 50% of the target ingredients.
	 * Products below this threshold are considered too incomplete to be useful.
	 */
	public static boolean meetsMinimumCoverage(LocalProductDto p) {
		if (p.totalIngredients() == null || p.totalIngredients() == 0) return true;
		int score = p.matchScore() != null ? p.matchScore() : 0;
		return (double) score / p.totalIngredients() >= 0.1;
	}

	/**
	 * Central factory method for building a LocalProductDto with overlap scoring.
	 * Both DrugTranslateService and SymptomDrugMappingService use this so scoring
	 * logic lives in exactly one place.
	 */
	public static LocalProductDto buildProductDto(
		Long id,
		String name,
		String imageUrl,
		String source,
		String reason,
		List<String> candidateIngredients,
		List<String> targetIngredients,
		String warningContext) {
		int score = countOverlap(candidateIngredients, targetIngredients);
		int total = targetIngredients.size();
		String warning = buildCoverageWarning(candidateIngredients, targetIngredients, warningContext);
		return new LocalProductDto(id, name, imageUrl, source, reason, score, total, warning);
	}
}