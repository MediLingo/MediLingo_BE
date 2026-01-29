package com.example.medilingo.domain.drug;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class DrugSearchLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private Long localProductId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static DrugSearchLog create(String countryCode, Long localProductId) {
        DrugSearchLog log = new DrugSearchLog();
        log.countryCode = countryCode;
        log.localProductId = localProductId;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
