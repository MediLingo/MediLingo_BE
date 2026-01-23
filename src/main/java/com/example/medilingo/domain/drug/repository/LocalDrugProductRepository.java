package com.example.medilingo.domain.drug.repository;

import com.example.medilingo.domain.drug.LocalDrugProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalDrugProductRepository extends JpaRepository<LocalDrugProduct, Long> {
    List<LocalDrugProduct> findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
            String countryCode, String activeIngredient
    );
}
