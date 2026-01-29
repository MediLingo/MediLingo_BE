package com.example.medilingo.controller.drug.request;

public record DrugTranslateRequest(
        String koreanDrugText,
        String countryCode
) {}
