package com.example.medilingo.controller.drug.response;

import java.util.List;

public record DrugTranslateResponse(
        NormalizedDrug normalized,
        List<LocalProductDto> localProducts,
        String disclaimer
) {
}
