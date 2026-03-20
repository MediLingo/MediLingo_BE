package com.example.medilingo.controller.drug;

import com.example.medilingo.controller.drug.response.DrugExplainResponse;
import com.example.medilingo.service.DrugExplainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drug")
@RequiredArgsConstructor
public class DrugExplainController {

	private final DrugExplainService drugExplainService;

	@GetMapping("/explain")
	public ResponseEntity<DrugExplainResponse> explain(
		@RequestParam String ingredient,
		@RequestParam(required = false) String form,
		@RequestParam(defaultValue = "Korean") String lang
	) {
		String explanation = drugExplainService.explain(ingredient, form, lang);

		if (explanation == null) {
			return ResponseEntity.ok(
				new DrugExplainResponse(null, "설명을 불러올 수 없어요.")
			);
		}

		return ResponseEntity.ok(new DrugExplainResponse(explanation, null));
	}
}