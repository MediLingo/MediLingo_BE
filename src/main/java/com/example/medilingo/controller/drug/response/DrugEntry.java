package com.example.medilingo.controller.drug.response;

import java.util.List;

public record DrugEntry(
	String brandName,
	String splSetId,
	String displayName
) {}