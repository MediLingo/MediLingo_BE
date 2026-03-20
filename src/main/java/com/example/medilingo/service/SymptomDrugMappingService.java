package com.example.medilingo.service;

import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.*;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import com.example.medilingo.util.DrugMatchingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymptomDrugMappingService {

    private final SymptomDrugGeminiService geminiService;
    private final LocalDrugProductRepository localRepo;
    private final OpenFdaService openFdaService;
    private final DailyMedImageService dailyMedImageService;

    // Differs from translate flow warning — framed around symptoms, not the original drug
    private static final String COVERAGE_WARNING_CONTEXT = "이 약은 ";

    public DrugTranslateResponse symptomDrugMapping(SymptomDrugMappingRequest req) {
        SymptomDrugGeminiService.SymptomMappingResult mappingResult =
            geminiService.recommendFromSymptoms(req);

        NormalizedDrug normalized = mappingResult.normalizedDrug();
        List<SymptomIngredientMatch> matches = mappingResult.matches();
        String country = normalizeCountry(req.countryCode());

        if (normalized.activeIngredients().isEmpty()) {
            return emptyResponse(normalized,
                "증상에 맞는 성분을 추천하기 어려워요. 증상을 좀 더 구체적으로 입력해 주세요.");
        }

        List<LocalProductDto> products = "US".equals(country)
            ? lookupViaOpenFda(normalized.activeIngredients(), matches)
            : lookupViaLocalRepo(country, normalized, matches);

        if (products.isEmpty()) {
            return emptyResponse(normalized,
                "해당 국가의 데이터가 아직 부족해요. (seed 데이터 확장 예정)");
        }

        return new DrugTranslateResponse(normalized, products, disclaimer());
    }

    // Queries OpenFDA once per ingredient, deduplicates by splSetId,
    // then sorts by overlap score so combination drugs surface first.
    private List<LocalProductDto> lookupViaOpenFda(
        List<String> targetIngredients,
        List<SymptomIngredientMatch> matches) {

        Set<String> seenSplIds = new LinkedHashSet<>();

        return targetIngredients.stream()
            .flatMap(ingredient ->
                openFdaService.searchByIngredientRich(ingredient, 5).stream())
            // deduplicate — same product may appear for multiple ingredient queries
            .filter(e -> e.splSetId() == null || seenSplIds.add(e.splSetId()))
            .map(e -> DrugMatchingUtils.buildProductDto(
                null,
                e.displayName(),
                dailyMedImageService.fetchImageUrl(e.splSetId()),
                "OpenFDA",
                DrugMatchingUtils.buildReasonFromMatches(e.allIngredients(), matches),
                e.allIngredients(),
                targetIngredients,
                COVERAGE_WARNING_CONTEXT))
            .sorted(byMatchScoreDesc())
            .limit(10)
            .toList();
    }

    // Local repo path — same overlap scoring as OpenFDA path via DrugMatchingUtils
    private List<LocalProductDto> lookupViaLocalRepo(
        String country,
        NormalizedDrug normalized,
        List<SymptomIngredientMatch> matches) {

        return localRepo
            .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
                country,
                normalized.primaryIngredient().toLowerCase())
            .stream()
            .map(p -> DrugMatchingUtils.buildProductDto(
                p.getId(),
                p.getLocalName(),
                p.getImageUrl(),
                p.getSource(),
                DrugMatchingUtils.buildReasonFromMatches(p.getAllIngredients(), matches),
                p.getAllIngredients(),
                normalized.activeIngredients(),
                COVERAGE_WARNING_CONTEXT))
            .sorted(byMatchScoreDesc())
            .toList();
    }

    // Centralizes the empty/error response pattern — avoids repeating
    // NormalizedDrug reconstruction with merged notes throughout the method
    private DrugTranslateResponse emptyResponse(NormalizedDrug normalized, String extraNote) {
        return new DrugTranslateResponse(
            new NormalizedDrug(
                normalized.activeIngredients(),
                normalized.dose(),
                normalized.form(),
                normalizeNotes(mergeNotes(normalized.notes(), extraNote))
            ),
            List.of(),
            disclaimer()
        );
    }

    private Comparator<LocalProductDto> byMatchScoreDesc() {
        return Comparator.comparingInt(
            (LocalProductDto p) -> p.matchScore() != null ? p.matchScore() : 0
        ).reversed();
    }

    private String disclaimer() {
        return "의학적 진단이 아니며, 일반적인 정보 제공 목적입니다. 복용 전 약사/의사 상담을 권장합니다.";
    }

    private String normalizeCountry(String cc) {
        if (cc == null || cc.isBlank()) return "US";
        return cc.trim().toUpperCase();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isBlank();
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