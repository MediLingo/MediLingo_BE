package com.example.medilingo.service;

import com.example.medilingo.controller.drug.response.NormalizedDrug;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DrugFallbackMappingService {

    /**
     * 한국 약 브랜드명 → 대표 성분 매핑
     * LinkedHashMap: 위에서 먼저 매칭된 것이 우선
     */
    private static final Map<Pattern, String> BRAND_TO_INGREDIENT = new LinkedHashMap<>();

    static {
        // ===== 진통/해열 =====
        put("타이레놀|tylenol", "acetaminophen");
        put("어린이\\s*타이레놀|키즈\\s*타이레놀", "acetaminophen");
        put("펜잘|penzal", "acetaminophen");       // 복합제 가능
        put("게보린|geboryn", "acetaminophen");   // 복합제 가능
        put("이지엔\\s*6|이지엔6|ezen\\s*6|ezen6", "ibuprofen");
        put("부루펜|brufen", "ibuprofen");
        put("애드빌|advil", "ibuprofen");
        put("알레브|aleve", "naproxen");

        // ===== 감기/기침 (복합제 多) =====
        put("테라플루|theraflu", "acetaminophen");
        put("타이레놀\\s*콜드|타이레놀\\s*감기", "acetaminophen");
        put("판콜\\s*A|판콜", "acetaminophen");
        put("콜대원|콜대원\\s*시럽", "acetaminophen");
        put("코푸\\s*시럽|코푸", "dextromethorphan");

        // ===== 알레르기 =====
        put("지르텍|zyrtec", "cetirizine");
        put("클라리틴|claritin", "loratadine");
        put("알레그라|allegra", "fexofenadine");
        put("데스로라타딘|desloratadine", "desloratadine");

        // ===== 위장/소화 =====
        put("겔포스|gelpos", "aluminum_hydroxide");
        put("알마겔|almagel", "aluminum_hydroxide");
        put("개비스콘|gaviscon", "alginate");
        put("훼스탈|festal", "pancreatin");
        put("베아제|bea?ze", "pancreatin");
        put("까스활명수|활명수", "simethicone");
        put("가스모틴|gasmotin", "mosapride");
        put("오메프라졸|omeprazole", "omeprazole");
        put("파모티딘|famotidine", "famotidine");

        // ===== 설사/변비/멀미 =====
        put("정로환|정로환\\s*당의정", "loperamide");
        put("로페라미드|loperamide", "loperamide");
        put("듀파락|dup(h)?alac", "lactulose");
        put("둘코락스|dulcolax", "bisacodyl");
        put("드라마민|dramamine", "dimenhydrinate");
        put("메클리진|meclizine", "meclizine");

        // ===== 피부/연고 =====
        put("후시딘|fucidin", "fusidic_acid");
        put("마데카솔|madecassol", "centella_asiatica");
        put("비판텐|bepanthen", "dexpanthenol");
        put("클로트리마졸|clotrimazole", "clotrimazole");

        // ===== 기타 =====
        put("나잘\\s*스프레이|오트리빈|otrivin", "xylometazoline");
        put("아스피린|aspirin", "aspirin");
    }

    private static void put(String regex, String ingredient) {
        BRAND_TO_INGREDIENT.put(
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                ingredient
        );
    }

    /**
     * LLM이 성분을 못 줬을 때만 호출
     */
    public NormalizedDrug fallbackNormalize(String inputText, NormalizedDrug base) {
        if (inputText == null || inputText.isBlank()) {
            return base;
        }

        for (Map.Entry<Pattern, String> entry : BRAND_TO_INGREDIENT.entrySet()) {
            if (entry.getKey().matcher(inputText).find()) {
                String ingredient = entry.getValue();

                String note = mergeNotes(
                        base.notes(),
                        "일반적으로 알려진 브랜드-성분 매핑을 사용했어요. 라벨 확인을 권장해요."
                );

                // 복합제 가능성 경고
                if (looksLikeComboProduct(inputText)) {
                    note = mergeNotes(note, "복합 성분 제품일 수 있어요.");
                }

                return new NormalizedDrug(
                        ingredient,
                        base.dose(),
                        base.form(),
                        note
                );
            }
        }

        // 매칭 실패 → 그대로 반환
        return base;
    }

    private boolean looksLikeComboProduct(String text) {
        return text.matches(".*(종합|콜드|플러스|콤비|데이|나이트|감기).*");
    }

    private String mergeNotes(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " " + b;
    }
}
