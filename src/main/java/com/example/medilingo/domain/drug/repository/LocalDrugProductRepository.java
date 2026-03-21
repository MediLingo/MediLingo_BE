package com.example.medilingo.domain.drug.repository;

import com.example.medilingo.domain.drug.LocalDrugProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalDrugProductRepository extends JpaRepository<LocalDrugProduct, Long> {
    List<LocalDrugProduct> findTop10ByCountryCodeAndActiveIngredientOrderByIdDesc(
            String countryCode, String activeIngredient
    );
    Optional<LocalDrugProduct> findByCountryCodeAndLocalName(String countryCode, String localName);
}
