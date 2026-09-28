package com.example.dynamicform.lookup.service;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.lookup.domain.LookupSource;
import com.example.dynamicform.lookup.dto.LookupDtos.*;
import com.example.dynamicform.lookup.repository.LookupSourceRepository;
import com.example.dynamicform.schema.IdentifierPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LookupService {
    private final LookupSourceRepository repository;
    private final JdbcTemplate jdbc;
    private final IdentifierPolicy identifiers;

    @Transactional
    public LookupResponse create(LookupRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) throw ApiException.conflict("Mã danh mục đã tồn tại. Hãy sử dụng một mã khác.");
        if (!"ref_data".equals(request.sourceSchema())) throw ApiException.badRequest("Danh mục chỉ được kết nối với vùng dữ liệu dùng chung của hệ thống.");
        validateIdentifiers(request);
        validateSourceExists(request);
        LookupSource source = new LookupSource();
        source.setId(UUID.randomUUID()); source.setCode(request.code().toUpperCase()); source.setName(request.name());
        source.setSourceType(request.sourceType().toUpperCase()); source.setSourceSchema(request.sourceSchema());
        source.setSourceTable(request.sourceTable()); source.setValueColumn(request.valueColumn());
        source.setLabelColumn(request.labelColumn()); source.setActiveColumn(blankToNull(request.activeColumn()));
        source.setParentColumn(blankToNull(request.parentColumn())); source.setSortColumn(blankToNull(request.sortColumn()));
        source.setStatus(request.status() == null ? "ACTIVE" : request.status().toUpperCase()); source.setCreatedAt(Instant.now());
        repository.save(source);
        return map(source);
    }

    @Transactional(readOnly = true)
    public List<LookupResponse> list() { return repository.findAll().stream().map(this::map).toList(); }

    @Transactional
    public LookupResponse update(UUID id, LookupRequest request) {
        LookupSource source = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục lựa chọn."));
        if (!source.getCode().equalsIgnoreCase(request.code()) && repository.existsByCodeIgnoreCase(request.code())) {
            throw ApiException.conflict("Mã danh mục đã tồn tại. Hãy sử dụng một mã khác.");
        }
        if (!"ref_data".equals(request.sourceSchema())) throw ApiException.badRequest("Danh mục chỉ được kết nối với vùng dữ liệu dùng chung của hệ thống.");
        validateIdentifiers(request); validateSourceExists(request);
        source.setCode(request.code().toUpperCase()); source.setName(request.name()); source.setSourceType(request.sourceType().toUpperCase());
        source.setSourceSchema(request.sourceSchema()); source.setSourceTable(request.sourceTable()); source.setValueColumn(request.valueColumn());
        source.setLabelColumn(request.labelColumn()); source.setActiveColumn(blankToNull(request.activeColumn()));
        source.setParentColumn(blankToNull(request.parentColumn())); source.setSortColumn(blankToNull(request.sortColumn()));
        source.setStatus(request.status() == null ? "ACTIVE" : request.status().toUpperCase());
        return map(source);
    }

    @Transactional
    public void delete(UUID id) {
        LookupSource source = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục lựa chọn."));
        repository.delete(source);
    }

    @Transactional(readOnly = true)
    public OptionPage options(String code, String query, String parentValue, int page, int size) {
        LookupSource s = repository.findByCodeIgnoreCase(code).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục lựa chọn."));
        if (!"ACTIVE".equals(s.getStatus())) throw ApiException.conflict("Danh mục lựa chọn này đang tạm ngừng sử dụng.");
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * safeSize;
        StringBuilder sql = new StringBuilder("SELECT CAST(").append(s.getValueColumn()).append(" AS TEXT) AS value, CAST(")
                .append(s.getLabelColumn()).append(" AS TEXT) AS label FROM ").append(s.getSourceSchema()).append('.').append(s.getSourceTable())
                .append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (s.getActiveColumn() != null) sql.append(" AND ").append(s.getActiveColumn()).append(" = TRUE");
        if (query != null && !query.isBlank()) { sql.append(" AND LOWER(CAST(").append(s.getLabelColumn()).append(" AS TEXT)) LIKE LOWER(?)"); args.add("%" + query.trim() + "%"); }
        if (parentValue != null && !parentValue.isBlank()) {
            if (s.getParentColumn() == null) throw ApiException.badRequest("Danh mục này chưa được cấu hình quan hệ cha - con.");
            sql.append(" AND CAST(").append(s.getParentColumn()).append(" AS TEXT) = ?"); args.add(parentValue);
        }
        sql.append(" ORDER BY ").append(s.getSortColumn() == null ? s.getLabelColumn() : s.getSortColumn())
                .append(" LIMIT ? OFFSET ?"); args.add(safeSize + 1); args.add(offset);
        List<Option> rows = jdbc.query(sql.toString(), (rs, n) -> new Option(rs.getString("value"), rs.getString("label")), args.toArray());
        boolean more = rows.size() > safeSize;
        return new OptionPage(more ? rows.subList(0, safeSize) : rows, more);
    }

    @Transactional(readOnly = true)
    public boolean valueExists(LookupSource s, Object value) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + s.getSourceSchema() + "." + s.getSourceTable()
                + " WHERE CAST(" + s.getValueColumn() + " AS TEXT)=?" + (s.getActiveColumn() == null ? "" : " AND " + s.getActiveColumn() + "=TRUE") + ")";
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, String.valueOf(value)));
    }

    private void validateIdentifiers(LookupRequest r) {
        identifiers.requireValid(r.sourceSchema(), "Schema"); identifiers.requireValid(r.sourceTable(), "Bảng");
        identifiers.requireValid(r.valueColumn(), "Cột value"); identifiers.requireValid(r.labelColumn(), "Cột label");
        if (r.activeColumn() != null && !r.activeColumn().isBlank()) identifiers.requireValid(r.activeColumn(), "Cột active");
        if (r.parentColumn() != null && !r.parentColumn().isBlank()) identifiers.requireValid(r.parentColumn(), "Cột parent");
        if (r.sortColumn() != null && !r.sortColumn().isBlank()) identifiers.requireValid(r.sortColumn(), "Cột sort");
    }

    private void validateSourceExists(LookupRequest r) {
        Integer table = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?", Integer.class, r.sourceSchema(), r.sourceTable());
        if (table == null || table == 0) throw ApiException.badRequest("Không tìm thấy bảng chứa dữ liệu danh mục.");
        List<String> needed = new ArrayList<>(List.of(r.valueColumn(), r.labelColumn()));
        if (r.activeColumn() != null && !r.activeColumn().isBlank()) needed.add(r.activeColumn());
        if (r.parentColumn() != null && !r.parentColumn().isBlank()) needed.add(r.parentColumn());
        if (r.sortColumn() != null && !r.sortColumn().isBlank()) needed.add(r.sortColumn());
        for (String column : needed) {
            Integer found = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?",
                    Integer.class, r.sourceSchema(), r.sourceTable(), column);
            if (found == null || found == 0) throw ApiException.badRequest("Không tìm thấy cột dữ liệu của danh mục: " + column);
        }
    }

    private LookupResponse map(LookupSource s) { return new LookupResponse(s.getId(), s.getCode(), s.getName(), s.getSourceType(), s.getSourceSchema(), s.getSourceTable(), s.getValueColumn(), s.getLabelColumn(), s.getActiveColumn(), s.getParentColumn(), s.getSortColumn(), s.getStatus()); }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
