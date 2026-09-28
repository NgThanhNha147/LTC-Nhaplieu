package com.example.dynamicform.lookup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class LookupDtos {
    private LookupDtos() {}

    public record LookupRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,99}$") String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank String sourceType,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String sourceSchema,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String sourceTable,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String valueColumn,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String labelColumn,
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String activeColumn,
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String parentColumn,
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$") String sortColumn,
            String status) {}

    public record LookupResponse(UUID id, String code, String name, String sourceType,
                                 String sourceSchema, String sourceTable, String valueColumn,
                                 String labelColumn, String activeColumn, String parentColumn,
                                 String sortColumn, String status) {}
    public record Option(String value, String label) {}
    public record OptionPage(List<Option> items, boolean hasMore) {}
}
