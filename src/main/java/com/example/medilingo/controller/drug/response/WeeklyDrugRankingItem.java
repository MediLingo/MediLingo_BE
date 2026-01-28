package com.example.medilingo.controller.drug.response;

public record WeeklyDrugRankingItem(
        Long localProductId,
        String localName,
        String imageUrl,
        String source,
        Long clickCount
) {}