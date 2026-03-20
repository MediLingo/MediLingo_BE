package com.example.medilingo.controller.drug.response;

public record LocalProductDto(
        Long localProductId,
        String name,
        String imageUrl,
        String source,
		String reason,           // which symptoms/ingredients this covers
		Integer matchScore,      // how many target ingredients this product contains
		Integer totalIngredients,// total target ingredients
		String coverageWarning   // null if full match, warning message if partial
) {
	// Backward-compatible constructor for existing code that doesn't use new fields
	public LocalProductDto(Long localProductId, String name, String imageUrl, String source) {
		this(localProductId, name, imageUrl, source, null, null, null, null);
	}
}
