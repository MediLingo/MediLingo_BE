package com.example.medilingo.service;

import com.example.medilingo.domain.drug.DrugSearchLog;
import com.example.medilingo.domain.drug.repository.DrugSearchLogRepository;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DrugClickService {
    private final DrugSearchLogRepository logRepository;
    private final LocalDrugProductRepository localRepo;

    @Transactional
    public void click(Long localProductId, String countryCode) {
        // 존재 검증 (없는 id로 로그 쌓이는 것 방지)
        localRepo.findById(localProductId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 localProductId 입니다: " + localProductId));

        String country = normalizeCountry(countryCode);

        logRepository.save(DrugSearchLog.create(country, localProductId));
    }

    private String normalizeCountry(String cc) {
        if (cc == null) return "US";
        String s = cc.trim().toUpperCase();
        return s.isBlank() ? "US" : s;
    }
}
