package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.DrugTranslateRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DrugTranslateService {
    private final DrugNormalizeService drugNormalizeService;
    private final LocalDrugProductRepository localRepo;
    private final DrugFallbackMappingService fallbackMappingService;

//    public DrugTranslateResponse translate(DrugTranslateRequest req) {
//        NormalizedDrug normalized = drugNormalizeService.normalize(req.koreanDrugText());
//
//        List<LocalProductDto> products = List.of();
//        if (normalized.activeIngredient() != null && !normalized.activeIngredient().isBlank()) {
//            products = localRepo
//                    .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
//                            req.countryCode().toUpperCase(),
//                            normalized.activeIngredient().toLowerCase()
//                    )
//                    .stream()
//                    .map(p -> new LocalProductDto(
//                            p.getLocalName(),
//                            p.getImageUrl(),
//                            p.getSource()
//                    ))
//                    .toList();
//        }
//
//        return new DrugTranslateResponse(
//                normalized,
//                products,
//                "현지 성분/용량은 국가별로 다를 수 있어요. 복용 전 라벨 확인 및 약사/의사 상담을 권장합니다."
//        );
//    }

    public DrugTranslateResponse translate(DrugTranslateRequest req) {
        final String country = normalizeCountry(req.countryCode());
        final String input = safe(req.koreanDrugText());

        // 1) LLM normalize
        NormalizedDrug normalized = drugNormalizeService.normalize(input);

        // 2) fallback mapping if ingredient missing
        if (isBlank(normalized.activeIngredient())) {
            NormalizedDrug fallback = fallbackMappingService.fallbackNormalize(input, normalized);
            normalized = new NormalizedDrug(
                    fallback.activeIngredient(),
                    firstNonBlank(fallback.dose(), normalized.dose()),
                    firstNonBlank(fallback.form(), normalized.form()),
                    normalizeNotes(
                            mergeNotes(
                                    "브랜드명만으로는 성분 확정이 어려울 수 있어요.",
                                    fallback.notes()
                            )
                    )
            );
        } else {
            normalized = new NormalizedDrug(
                    normalized.activeIngredient(),
                    normalized.dose(),
                    normalized.form(),
                    normalizeNotes(normalized.notes())
            );
        }

        // 3) ingredient still missing → 안내 + 빈 결과
        if (isBlank(normalized.activeIngredient())) {
            NormalizedDrug finalNormalized = new NormalizedDrug(
                    null,
                    normalized.dose(),
                    normalized.form(),
                    normalizeNotes(
                            mergeNotes(
                                    normalized.notes(),
                                    "성분명을 알 수 없어요. 가능하면 성분명(예: acetaminophen/ibuprofen)으로 입력해 주세요."
                            )
                    )
            );
            return new DrugTranslateResponse(finalNormalized, List.of(), disclaimer());
        }

        // 4) country + ingredient 조회
        List<LocalProductDto> products = localRepo
                .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
                        country,
                        normalized.activeIngredient().toLowerCase()
                )
                .stream()
                .map(p -> new LocalProductDto(p.getLocalName(), p.getImageUrl(), p.getSource()))
                .toList();

        // 5) products empty → notes 보강
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

        return new DrugTranslateResponse(normalized, products, disclaimer());
    }

    /* =========================
       Helper methods
       ========================= */

    private String disclaimer() {
        return "현지 성분/용량은 국가별로 다를 수 있어요. 복용 전 라벨 확인 및 약사/의사 상담을 권장합니다.";
    }

    private String normalizeCountry(String cc) {
        String s = safe(cc).toUpperCase();
        return s.isBlank() ? "US" : s;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isBlank();
    }

    private String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a.trim();
        if (!isBlank(b)) return b.trim();
        return null;
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
