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
    private final SymptomDrugGeminiService geminiService;
    private final UsOpenFdaNdcService usOpenFdaNdcService;
    private final BingImageSearchService bingImageSearchService;

    public DrugTranslateResponse symptomDrugMapping(SymptomDrugMappingRequest req){

        //1st gemini call
        NormalizedDrug normalized = geminiService.recommendFromSymptoms(req);

        if (normalized.activeIngredient() == null){
            return new DrugTranslateResponse(
                    normalized,
                    List.of(),
                    disclaimer()
            );
        }
        // 2) DB/API lookup: ingredient -> products (no LLM)
        List<LocalProductDto> products = switch (req.countryCode().toUpperCase()) {
            case "US" -> usOpenFdaNdcService.findTopProductsByIngredient(normalized.activeIngredient(), 20);
            default -> List.of(); // JP/KR: later (seed table or other API)
        };

//        List<LocalProductDto> withImages = products.stream()
//                .limit(3)
//                .map(p -> new LocalProductDto(
//                        p.name(),
//                        bingImageSearchService.findImageUrl(p.name() + " product"),
//                        "bing"
//                ))
//                .toList();


        return new DrugTranslateResponse(normalized, products, disclaimer());
    }

    private String disclaimer() {
        return "의학적 진단이 아니며, 일반적인 정보 제공 목적입니다. 복용 전 약사/의사 상담을 권장합니다.";
    }
}
