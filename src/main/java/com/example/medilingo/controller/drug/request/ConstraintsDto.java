package com.example.medilingo.controller.drug.request;

import java.util.List;

public record ConstraintsDto(
        List<String> allergies,
        List<String> currentMeds
) {
}
