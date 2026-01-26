package com.example.medilingo.controller.drug.request;

import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;

public record SymptomDto(
        @NotBlank
        String name,

        @NotBlank
        @Enumerated
        Severity severity
) {
}
