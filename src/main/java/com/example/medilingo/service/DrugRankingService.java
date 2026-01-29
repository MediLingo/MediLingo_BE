package com.example.medilingo.service;

import com.example.medilingo.controller.drug.response.DrugCountDto;
import com.example.medilingo.controller.drug.response.WeeklyDrugRankingItem;
import com.example.medilingo.controller.drug.response.WeeklyRankingRow;
import com.example.medilingo.domain.drug.LocalDrugProduct;
import com.example.medilingo.domain.drug.repository.DrugSearchLogRepository;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrugRankingService {

    private final DrugSearchLogRepository logRepository;
    private final LocalDrugProductRepository localRepo;

    public List<WeeklyDrugRankingItem> weekly(String countryCode, int limit) {
        String country = normalizeCountry(countryCode);

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(7);

        List<WeeklyRankingRow> rows = logRepository.weeklyRankingByCountry(country, from, to);
        List<WeeklyRankingRow> topRows = rows.stream().limit(limit).toList();

        List<Long> ids = topRows.stream()
                .map(WeeklyRankingRow::localProductId)
                .toList();

        // LocalDrugProduct 엔티티가 있다고 가정 (localRepo가 JpaRepository면 findAllById 사용 가능)
        Map<Long, Object> productMap = localRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        p -> ((LocalDrugProduct) p).getId(),
                        p -> p
                ));

        List<WeeklyDrugRankingItem> result = new ArrayList<>();
        for (WeeklyRankingRow row : topRows) {
            var pObj = productMap.get(row.localProductId());
            if (pObj == null) continue; // seed 데이터 정리 전 누락 방지

            var p = (com.example.medilingo.domain.drug.LocalDrugProduct) pObj;

            result.add(new WeeklyDrugRankingItem(
                    row.localProductId(),
                    p.getLocalName(),
                    p.getImageUrl(),
                    p.getSource(),
                    row.count()
            ));
        }
        return result;
    }

    private String normalizeCountry(String cc) {
        if (cc == null) return "US";
        String s = cc.trim().toUpperCase();
        return s.isBlank() ? "US" : s;
    }

}
