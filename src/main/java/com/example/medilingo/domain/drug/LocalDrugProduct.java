package com.example.medilingo.domain.drug;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "local_drug_product",
        indexes = {@Index(name="idx_country_ing", columnList="countryCode,activeIngredient")})
public class LocalDrugProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String countryCode;       // e.g., "JP"
    private String activeIngredient;  // primary ingredient — kept for existing queries.  e.g., "acetaminophen"

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "local_drug_product_ingredients",
        joinColumns = @JoinColumn(name = "drug_product_id")
    )
    @Column(name = "ingredient")
    private List<String> allIngredients = new ArrayList<>();  // full ingredient list


    private String localName;
    private String imageUrl;
    private String source;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected LocalDrugProduct() {}

    public LocalDrugProduct(String countryCode, String activeIngredient,
        List<String> allIngredients, String localName,
        String imageUrl, String source) {
        this.countryCode = countryCode;
        this.activeIngredient = activeIngredient;
        this.allIngredients = allIngredients != null ? allIngredients : new ArrayList<>();
        this.localName = localName;
        this.imageUrl = imageUrl;
        this.source = source;
    }
}
