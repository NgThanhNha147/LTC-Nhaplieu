package com.example.dynamicform.schema;

import com.example.dynamicform.template.domain.ComponentType;
import com.example.dynamicform.template.domain.FieldDataType;
import com.example.dynamicform.template.domain.FieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationPlannerTest {
    private final MigrationPlanner planner = new MigrationPlanner();

    @Test
    void allowsOptionalFieldAndPreservesExistingRows() {
        FieldDefinition existing = field("ownerName", "owner_name", FieldDataType.STRING);
        FieldDefinition added = field("ownerPhone", "owner_phone", FieldDataType.STRING);

        var result = planner.analyze(List.of(existing), List.of(existing, added), 12);

        assertThat(result.compatible()).isTrue();
        assertThat(result.addedFields()).isEqualTo(1);
        assertThat(result.changes()).anyMatch(change -> change.fieldCode().equals("ownerPhone") && change.severity().equals("SAFE"));
    }

    @Test
    void blocksRequiredFieldWithoutDefaultWhenOldRowsExist() {
        FieldDefinition existing = field("ownerName", "owner_name", FieldDataType.STRING);
        FieldDefinition added = field("ownerPhone", "owner_phone", FieldDataType.STRING);
        added.setRequired(true);

        var result = planner.analyze(List.of(existing), List.of(existing, added), 3);

        assertThat(result.compatible()).isFalse();
        assertThat(result.blockers()).anyMatch(message -> message.contains("chưa có giá trị điền sẵn"));
    }

    @Test
    void allowsRequiredFieldWithValidDefault() {
        FieldDefinition existing = field("ownerName", "owner_name", FieldDataType.STRING);
        FieldDefinition added = field("verified", "verified", FieldDataType.BOOLEAN);
        added.setRequired(true);
        added.setDefaultValue("false");

        var result = planner.analyze(List.of(existing), List.of(existing, added), 5);

        assertThat(result.compatible()).isTrue();
    }

    @Test
    void blocksRemovingOrChangingPhysicalField() {
        FieldDefinition sourceName = field("ownerName", "owner_name", FieldDataType.STRING);
        FieldDefinition changedName = field("ownerName", "owner_name", FieldDataType.INTEGER);

        var changed = planner.analyze(List.of(sourceName), List.of(changedName), 2);
        var removed = planner.analyze(List.of(sourceName), List.of(), 2);

        assertThat(changed.compatible()).isFalse();
        assertThat(changed.blockers()).anyMatch(message -> message.contains("đổi loại dữ liệu"));
        assertThat(removed.compatible()).isFalse();
        assertThat(removed.removedFields()).isEqualTo(1);
    }

    @Test
    void treatsDisplayChangesAsSafe() {
        FieldDefinition source = field("ownerName", "owner_name", FieldDataType.STRING);
        FieldDefinition target = field("ownerName", "owner_name", FieldDataType.STRING);
        target.setLabel("Tên người sử dụng đất");

        var result = planner.analyze(List.of(source), List.of(target), 9);

        assertThat(result.compatible()).isTrue();
        assertThat(result.modifiedFields()).isEqualTo(1);
    }

    @Test
    void treatsFieldCodeRenameWithSameStorageColumnAsSafe() {
        FieldDefinition source = field("serialNumber", "serial_number", FieldDataType.STRING);
        source.setRequired(true);
        FieldDefinition target = field("soSeria", "serial_number", FieldDataType.STRING);
        target.setRequired(true);

        var result = planner.analyze(List.of(source), List.of(target), 13, 10);

        assertThat(result.compatible()).isTrue();
        assertThat(result.addedFields()).isZero();
        assertThat(result.removedFields()).isZero();
        assertThat(result.modifiedFields()).isEqualTo(1);
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.changeType()).isEqualTo("MODIFIED");
            assertThat(change.message()).contains("serialNumber", "soSeria", "serial_number");
        });
    }

    private FieldDefinition field(String code, String column, FieldDataType type) {
        FieldDefinition field = new FieldDefinition();
        field.setFieldCode(code);
        field.setColumnName(column);
        field.setLabel(code);
        field.setDataType(type);
        field.setComponentType(type == FieldDataType.INTEGER ? ComponentType.NUMBER : type == FieldDataType.BOOLEAN ? ComponentType.CHECKBOX : ComponentType.TEXT);
        field.setRequired(false);
        field.setUniqueValue(false);
        field.setIndexed(false);
        field.setSearchable(true);
        field.setSortable(true);
        field.setExportable(true);
        field.setMaxLength(type == FieldDataType.STRING ? 255 : null);
        field.setDisplayOrder(1);
        field.setGridSpan(6);
        field.setReadOnly(false);
        field.setHidden(false);
        return field;
    }
}
