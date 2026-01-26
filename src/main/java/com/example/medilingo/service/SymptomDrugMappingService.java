package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SymptomDrugMappingService {
    private final DrugTranslateService drugTranslateService;
    private final SymptomDrugGeminiService geminiService;

    public DrugTranslateResponse symptomDrugMapping(SymptomDrugMappingRequest req){

        NormalizedDrug normalized = geminiService.recommendFromSymptoms(req);

        if (normalized.activeIngredient() == null){
            return new DrugTranslateResponse(
                    normalized,
                    List.of(), // MVP: no local products yet
                    disclaimer()
            );
        }
        List<LocalProductDto> products =
                geminiService.recommendLocalProducts(
                        req.countryCode(),
                        normalized
                );

        return new DrugTranslateResponse(
                normalized,
                products, // MVP: no local products yet
                disclaimer()
        );
    }

    private String disclaimer() {
        return "의학적 진단이 아니며, 일반적인 정보 제공 목적입니다. 복용 전 약사/의사 상담을 권장합니다.";
    }
}
