package com.example.dynamicform.documentanalysis;

import com.example.dynamicform.documentanalysis.DocumentAnalysisDtos.SuggestedField;
import com.example.dynamicform.documentanalysis.DocumentAnalysisDtos.SuggestedForm;
import com.example.dynamicform.documentanalysis.DocumentAnalysisDtos.SuggestedGroup;
import com.example.dynamicform.documentanalysis.PdfDocumentExtractor.AcroField;
import com.example.dynamicform.documentanalysis.PdfDocumentExtractor.PageText;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentSemanticAnalyzer {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern LABEL_VALUE = Pattern.compile("^\\s*([\\p{L}\\d][\\p{L}\\d .,/()%-]{1,70})\\s*[:：]\\s*(.+?)\\s*$", FLAGS);
    private static final Pattern OWNER = Pattern.compile("(?:^|\\s)(?:ông|bà)\\s+([\\p{L}][\\p{L} .'-]{2,80}?)(?=,|;|\\s+sinh(?:\\s|$))", FLAGS);
    private static final Pattern BIRTH = Pattern.compile("\\bsinh\\s+(?:ngày|năm)\\s*[:：]?\\s*(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}|\\d{4})", FLAGS);
    private static final Pattern IDENTITY = Pattern.compile("\\b(CCCD|CMND|căn cước(?: công dân)?|hộ chiếu)\\s*(?:số)?\\s*[:：]?\\s*([A-Z0-9]{6,20})", FLAGS);
    private static final Pattern ISSUE_DATE = Pattern.compile("\\bcấp\\s+ngày\\s*[:：]?\\s*(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})", FLAGS);
    private static final Pattern LAND_PARCEL = Pattern.compile("\\bthửa(?: đất)?\\s*(?:số)?\\s*[:：]?\\s*([A-Z0-9./-]+)", FLAGS);
    private static final Pattern MAP_SHEET = Pattern.compile("\\btờ\\s+bản\\s+đồ\\s*(?:số)?\\s*[:：]?\\s*([A-Z0-9./-]+)", FLAGS);
    private static final Pattern AREA = Pattern.compile("\\bdiện\\s+tích\\s*[:：]?\\s*([0-9.,]+)\\s*(m2|m²)?", FLAGS);
    private static final Pattern SERIAL = Pattern.compile("\\b(?:số\\s+seri|seri)\\s*[:：]?\\s*([A-Z0-9 -]{4,30})", FLAGS);
    private static final Pattern REGISTER_NO = Pattern.compile("\\bsố\\s+vào\\s+sổ\\s*[:：]?\\s*([A-Z0-9./-]{2,40})", FLAGS);
    private static final Pattern AMBIGUOUS_CODE = Pattern.compile("^[A-ZĐ]{1,5}\\s*[0-9]{5,14}$");

    public AnalysisResult analyze(PdfDocumentExtractor.ExtractedDocument document) {
        LinkedHashMap<String, GroupBucket> groups = new LinkedHashMap<>();
        Map<String, SuggestedField> fieldsByCode = new LinkedHashMap<>();

        for (AcroField field : document.acroFields()) {
            FieldMeaning meaning = inferMeaning(field.label(), field.value());
            add(groups, fieldsByCode, meaning.groupCode(), meaning.groupLabel(), meaning.code(), field.label(),
                    meaning.dataType(), componentForAcro(field.fieldType(), meaning.componentType()), field.value(),
                    decimal("0.99"), field.page(), "Trường biểu mẫu có sẵn trong PDF");
        }

        List<PageText> pages = document.pages().isEmpty() ? List.of(new PageText(1, document.text())) : document.pages();
        for (PageText page : pages) analyzePage(page, groups, fieldsByCode);

        if (groups.isEmpty()) {
            GroupBucket group = groups.computeIfAbsent("THONG_TIN_CAN_KIEM_TRA",
                    key -> new GroupBucket(key, "Thông tin cần kiểm tra", groups.size()));
            group.fields.add(new SuggestedField(UUID.randomUUID().toString(), "noi_dung_tai_lieu",
                    "Nội dung tài liệu", "TEXTAREA", "TEXTAREA", document.text(), decimal("0.30"),
                    1, "Chưa đủ căn cứ để tách thành các trường riêng", false, true));
        }

        List<SuggestedGroup> resultGroups = groups.values().stream()
                .filter(group -> !group.fields.isEmpty())
                .map(group -> new SuggestedGroup(group.code, group.label, group.order, List.copyOf(group.fields)))
                .toList();
        BigDecimal confidence = resultGroups.stream().flatMap(group -> group.fields().stream())
                .map(SuggestedField::confidence).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, resultGroups.stream().mapToInt(group -> group.fields().size()).sum())), 4, RoundingMode.HALF_UP);
        return new AnalysisResult(detectDocumentType(document.text()), confidence, new SuggestedForm(resultGroups));
    }

    AnalysisResult analyzeText(String text) {
        return analyze(new PdfDocumentExtractor.ExtractedDocument(DocumentSourceKind.DIGITAL_TEXT, text,
                List.of(new PageText(1, text)), List.of()));
    }

    private void analyzePage(PageText page, LinkedHashMap<String, GroupBucket> groups,
                             Map<String, SuggestedField> fieldsByCode) {
        String currentHeading = "Thông tin chung";
        String[] lines = page.text().split("\\R");
        int unknownCounter = 1;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) continue;
            if (isHeading(line)) {
                currentHeading = cleanHeading(line);
                continue;
            }
            Matcher pair = LABEL_VALUE.matcher(line);
            if (pair.matches()) {
                String label = pair.group(1).trim();
                String value = pair.group(2).trim();
                FieldMeaning meaning = inferMeaning(label, value);
                String groupCode = "THONG_TIN_CHUNG".equals(meaning.groupCode()) ? code(currentHeading).toUpperCase(Locale.ROOT) : meaning.groupCode();
                String groupLabel = "THONG_TIN_CHUNG".equals(meaning.groupCode()) ? currentHeading : meaning.groupLabel();
                add(groups, fieldsByCode, groupCode, groupLabel, meaning.code(), label, meaning.dataType(),
                        meaning.componentType(), value, decimal("0.84"), page.page(), line);
            }
            if (AMBIGUOUS_CODE.matcher(line.replaceAll("\\s+", " ")).matches()) {
                String fieldCode = "thong_tin_chua_xac_dinh_" + unknownCounter++;
                add(groups, fieldsByCode, "THONG_TIN_CAN_KIEM_TRA", "Thông tin cần kiểm tra", fieldCode,
                        "Thông tin chưa xác định", "STRING", "TEXT", line, decimal("0.35"), page.page(),
                        "Giá trị không có nhãn rõ ràng; cần người dùng xác nhận");
            }
        }

        addPattern(page, OWNER, "ho_ten_chu_su_dung", "Họ tên chủ sử dụng", "STRING", "TEXT",
                "CHU_SU_DUNG", "Chủ sử dụng", decimal("0.88"), groups, fieldsByCode, 1);
        addPattern(page, BIRTH, "ngay_sinh", "Ngày hoặc năm sinh", "STRING", "TEXT",
                "CHU_SU_DUNG", "Chủ sử dụng", decimal("0.86"), groups, fieldsByCode, 1);
        addIdentity(page, groups, fieldsByCode);
        addPattern(page, ISSUE_DATE, "ngay_cap_giay_to", "Ngày cấp giấy tờ", "DATE", "DATE",
                "CHU_SU_DUNG", "Chủ sử dụng", decimal("0.86"), groups, fieldsByCode, 1);
        addPattern(page, LAND_PARCEL, "so_thua", "Số thửa", "STRING", "TEXT",
                "THUA_DAT", "Thửa đất", decimal("0.93"), groups, fieldsByCode, 1);
        addPattern(page, MAP_SHEET, "to_ban_do", "Tờ bản đồ", "STRING", "TEXT",
                "THUA_DAT", "Thửa đất", decimal("0.93"), groups, fieldsByCode, 1);
        addPattern(page, AREA, "dien_tich", "Diện tích", "DECIMAL", "NUMBER",
                "THUA_DAT", "Thửa đất", decimal("0.93"), groups, fieldsByCode, 1);
        addPattern(page, SERIAL, "so_seri", "Số seri", "STRING", "TEXT",
                "THONG_TIN_PHAT_HANH", "Thông tin phát hành", decimal("0.92"), groups, fieldsByCode, 1);
        addPattern(page, REGISTER_NO, "so_vao_so", "Số vào sổ", "STRING", "TEXT",
                "CAP_GIAY_CHUNG_NHAN", "Cấp giấy chứng nhận", decimal("0.92"), groups, fieldsByCode, 1);
    }

    private void addIdentity(PageText page, LinkedHashMap<String, GroupBucket> groups,
                             Map<String, SuggestedField> fieldsByCode) {
        Matcher matcher = IDENTITY.matcher(page.text());
        if (!matcher.find()) return;
        add(groups, fieldsByCode, "CHU_SU_DUNG", "Chủ sử dụng", "loai_giay_to", "Loại giấy tờ",
                "STRING", "TEXT", matcher.group(1).trim(), decimal("0.91"), page.page(), matcher.group(0));
        add(groups, fieldsByCode, "CHU_SU_DUNG", "Chủ sử dụng", "so_giay_to", "Số giấy tờ",
                "STRING", "TEXT", matcher.group(2).trim(), decimal("0.94"), page.page(), matcher.group(0));
    }

    private void addPattern(PageText page, Pattern pattern, String fieldCode, String label,
                            String dataType, String componentType, String groupCode, String groupLabel,
                            BigDecimal confidence, LinkedHashMap<String, GroupBucket> groups,
                            Map<String, SuggestedField> fieldsByCode, int valueGroup) {
        Matcher matcher = pattern.matcher(page.text());
        if (!matcher.find()) return;
        add(groups, fieldsByCode, groupCode, groupLabel, fieldCode, label, dataType, componentType,
                matcher.group(valueGroup).trim(), confidence, page.page(), matcher.group(0));
    }

    private void add(LinkedHashMap<String, GroupBucket> groups, Map<String, SuggestedField> fieldsByCode,
                     String groupCode, String groupLabel, String fieldCode, String label, String dataType,
                     String componentType, String value, BigDecimal confidence, int page, String evidence) {
        String safeCode = uniqueCode(code(fieldCode), fieldsByCode, fieldCode);
        SuggestedField existing = fieldsByCode.get(code(fieldCode));
        if (existing != null) {
            if (existing.confidence() != null && existing.confidence().compareTo(confidence) >= 0) return;
            groups.values().forEach(group -> group.fields.removeIf(field -> field.fieldCode().equals(existing.fieldCode())));
            safeCode = existing.fieldCode();
        }
        GroupBucket bucket = groups.computeIfAbsent(safeGroupCode(groupCode),
                key -> new GroupBucket(key, groupLabel == null || groupLabel.isBlank() ? "Thông tin chung" : groupLabel, groups.size()));
        SuggestedField field = new SuggestedField(UUID.randomUUID().toString(), safeCode,
                label == null || label.isBlank() ? "Thông tin chưa xác định" : label.trim(), dataType, componentType,
                cleanValue(value), confidence, Math.max(1, page), trim(evidence, 1900), false, true);
        bucket.fields.add(field);
        fieldsByCode.put(safeCode, field);
    }

    private String uniqueCode(String preferred, Map<String, SuggestedField> fields, String source) {
        if (!fields.containsKey(preferred)) return preferred;
        if (Objects.equals(fields.get(preferred).label(), source)) return preferred;
        int suffix = 2;
        while (fields.containsKey(preferred + "_" + suffix)) suffix++;
        return preferred + "_" + suffix;
    }

    private FieldMeaning inferMeaning(String label, String value) {
        String normalized = normalize(label);
        if (normalized.contains("ho ten") || normalized.equals("ho va ten")) return meaning("ho_ten", "STRING", "TEXT", "CHU_SU_DUNG", "Chủ sử dụng");
        if (normalized.contains("ngay sinh") || normalized.contains("nam sinh")) return meaning("ngay_sinh", dateLike(value) ? "DATE" : "STRING", dateLike(value) ? "DATE" : "TEXT", "CHU_SU_DUNG", "Chủ sử dụng");
        if (normalized.contains("so giay to") || normalized.contains("cccd") || normalized.contains("cmnd")) return meaning("so_giay_to", "STRING", "TEXT", "CHU_SU_DUNG", "Chủ sử dụng");
        if (normalized.contains("loai giay to")) return meaning("loai_giay_to", "STRING", "TEXT", "CHU_SU_DUNG", "Chủ sử dụng");
        if (normalized.contains("ngay cap") && !normalized.contains("gcn")) return meaning("ngay_cap_giay_to", "DATE", "DATE", "CHU_SU_DUNG", "Chủ sử dụng");
        if (normalized.contains("noi cap")) return meaning("noi_cap_giay_to", "STRING", "TEXT", "CHU_SU_DUNG", "Chủ sử dụng");
        if (normalized.contains("dia chi")) return meaning("dia_chi", "TEXTAREA", "TEXTAREA", "DIA_CHI", "Địa chỉ");
        if (normalized.contains("so thua") || normalized.contains("thua dat so")) return meaning("so_thua", "STRING", "TEXT", "THUA_DAT", "Thửa đất");
        if (normalized.contains("to ban do")) return meaning("to_ban_do", "STRING", "TEXT", "THUA_DAT", "Thửa đất");
        if (normalized.contains("dien tich")) return meaning("dien_tich", "DECIMAL", "NUMBER", "THUA_DAT", "Thửa đất");
        if (normalized.contains("loai dat") || normalized.contains("muc dich su dung")) return meaning("loai_dat", "STRING", "TEXT", "THUA_DAT", "Thửa đất");
        if (normalized.contains("hinh thuc su dung")) return meaning("hinh_thuc_su_dung", "STRING", "TEXT", "THUA_DAT", "Thửa đất");
        if (normalized.contains("thoi han su dung")) return meaning("thoi_han_su_dung", "STRING", "TEXT", "THUA_DAT", "Thửa đất");
        if (normalized.contains("nguon goc su dung")) return meaning("nguon_goc_su_dung", "TEXTAREA", "TEXTAREA", "THUA_DAT", "Thửa đất");
        if (normalized.contains("so vao so")) return meaning("so_vao_so", "STRING", "TEXT", "CAP_GIAY_CHUNG_NHAN", "Cấp giấy chứng nhận");
        if (normalized.contains("ngay cap gcn") || normalized.contains("ngay cap giay chung nhan")) return meaning("ngay_cap_gcn", "DATE", "DATE", "CAP_GIAY_CHUNG_NHAN", "Cấp giấy chứng nhận");
        if (normalized.contains("nguoi ky")) return meaning("nguoi_ky", "STRING", "TEXT", "CAP_GIAY_CHUNG_NHAN", "Cấp giấy chứng nhận");
        if (normalized.contains("seri")) return meaning("so_seri", "STRING", "TEXT", "THONG_TIN_PHAT_HANH", "Thông tin phát hành");
        if (normalized.contains("ma vach")) return meaning("ma_vach", "STRING", "TEXT", "THONG_TIN_PHAT_HANH", "Thông tin phát hành");
        if (normalized.startsWith("ngay") && dateLike(value)) return meaning(code(label), "DATE", "DATE", "THONG_TIN_CHUNG", "Thông tin chung");
        return meaning(code(label), inferDataType(value), inferComponent(value), "THONG_TIN_CHUNG", "Thông tin chung");
    }

    private FieldMeaning meaning(String code, String dataType, String componentType, String groupCode, String groupLabel) {
        return new FieldMeaning(code(code), dataType, componentType, groupCode, groupLabel);
    }

    private String componentForAcro(String fieldType, String fallback) {
        if ("Btn".equals(fieldType)) return "CHECKBOX";
        if ("Ch".equals(fieldType)) return "SELECT";
        return fallback;
    }

    private String inferDataType(String value) {
        if (dateLike(value)) return "DATE";
        if (value != null && value.matches("[-+]?[0-9]+([.,][0-9]+)?")) return value.matches("[-+]?[0-9]+") ? "INTEGER" : "DECIMAL";
        if (value != null && value.length() > 180) return "TEXTAREA";
        return "STRING";
    }

    private String inferComponent(String value) {
        return switch (inferDataType(value)) {
            case "DATE" -> "DATE";
            case "INTEGER", "DECIMAL" -> "NUMBER";
            case "TEXTAREA" -> "TEXTAREA";
            default -> "TEXT";
        };
    }

    private boolean dateLike(String value) {
        return value != null && value.trim().matches("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}");
    }

    private boolean isHeading(String line) {
        if (line.length() < 3 || line.length() > 90 || line.contains(":")) return false;
        long letters = line.chars().filter(Character::isLetter).count();
        long upper = line.chars().filter(Character::isUpperCase).count();
        return letters >= 3 && upper >= Math.ceil(letters * 0.72);
    }

    private String cleanHeading(String line) {
        return line.replaceFirst("^[IVXLC0-9.\\-() ]+", "").trim();
    }

    private String detectDocumentType(String text) {
        String normalized = normalize(text);
        if (normalized.contains("giay chung nhan quyen su dung dat")) return "Giấy chứng nhận quyền sử dụng đất";
        if (normalized.contains("hop dong chuyen nhuong")) return "Hợp đồng chuyển nhượng";
        if (normalized.contains("don dang ky") && normalized.contains("dat dai")) return "Đơn đăng ký đất đai";
        if (normalized.contains("bien ban")) return "Biên bản";
        if (normalized.contains("quyet dinh")) return "Quyết định";
        return "Tài liệu chưa xác định";
    }

    private String code(String value) {
        String normalized = normalize(value).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) normalized = "truong_du_lieu";
        if (!Character.isLetter(normalized.charAt(0))) normalized = "field_" + normalized;
        return trim(normalized, 60);
    }

    private String safeGroupCode(String value) {
        String result = code(value).toUpperCase(Locale.ROOT);
        return result.length() < 2 ? "THONG_TIN_CHUNG" : result;
    }

    private String normalize(String value) {
        if (value == null) return "";
        String replaced = value.replace('đ', 'd').replace('Đ', 'D');
        return Normalizer.normalize(replaced, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String cleanValue(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String trim(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value); }

    public record AnalysisResult(String documentType, BigDecimal confidence, SuggestedForm form) {}
    private record FieldMeaning(String code, String dataType, String componentType, String groupCode, String groupLabel) {}
    private static final class GroupBucket {
        private final String code;
        private final String label;
        private final int order;
        private final List<SuggestedField> fields = new ArrayList<>();
        private GroupBucket(String code, String label, int order) { this.code = code; this.label = label; this.order = order; }
    }
}
