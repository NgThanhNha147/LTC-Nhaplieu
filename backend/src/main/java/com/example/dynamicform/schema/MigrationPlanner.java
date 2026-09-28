package com.example.dynamicform.schema;

import com.example.dynamicform.template.domain.FieldDataType;
import com.example.dynamicform.template.domain.FieldDefinition;
import com.example.dynamicform.template.dto.TemplateDtos.MigrationChange;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MigrationPlanner {
    public Analysis analyze(List<FieldDefinition> sourceFields, List<FieldDefinition> targetFields, long sourceRecordCount) {
        return analyze(sourceFields, targetFields, sourceRecordCount, sourceRecordCount);
    }

    public Analysis analyze(List<FieldDefinition> sourceFields, List<FieldDefinition> targetFields,
                            long sourceRecordCount, long completedRecordCount) {
        Map<String, FieldDefinition> sourceByCode = sourceFields.stream()
                .collect(Collectors.toMap(field -> key(field.getFieldCode()), Function.identity()));
        Map<String, FieldDefinition> sourceByColumn = sourceFields.stream()
                .collect(Collectors.toMap(field -> key(field.getColumnName()), Function.identity()));
        Set<String> matchedSourceCodes = new HashSet<>();
        List<MigrationChange> changes = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        int added = 0;
        int modified = 0;
        int removed = 0;

        for (FieldDefinition target : targetFields) {
            FieldDefinition source = sourceByCode.get(key(target.getFieldCode()));
            if (source == null) source = sourceByColumn.get(key(target.getColumnName()));
            if (source == null) {
                added++;
                String blocker = addedFieldBlocker(target, sourceRecordCount, completedRecordCount);
                if (blocker != null) blockers.add(blocker);
                changes.add(change("ADDED", blocker == null ? "SAFE" : "BLOCKING", target, blocker == null
                        ? addedFieldMessage(target, sourceRecordCount) : blocker));
                continue;
            }
            matchedSourceCodes.add(key(source.getFieldCode()));

            String structuralBlocker = structuralBlocker(source, target);
            if (structuralBlocker != null) {
                modified++;
                blockers.add(structuralBlocker);
                changes.add(change("MODIFIED", "BLOCKING", target, structuralBlocker));
                continue;
            }

            String requiredBlocker = requiredBlocker(source, target, completedRecordCount);
            if (requiredBlocker != null) {
                modified++;
                blockers.add(requiredBlocker);
                changes.add(change("MODIFIED", "BLOCKING", target, requiredBlocker));
                continue;
            }

            if (metadataChanged(source, target)) {
                modified++;
                String message = !source.getFieldCode().equalsIgnoreCase(target.getFieldCode())
                        ? "Đổi mã kỹ thuật từ '" + source.getFieldCode() + "' sang '" + target.getFieldCode()
                        + "'; dữ liệu cũ trong cột '" + target.getColumnName() + "' được giữ nguyên."
                        : "Cập nhật cách hiển thị, điều kiện nhập hoặc tối ưu tìm kiếm; dữ liệu cũ được giữ nguyên.";
                changes.add(change("MODIFIED", "SAFE", target, message));
            }
        }

        for (FieldDefinition source : sourceFields) {
            if (!matchedSourceCodes.contains(key(source.getFieldCode()))) {
                removed++;
                String message = "Không thể xóa trường '" + source.getLabel() + "' khi đang có dữ liệu. Hãy chọn Ẩn khỏi biểu mẫu trong tab Nâng cao thay vì xóa.";
                blockers.add(message);
                changes.add(change("REMOVED", "BLOCKING", source, message));
            }
        }

        return new Analysis(blockers.isEmpty(), added, modified, removed, changes, blockers);
    }

    public boolean hasDefault(FieldDefinition field) {
        return field.getDefaultValue() != null && !field.getDefaultValue().isBlank();
    }

    public boolean validDefault(FieldDefinition field) {
        if (!hasDefault(field)) return true;
        String value = field.getDefaultValue().trim();
        try {
            switch (field.getDataType()) {
                case INTEGER -> Long.parseLong(value);
                case DECIMAL -> {
                    BigDecimal decimal = new BigDecimal(value);
                    if (field.getPrecisionValue() != null && decimal.precision() > field.getPrecisionValue()) return false;
                    if (field.getScaleValue() != null && decimal.scale() > field.getScaleValue()) return false;
                }
                case DATE -> LocalDate.parse(value);
                case DATETIME -> parseDateTime(value);
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) return false;
                }
                case UUID -> UUID.fromString(value);
                case STRING, EMAIL, PHONE -> {
                    if (field.getMaxLength() != null && value.length() > field.getMaxLength()) return false;
                }
                default -> { }
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String addedFieldBlocker(FieldDefinition field, long sourceRecordCount, long completedRecordCount) {
        if (hasDefault(field) && !validDefault(field)) {
            return "Giá trị điền sẵn của trường '" + field.getLabel() + "' không đúng định dạng " + dataTypeLabel(field.getDataType()) + ".";
        }
        if (completedRecordCount > 0 && Boolean.TRUE.equals(field.getRequired()) && !hasDefault(field)) {
            return "Trường mới '" + field.getLabel() + "' là bắt buộc nhưng chưa có giá trị điền sẵn cho " + completedRecordCount + " hồ sơ đã hoàn tất.";
        }
        if (sourceRecordCount > 1 && Boolean.TRUE.equals(field.getUniqueValue()) && hasDefault(field)) {
            return "Trường mới '" + field.getLabel() + "' không được phép trùng nhưng đang dùng cùng một giá trị điền sẵn cho nhiều hồ sơ cũ.";
        }
        return null;
    }

    private String addedFieldMessage(FieldDefinition field, long sourceRecordCount) {
        if (sourceRecordCount == 0) return "Thêm trường mới; chưa có dữ liệu cũ cần chuyển.";
        if (hasDefault(field)) return "Thêm trường mới và điền giá trị ban đầu cho " + sourceRecordCount + " hồ sơ cũ.";
        return "Thêm trường mới; " + sourceRecordCount + " hồ sơ cũ sẽ để trống trường này.";
    }

    private String structuralBlocker(FieldDefinition source, FieldDefinition target) {
        if (!source.getColumnName().equalsIgnoreCase(target.getColumnName())) {
            return "Phiên bản hiện tại chưa hỗ trợ đổi tên cột lưu trữ của trường '" + target.getLabel() + ".";
        }
        if (source.getDataType() != target.getDataType()) {
            return "Phiên bản hiện tại chưa hỗ trợ đổi loại dữ liệu của trường '" + target.getLabel() + "' từ " + dataTypeLabel(source.getDataType()) + " sang " + dataTypeLabel(target.getDataType()) + ".";
        }
        if (!Objects.equals(source.getMaxLength(), target.getMaxLength())
                || !Objects.equals(source.getPrecisionValue(), target.getPrecisionValue())
                || !Objects.equals(source.getScaleValue(), target.getScaleValue())) {
            return "Phiên bản hiện tại chưa hỗ trợ thay đổi giới hạn độ dài hoặc số chữ số của trường '" + target.getLabel() + ".";
        }
        return null;
    }

    private String requiredBlocker(FieldDefinition source, FieldDefinition target, long completedRecordCount) {
        boolean becameRequired = !Boolean.TRUE.equals(source.getRequired()) && Boolean.TRUE.equals(target.getRequired());
        if (!becameRequired || completedRecordCount == 0) return null;
        if (!hasDefault(target)) {
            return "Trường '" + target.getLabel() + "' chuyển thành bắt buộc nhưng chưa có giá trị điền sẵn cho dữ liệu cũ.";
        }
        if (!validDefault(target)) {
            return "Giá trị điền sẵn của trường '" + target.getLabel() + "' không đúng định dạng " + dataTypeLabel(target.getDataType()) + ".";
        }
        return null;
    }

    private boolean metadataChanged(FieldDefinition source, FieldDefinition target) {
        return !source.getFieldCode().equalsIgnoreCase(target.getFieldCode())
                || !Objects.equals(source.getLabel(), target.getLabel())
                || source.getComponentType() != target.getComponentType()
                || !Objects.equals(source.getRequired(), target.getRequired())
                || !Objects.equals(source.getUniqueValue(), target.getUniqueValue())
                || !Objects.equals(source.getIndexed(), target.getIndexed())
                || !Objects.equals(source.getSearchable(), target.getSearchable())
                || !Objects.equals(source.getSortable(), target.getSortable())
                || !Objects.equals(source.getExportable(), target.getExportable())
                || !Objects.equals(source.getDefaultValue(), target.getDefaultValue())
                || !Objects.equals(source.getReadOnly(), target.getReadOnly())
                || !Objects.equals(source.getHidden(), target.getHidden())
                || !Objects.equals(source.getDisplayOrder(), target.getDisplayOrder())
                || !Objects.equals(source.getGridSpan(), target.getGridSpan())
                || !Objects.equals(lookupId(source), lookupId(target));
    }

    private UUID lookupId(FieldDefinition field) {
        return field.getLookupSource() == null ? null : field.getLookupSource().getId();
    }

    private MigrationChange change(String type, String severity, FieldDefinition field, String message) {
        return new MigrationChange(type, severity, field.getFieldCode(), field.getLabel(), message);
    }

    private String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String dataTypeLabel(FieldDataType type) {
        return switch (type) {
            case STRING, TEXTAREA -> "văn bản";
            case INTEGER -> "số nguyên";
            case DECIMAL -> "số";
            case DATE -> "ngày";
            case DATETIME -> "ngày và giờ";
            case BOOLEAN -> "có/không";
            case LIST -> "danh mục lựa chọn";
            case EMAIL -> "email";
            case PHONE -> "số điện thoại";
            case UUID -> "mã định danh";
        };
    }

    private void parseDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            LocalDateTime.parse(value);
        }
    }

    public record Analysis(boolean compatible, int addedFields, int modifiedFields, int removedFields,
                           List<MigrationChange> changes, List<String> blockers) {}
}
