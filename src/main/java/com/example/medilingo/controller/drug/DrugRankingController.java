package com.example.medilingo.controller.drug;

import com.example.medilingo.controller.drug.request.DrugClickRequest;
import com.example.medilingo.controller.drug.request.DrugTranslateRequest;
import com.example.medilingo.controller.drug.response.DrugTranslateResponse;
import com.example.medilingo.controller.drug.response.WeeklyDrugRankingItem;
import com.example.medilingo.domain.member.Member;
import com.example.medilingo.security.entity.UserDetailsImpl;
import com.example.medilingo.service.DrugClickService;
import com.example.medilingo.service.DrugRankingService;
import com.example.medilingo.service.DrugTranslateService;
import com.example.medilingo.util.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drugs")
public class DrugRankingController {

    private final DrugClickService drugClickService;
    private final DrugRankingService drugRankingService;

    @PostMapping("/{id}/click")
    public ResponseEntity<ApiResult<Void>> click(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable("id") Long localProductId,
            @RequestBody(required = false) DrugClickRequest reqDto
    ) {

        String countryCode = (reqDto == null) ? null : reqDto.countryCode();

        drugClickService.click(localProductId, countryCode);

        return ResponseEntity.ok(ApiResult.success(null));
    }

    @GetMapping("/weekly")
    public ResponseEntity<ApiResult<List<WeeklyDrugRankingItem>>> weeklyRanking(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam String country,
            @RequestParam(defaultValue = "10") int limit
    ) {

        List<WeeklyDrugRankingItem> result = drugRankingService.weekly(country, limit);

        return ResponseEntity.ok(ApiResult.success(result));
    }
}
