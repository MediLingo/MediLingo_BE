package com.example.medilingo.controller.drug;

import com.example.medilingo.controller.drug.request.DrugTranslateRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.LocalProductDto;
import com.example.medilingo.service.DrugTranslateService;
import com.example.medilingo.util.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drug")
public class DrugTranslateController {
    private final DrugTranslateService drugTranslateService;

    @PostMapping("/translate")
    public ResponseEntity<ApiResult<DrugTranslateResponse>> translate(@RequestBody DrugTranslateRequest reqDto){
        DrugTranslateResponse result = drugTranslateService.translate(reqDto);
        return ResponseEntity.ok(ApiResult.success(result));
    }

}
