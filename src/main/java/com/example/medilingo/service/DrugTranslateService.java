package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.DrugTranslateRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.controller.drug.response.NormalizedDrug;
import com.example.medilingo.domain.drug.LocalDrugProduct;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import com.example.medilingo.util.DrugMatchingUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugTranslateService {
    private final DrugNormalizeGeminiService drugNormalizeService;
    private final DrugExplainService drugExplainService;
    private final LocalDrugProductRepository localRepo;
    private final DrugFallbackMappingService fallbackMappingService;
    private final OpenFdaService openFdaService;
    private final DailyMedImageService dailyMedImageService;
    private final LocalProductUpsertService upsertService;

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
        List<String> allTargetIngredients = normalized.activeIngredients();

        // Pre-fetch cached explanations for each ingredient (reuses /explain cache)
        Map<String, String> explanations = fetchExplanations(allTargetIngredients, normalized.form());

        if ("US".equals(country)) {
            products = lookupViaOpenFda(allTargetIngredients, explanations);
        } else {
            products = lookupViaLocalRepo(country, normalized.primaryIngredient(), allTargetIngredients, explanations);
        }

        // After products are built, check if all are partial
        boolean allPartial = allTargetIngredients.size() > 1 &&
            products.stream().allMatch(p ->
                p.matchScore() != null && p.matchScore() < allTargetIngredients.size());

        String notes = allPartial
            ? normalizeNotes(mergeNotes(
                normalized.notes(),
                "해당 국가에서 동일한 복합 성분 제품을 찾을 수 없었어요. 아래 약들을 조합하면 비슷한 효과를 얻을 수 있어요."
            ))
            : normalizeNotes(normalized.notes());

        // 5) products empty → notes 보강
        if (products.isEmpty()) {
            return new DrugTranslateResponse(
                new NormalizedDrug(
                    normalized.activeIngredients(),
                    normalized.dose(),
                    normalized.form(),
                    notes
                ),
                products,
                disclaimer()
            );
        }

        return new DrugTranslateResponse(normalized, products, disclaimer());
    }

    // Differs from symptom flow warning — framed around the original drug's ingredients
    private static final String COVERAGE_WARNING_CONTEXT = "원래 약의 성분 중 ";

    /**
     * Look up US drug products via OpenFDA, then enrich each result
     * with a product image from DailyMed using the SPL set ID.
     * Queries once per ingredient, deduplicates by splSetId,
     * then sorts by overlap score so combination drugs surface first.
     */
    private List<LocalProductDto> lookupViaOpenFda(
        List<String> targetIngredients,
        Map<String, String> explanations) {

        Set<String> seenSplIds = new LinkedHashSet<>();

        List<LocalProductDto> results = targetIngredients.stream()
            .flatMap(ingredient ->
                openFdaService.searchByIngredientRich(ingredient, 5).stream())
            .filter(e -> e.splSetId() == null || seenSplIds.add(e.splSetId()))
            .map(e -> DrugMatchingUtils.buildProductDto(
                null,
                e.displayName(),
                dailyMedImageService.fetchImageUrl(e.splSetId()),
                "OpenFDA",
                DrugMatchingUtils.buildReasonFromExplanations(e.allIngredients(), targetIngredients, explanations),
                e.allIngredients(),
                targetIngredients,
                COVERAGE_WARNING_CONTEXT))
            .sorted(byMatchScoreDesc())
            .filter(DrugMatchingUtils::meetsMinimumCoverage)
            .limit(10)
            .toList();

        results.forEach(dto -> upsertService.upsert(
                "US",
                targetIngredients.get(0), //legacy primary ingredient only, since OpenFDA products don't have a clear primary ingredient field
                targetIngredients,
                dto.name(),
                dto.imageUrl(),
                dto.source()
        ));

        return results;
    }

    /**
     * Local repo path — same overlap scoring as OpenFDA path via DrugMatchingUtils.
     */
    private List<LocalProductDto> lookupViaLocalRepo(
        String country,
        String primaryIngredient,
        List<String> targetIngredients,
        Map<String, String> explanations) {

        return localRepo
            .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
                country,
                primaryIngredient.toLowerCase())
            .stream()
            .map(p -> DrugMatchingUtils.buildProductDto(
                p.getId(),
                p.getLocalName(),
                p.getImageUrl(),
                p.getSource(),
                DrugMatchingUtils.buildReasonFromExplanations(p.getAllIngredients(), targetIngredients, explanations),
                p.getAllIngredients(),
                targetIngredients,
                COVERAGE_WARNING_CONTEXT))
            .sorted(byMatchScoreDesc())
            .toList();
    }

    private Comparator<LocalProductDto> byMatchScoreDesc() {
        return Comparator.comparingInt(
            (LocalProductDto p) -> p.matchScore() != null ? p.matchScore() : 0
        ).reversed();
    }

    /* =========================
       Helper methods
       ========================= */

    /**
     * Pre-fetches a plain-language Gemini explanation for each target ingredient.
     * Reuses DrugExplainService.explain() which is Redis-cached, so repeated
     * calls for the same ingredient are free after the first request.
     * Returns a Map of lowercase ingredient → explanation text.
     */
    private Map<String, String> fetchExplanations(List<String> ingredients, String form) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String ingredient : ingredients) {
            try {
                String explanation = drugExplainService.explain(ingredient, form, "ko");
                if (explanation != null && !explanation.isBlank()) {
                    map.put(ingredient.toLowerCase(), explanation.trim());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch explanation for {}: {}", ingredient, e.getMessage());
            }
        }
        return map;
    }

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