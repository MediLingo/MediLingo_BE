package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymptomDrugMappingService {
    private final SymptomDrugGeminiService geminiService;
    private final LocalDrugProductRepository localRepo;
    private final OpenFdaService openFdaService;
    private final DailyMedImageService dailyMedImageService;

    public DrugTranslateResponse symptomDrugMapping(SymptomDrugMappingRequest req){

        NormalizedDrug normalized = geminiService.recommendFromSymptoms(req);
        String country = normalizeCountry(req.countryCode());

        // No ingredients identified → return empty result with guidance
        if (normalized.activeIngredients() == null || normalized.activeIngredients().isEmpty()){
            return new DrugTranslateResponse(
                    new NormalizedDrug(
                            List.of(),
                            normalized.dose(),
                            normalized.form(),
                            normalizeNotes(mergeNotes(
                                    normalized.notes(),
                                    "증상에 맞는 성분을 추천하기 어려워요. 증상을 좀 더 구체적으로 입력해 주세요."
                            ))
                    ),
                    List.of(),
                    disclaimer()
            );
        }

        // Look up products — OpenFDA for US, local DB for others
        List<LocalProductDto> products;

        if ("US".equals(country)) {
            products = lookupViaOpenFda(normalized.primaryIngredient());
        } else {
            products = localRepo
                    .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
                            country,
                            normalized.primaryIngredient().toLowerCase()
                    )
                    .stream()
                    .map(p -> new LocalProductDto(p.getId(), p.getLocalName(), p.getImageUrl(), p.getSource()))
                    .toList();
        }

        if (products.isEmpty()) {
            NormalizedDrug finalNormalized = new NormalizedDrug(
                    normalized.activeIngredients(),
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
                products,
                disclaimer()
        );
    }

    private String disclaimer() {
        return "의학적 진단이 아니며, 일반적인 정보 제공 목적입니다. 복용 전 약사/의사 상담을 권장합니다.";
    }

    /**
     * Look up US drug products via OpenFDA, then enrich with DailyMed product images.
     */
    private List<LocalProductDto> lookupViaOpenFda(String ingredient) {
        List<OpenFdaService.DrugEntry> entries = openFdaService.searchByIngredientRich(ingredient, 10);
        return entries.stream()
                .map(e -> {
                    String imageUrl = dailyMedImageService.fetchImageUrl(e.splSetId());
                    return new LocalProductDto(null, e.displayName(), imageUrl, "OpenFDA");
                })
                .toList();
    }

    private String normalizeCountry(String cc) {
        if (cc == null || cc.isBlank()) return "US";
        return cc.trim().toUpperCase();
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
