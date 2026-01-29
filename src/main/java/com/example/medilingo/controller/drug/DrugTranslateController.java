package com.example.medilingo.controller.drug;

import com.example.medilingo.controller.drug.request.DrugTranslateRequest;
import com.example.medilingo.controller.drug.request.SymptomDrugMappingRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.domain.member.Member;
import com.example.medilingo.security.entity.UserDetailsImpl;
import com.example.medilingo.service.DrugTranslateService;
import com.example.medilingo.service.SymptomDrugMappingService;
import com.example.medilingo.util.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drug")
public class DrugTranslateController {
    private final DrugTranslateService drugTranslateService;
    private final SymptomDrugMappingService symptomDrugMappingService;

    @PostMapping("/translate")
    public ResponseEntity<ApiResult<DrugTranslateResponse>> translate(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody DrugTranslateRequest reqDto){
        //Member member = userDetails.getUser();
        DrugTranslateResponse result = drugTranslateService.translate(reqDto);
        return ResponseEntity.ok(ApiResult.success(result));
    }

    @PostMapping("/symptom-drug-mapping")
    public ResponseEntity<ApiResult<DrugTranslateResponse>> symptomDrugMapping(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody SymptomDrugMappingRequest reqDto){
        DrugTranslateResponse result = symptomDrugMappingService.symptomDrugMapping(reqDto);
        return ResponseEntity.ok(ApiResult.success(result));
    }

}
