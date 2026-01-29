package com.example.medilingo.controller.drug.response;

public record NormalizedDrug(
        String activeIngredient,
        String dose,
        String form,
        String notes
) {}