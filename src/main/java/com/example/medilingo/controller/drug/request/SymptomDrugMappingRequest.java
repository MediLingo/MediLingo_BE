package com.example.medilingo.controller.drug.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SymptomDrugMappingRequest(
        @NotEmpty
        @Valid
        List<SymptomDto> symptoms,

        @NotBlank
        String countryCode,

        // optional
        @Valid
        PatientDto patient,

        @Valid
        ConstraintsDto constraints
) {
}
