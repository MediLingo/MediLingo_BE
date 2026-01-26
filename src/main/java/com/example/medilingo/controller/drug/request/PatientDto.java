package com.example.medilingo.controller.drug.request;

public record PatientDto(
        Integer age,
        String sex,
        Boolean pregnant
) {
}
