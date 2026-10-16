package com.example.dynamicform.documentanalysis;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSemanticAnalyzerTest {
    private final DocumentSemanticAnalyzer analyzer = new DocumentSemanticAnalyzer();

    @Test
    void infersFieldsThatAreEmbeddedInVietnameseSentences() {
        String text = """
                GIẤY CHỨNG NHẬN QUYỀN SỬ DỤNG ĐẤT
                Ông Nguyễn Văn An, sinh năm 1980, CCCD số 012345678999 cấp ngày 15-06-2021.
                Thửa đất số: 125
                Tờ bản đồ số: 08
                Diện tích: 152,5 m²
                Số vào sổ: CS01234
                """;

        var result = analyzer.analyzeText(text);
        Map<String, String> values = result.form().groups().stream()
                .flatMap(group -> group.fields().stream())
                .collect(Collectors.toMap(field -> field.fieldCode(), field -> field.value(), (left, right) -> left));

        assertThat(result.documentType()).isEqualTo("Giấy chứng nhận quyền sử dụng đất");
        assertThat(values).containsEntry("ho_ten_chu_su_dung", "Nguyễn Văn An")
                .containsEntry("ngay_sinh", "1980")
                .containsEntry("so_giay_to", "012345678999")
                .containsEntry("so_thua", "125")
                .containsEntry("to_ban_do", "08")
                .containsEntry("dien_tich", "152,5")
                .containsEntry("so_vao_so", "CS01234");
    }
}
