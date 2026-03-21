package com.example.medilingo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.medilingo.domain.drug.LocalDrugProduct;
import com.example.medilingo.domain.drug.repository.LocalDrugProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocalProductUpsertService {

	private final LocalDrugProductRepository repo;

	@Transactional
	public LocalDrugProduct upsert(String countryCode, String activeIngredient,
		List<String> allIngredients, String localName,
		String imageUrl, String source) {
		return repo.findByCountryCodeAndLocalName(countryCode, localName)
			.orElseGet(() -> repo.save(new LocalDrugProduct(
				countryCode, activeIngredient, allIngredients, localName, imageUrl, source)));
	}
}