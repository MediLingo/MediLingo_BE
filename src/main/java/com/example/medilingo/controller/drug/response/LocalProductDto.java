package com.example.medilingo.controller.drug.response;

public record LocalProductDto(
        Long localProductId,
        String name,
        String imageUrl,
        String source
) {}
