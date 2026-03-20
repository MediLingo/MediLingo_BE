package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.DrugTranslateRequest;
import com.example.medilingo.controller.drug.response.DrugEntry;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.example.medilingo.domain.drug.DrugSearchLog;
import com.example.medilingo.domain.drug.repository.DrugSearchLogRepository;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugTranslateService {
    private final DrugNormalizeGeminiService drugNormalizeService;
    private final LocalDrugProductRepository localRepo;
    private final DrugFallbackMappingService fallbackMappingService;
    private final DrugSearchLogRepository drugSearchLogRepository;
    private final OpenFdaService openFdaService;
    private final DailyMedImageService dailyMedImageService;

    public DrugTranslateResponse translate(DrugTranslateRequest req) {
        final String country = normalizeCountry(req.countryCode());
        final String input = safe(req.koreanDrugText());

        // 1) LLM normalize
        NormalizedDrug normalized = drugNormalizeService.normalize(input);

        // 2) fallback mapping if ALL ingredients missing
        if (normalized.activeIngredients().isEmpty()) {
            NormalizedDrug fallback = fallbackMappingService.fallbackNormalize(input, normalized);
            normalized = new NormalizedDrug(
                fallback.activeIngredients(),
                firstNonBlank(fallback.dose(), normalized.dose()),
                firstNonBlank(fallback.form(), normalized.form()),
                normalizeNotes(mergeNotes(
                    "브랜드명만으로는 성분 확정이 어려울 수 있어요.",
                    fallback.notes()
                ))
            );
        } else {
            normalized = new NormalizedDrug(
                normalized.activeIngredients(),
                normalized.dose(),
                normalized.form(),
                normalizeNotes(normalized.notes())
            );
        }

        // 3) ingredient still missing → 안내 + 빈 결과
        if (normalized.activeIngredients().isEmpty()) {
            return new DrugTranslateResponse(
                new NormalizedDrug(
                    Collections.emptyList(),
                    normalized.dose(),
                    normalized.form(),
                    normalizeNotes(mergeNotes(
                        normalized.notes(),
                        "성분명을 알 수 없어요. 가능하면 성분명(예: acetaminophen/ibuprofen)으로 입력해 주세요."
                    ))
                ),
                List.of(),
                disclaimer()
            );
        }

        // 4) Look up drug products — OpenFDA for US, local DB for others
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

        // 5) products empty → notes 보강
        if (products.isEmpty()) {
            return new DrugTranslateResponse(
                new NormalizedDrug(
                    normalized.activeIngredients(),
                    normalized.dose(),
                    normalized.form(),
                    normalizeNotes(mergeNotes(
                        normalized.notes(),
                        "해당 국가의 데이터가 아직 부족해요. (seed 데이터 확장 예정)"
                    ))
                ),
                products,
                disclaimer()
            );
        }

        return new DrugTranslateResponse(normalized, products, disclaimer());
    }

    /**
     * Look up US drug products via OpenFDA, then enrich each result
     * with a product image from DailyMed using the SPL set ID.
     */
    private List<LocalProductDto> lookupViaOpenFda(String primaryIngredient) {
        List<DrugEntry> entries = openFdaService.searchByIngredientRich(primaryIngredient, 10);

        return entries.stream()
                .map(e -> {
                    String imageUrl = dailyMedImageService.fetchImageUrl(e.splSetId());
                    return new LocalProductDto(null, e.displayName(), imageUrl, "OpenFDA");
                })
                .toList();
    }


    // Counts how many of the candidate drug's ingredients appear in the Korean drug's ingredient list
    private int countOverlap(List<String> candidateIngredients, List<String> koreanIngredients) {
        if (candidateIngredients == null || candidateIngredients.isEmpty()) return 0;
        return (int) candidateIngredients.stream()
            .filter(ci -> koreanIngredients.stream()
                .anyMatch(ki -> ki.equalsIgnoreCase(ci)))
            .count();
    }

    /* =========================
       Helper methods (unchanged)
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