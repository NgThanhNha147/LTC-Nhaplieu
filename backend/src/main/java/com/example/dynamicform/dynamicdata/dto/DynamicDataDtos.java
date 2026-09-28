package com.example.dynamicform.dynamicdata.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DynamicDataDtos {
    private DynamicDataDtos() {}

    public record CreateRecordRequest(UUID sourceDocumentId, String status, @NotNull Map<String, Object> data) {}
    public record UpdateRecordRequest(@NotNull @Min(0) Long rowVersion, UUID sourceDocumentId, String status,
                                      @NotNull Map<String, Object> data) {}
    public record DynamicRecord(UUID id, UUID sourceDocumentId, String sourceDocumentUrl, String recordStatus, Long rowVersion,
                                Instant createdAt, Instant updatedAt, Map<String, Object> data) {}
    public record Filter(@NotBlank String field, @NotBlank String operator, Object value) {}
    public record Sort(@NotBlank String field, String direction) {}
    public record SearchRequest(@Valid List<Filter> filters, @Valid List<Sort> sort,
                                @Min(0) Integer page, @Min(1) @Max(200) Integer size,
                                String keyword, LocalDate fromDate, LocalDate toDate, String dateField,
                                @JsonAlias("recordStatus") String status,
                                @JsonAlias("hasAttachment") Boolean hasDocument) {}
    public record SearchPage(List<DynamicRecord> items, int page, int size, long totalElements, int totalPages) {}
}
