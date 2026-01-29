package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SymptomDrugMappingService {
    private final SymptomDrugGeminiService geminiService;
    private final LocalDrugProductRepository localRepo;

    public DrugTranslateResponse symptomDrugMapping(SymptomDrugMappingRequest req){

        NormalizedDrug normalized = geminiService.recommendFromSymptoms(req);

        if (normalized.activeIngredient() == null){
            return new DrugTranslateResponse(
                    normalized,
                    List.of(), // MVP: no local products yet
                    disclaimer()
            );
        }
//        List<LocalProductDto> products =
//                geminiService.recommendLocalProducts(
//                        req.countryCode(),
//                        normalized
//                );

        List<LocalProductDto> products = localRepo
                .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
                        req.countryCode(),
                        normalized.activeIngredient().toLowerCase()
                )
                .stream()
                .map(p -> new LocalProductDto(p.getId(), p.getLocalName(), p.getImageUrl(), p.getSource()))
                .toList();

        if (products.isEmpty()) {
            NormalizedDrug finalNormalized = new NormalizedDrug(
                    normalized.activeIngredient(),
                    normalized.dose(),
                    normalized.form(),
                    normalizeNotes(
                            mergeNotes(
                                    normalized.notes(),
                                    "해당 국가의 데이터가 아직 부족해요. (seed 데이터 확장 예정)"
                            )
                    )
            );
            return new DrugTranslateResponse(finalNormalized, products, disclaimer());
        }

        return new DrugTranslateResponse(
                normalized,
                products, // MVP: no local products yet
                disclaimer()
        );
    }

    private String disclaimer() {
        return "의학적 진단이 아니며, 일반적인 정보 제공 목적입니다. 복용 전 약사/의사 상담을 권장합니다.";
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isBlank();
    }

    /**
     * notes가 빈 문자열이면 null로 통일
     */
    private String normalizeNotes(String notes) {
        if (notes == null) return null;
        String trimmed = notes.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String mergeNotes(String a, String b) {
        if (isBlank(a)) return b;
        if (isBlank(b)) return a;
        return a.trim() + " " + b.trim();
    }

}
