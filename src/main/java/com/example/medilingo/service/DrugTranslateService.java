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

    public DrugTranslateResponse translate(DrugTranslateRequest req) {
        NormalizedDrug normalized = drugNormalizeService.normalize(req.koreanDrugText());

        List<LocalProductDto> products = List.of();
        if (normalized.activeIngredient() != null && !normalized.activeIngredient().isBlank()) {
            products = localRepo
                    .findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
                            req.countryCode().toUpperCase(),
                            normalized.activeIngredient().toLowerCase()
                    )
                    .stream()
                    .map(p -> new LocalProductDto(
                            p.getLocalName(),
                            p.getImageUrl(),
                            p.getSource()
                    ))
                    .toList();
        }

        return new DrugTranslateResponse(
                normalized,
                products,
                "현지 성분/용량은 국가별로 다를 수 있어요. 복용 전 라벨 확인 및 약사/의사 상담을 권장합니다."
        );
    }
}
