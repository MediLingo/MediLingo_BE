package com.example.medilingo.domain.drug;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "local_drug_product",
        indexes = {@Index(name="idx_country_ing", columnList="countryCode,activeIngredient")})
public class LocalDrugProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String countryCode;       // e.g., "JP"
    private String activeIngredient;  // e.g., "acetaminophen"

    private String localName;
    private String imageUrl;
    private String source;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected LocalDrugProduct() {}
}
