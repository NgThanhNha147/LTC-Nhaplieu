package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.audit.AuditService;
import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.*;
import com.example.dynamicform.template.domain.*;
import com.example.dynamicform.template.repository.*;
import com.example.dynamicform.schema.TemplateOperationLock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DynamicDataService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<FieldDataType> KEYWORD_TYPES = Set.of(
            FieldDataType.STRING, FieldDataType.TEXTAREA, FieldDataType.EMAIL,
            FieldDataType.PHONE, FieldDataType.LIST);
    private final TemplateVersionRepository versions;
    private final FieldDefinitionRepository fieldRepository;
    private final ValidationRuleRepository ruleRepository;
    private final DynamicRecordValidator validator;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final TemplateOperationLock operationLock;

    @Transactional
    public DynamicRecord create(String templateCode, CreateRecordRequest request) {
        operationLock.lockForDataChange(templateCode);
        Context ctx = context(templateCode);
        String status = normalizeStatus(request.status());
        Map<String, Object> data = validator.validateAndConvert(writablePayload(request.data(), ctx.fields()),
                ctx.fields(), ctx.rules(), "COMPLETED".equals(status));
        UUID id = UUID.randomUUID(); Timestamp now = Timestamp.from(Instant.now());
        List<String> columns = new ArrayList<>(List.of("id", "source_document_id"));
        List<String> values = new ArrayList<>(List.of(":id", ":sourceDocumentId"));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("sourceDocumentId", request.sourceDocumentId());
        for (FieldDefinition f : ctx.fields()) { columns.add(f.getColumnName()); values.add(":" + f.getFieldCode()); params.addValue(f.getFieldCode(), data.get(f.getFieldCode())); }
        columns.addAll(List.of("record_status", "created_at", "created_by", "updated_at", "updated_by"));
        values.addAll(List.of(":status", ":now", ":user", ":now", ":user"));
        params.addValue("status", status).addValue("now", now).addValue("user", currentUser.username());
        jdbc.update("INSERT INTO " + ctx.qualifiedTable() + " (" + String.join(",", columns) + ") VALUES (" + String.join(",", values) + ")", params);
        DynamicRecord result = get(ctx, id);
        audit.log(ctx.version().getId(), id, "CREATE", null, result.data());
        return result;
    }

    @Transactional(readOnly = true)
    public DynamicRecord get(String templateCode, UUID id) { return get(context(templateCode), id); }

    @Transactional
    public DynamicRecord update(String templateCode, UUID id, UpdateRecordRequest request) {
        operationLock.lockForDataChange(templateCode);
        Context ctx = context(templateCode); DynamicRecord old = get(ctx, id);
        String status = normalizeStatus(request.status());
        Map<String, Object> merged = new LinkedHashMap<>(old.data());
        merged.putAll(writablePayload(request.data(), ctx.fields()));
        Map<String, Object> data = validator.validateAndConvert(merged, ctx.fields(), ctx.rules(),
                "COMPLETED".equals(status), false, request.data().keySet());
        String assignments = ctx.fields().stream().map(f -> f.getColumnName() + "=:" + f.getFieldCode()).collect(Collectors.joining(","));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("expectedVersion", request.rowVersion())
                .addValue("sourceDocumentId", request.sourceDocumentId()).addValue("status", status)
                .addValue("now", Timestamp.from(Instant.now())).addValue("user", currentUser.username());
        ctx.fields().forEach(f -> params.addValue(f.getFieldCode(), data.get(f.getFieldCode())));
        int changed = jdbc.update("UPDATE " + ctx.qualifiedTable() + " SET " + assignments
                + ",source_document_id=:sourceDocumentId,record_status=:status,updated_at=:now,updated_by=:user,row_version=row_version+1"
                + " WHERE id=:id AND row_version=:expectedVersion AND deleted=FALSE", params);
        if (changed == 0) throw ApiException.conflict("Hồ sơ đã được người khác thay đổi hoặc không còn tồn tại. Hãy tải lại dữ liệu trước khi tiếp tục.");
        DynamicRecord result = get(ctx, id); audit.log(ctx.version().getId(), id, "UPDATE", old.data(), result.data()); return result;
    }

    @Transactional
    public void delete(String templateCode, UUID id, Long rowVersion) {
        operationLock.lockForDataChange(templateCode);
        Context ctx = context(templateCode); DynamicRecord old = get(ctx, id);
        long expectedVersion = rowVersion == null ? old.rowVersion() : rowVersion;
        int changed = jdbc.update("UPDATE " + ctx.qualifiedTable() + " SET deleted=TRUE,updated_at=:now,updated_by=:user,row_version=row_version+1 WHERE id=:id AND row_version=:version AND deleted=FALSE",
                new MapSqlParameterSource().addValue("now", Timestamp.from(Instant.now())).addValue("user", currentUser.username()).addValue("id", id).addValue("version", expectedVersion));
        if (changed == 0) throw ApiException.conflict("Hồ sơ đã được người khác thay đổi hoặc không còn tồn tại. Hãy tải lại dữ liệu trước khi tiếp tục.");
        audit.log(ctx.version().getId(), id, "DELETE", old.data(), null);
    }

    @Transactional(readOnly = true)
    public SearchPage search(String templateCode, SearchRequest request) {
        Context ctx = context(templateCode); int page = request.page() == null ? 0 : request.page(); int size = request.size() == null ? 20 : request.size();
        Map<String, FieldDefinition> byCode = ctx.fields().stream().collect(Collectors.toMap(FieldDefinition::getFieldCode, f -> f));
        StringBuilder where = new StringBuilder(" WHERE deleted=FALSE"); MapSqlParameterSource params = new MapSqlParameterSource(); int i = 0;
        appendKeyword(where, params, request.keyword(), ctx.fields());
        appendDateRange(where, params, request.fromDate(), request.toDate(), request.dateField());
        appendStatus(where, params, request.status());
        appendDocumentFilter(where, request.hasDocument());
        if (request.filters() != null) for (Filter filter : request.filters()) {
            FieldDefinition f = byCode.get(filter.field());
            if (f == null || !Boolean.TRUE.equals(f.getSearchable())) throw ApiException.badRequest("Trường dữ liệu này không được cấu hình để tìm kiếm: " + filter.field());
            appendFilter(where, params, f, filter, i++);
        }
        String order = buildOrder(request.sort(), byCode);
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + ctx.qualifiedTable() + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", page * size);
        List<DynamicRecord> items = jdbc.query("SELECT * FROM " + ctx.qualifiedTable() + where + order + " LIMIT :limit OFFSET :offset",
                params, (rs, n) -> mapRecord(rs, ctx.fields()));
        long count = total == null ? 0 : total;
        return new SearchPage(items, page, size, count, (int) Math.ceil((double) count / size));
    }

    private void appendKeyword(StringBuilder where, MapSqlParameterSource params, String keyword,
                               List<FieldDefinition> fields) {
        if (keyword == null || keyword.isBlank()) return;
        List<String> columns = fields.stream()
                .filter(field -> Boolean.TRUE.equals(field.getSearchable()))
                .filter(field -> KEYWORD_TYPES.contains(field.getDataType()))
                .map(FieldDefinition::getColumnName)
                .toList();
        if (columns.isEmpty()) {
            where.append(" AND FALSE");
            return;
        }
        where.append(" AND (");
        where.append(columns.stream()
                .map(column -> "LOWER(CAST(" + column + " AS TEXT)) LIKE LOWER(:keyword) ESCAPE '!'")
                .collect(Collectors.joining(" OR ")));
        where.append(')');
        String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        params.addValue("keyword", "%" + escaped + "%");
    }

    private void appendDateRange(StringBuilder where, MapSqlParameterSource params, LocalDate fromDate,
                                 LocalDate toDate, String requestedDateField) {
        if (fromDate == null && toDate == null) return;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw ApiException.badRequest("Ngày bắt đầu không được sau ngày kết thúc.");
        }
        String column = normalizeDateField(requestedDateField);
        if (fromDate != null) {
            where.append(" AND ").append(column).append(" >= :fromDate");
            params.addValue("fromDate", Timestamp.from(fromDate.atStartOfDay(BUSINESS_ZONE).toInstant()));
        }
        if (toDate != null) {
            where.append(" AND ").append(column).append(" < :toDateExclusive");
            params.addValue("toDateExclusive", Timestamp.from(toDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()));
        }
    }

    private String normalizeDateField(String field) {
        if (field == null || field.isBlank() || "createdAt".equals(field) || "created_at".equals(field)) return "created_at";
        if ("updatedAt".equals(field) || "updated_at".equals(field)) return "updated_at";
        throw ApiException.badRequest("Loại ngày dùng để lọc không hợp lệ.");
    }

    private void appendStatus(StringBuilder where, MapSqlParameterSource params, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) return;
        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "COMPLETED").contains(status)) {
            throw ApiException.badRequest("Trạng thái hồ sơ dùng để lọc không hợp lệ.");
        }
        where.append(" AND record_status = :recordStatus");
        params.addValue("recordStatus", status);
    }

    private void appendDocumentFilter(StringBuilder where, Boolean hasDocument) {
        if (hasDocument == null) return;
        where.append(hasDocument ? " AND source_document_id IS NOT NULL" : " AND source_document_id IS NULL");
    }

    public Context context(String code) {
        TemplateVersion version = versions.findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc(code, VersionStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Biểu mẫu chưa được đưa vào sử dụng. Hãy kiểm tra cấu hình, tạo bảng dữ liệu và chọn 'Đưa vào sử dụng'."));
        List<FieldDefinition> fields = fieldRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId());
        List<ValidationRule> rules = ruleRepository.findByFieldIdIn(fields.stream().map(FieldDefinition::getId).toList());
        return new Context(version, fields, rules);
    }

    private DynamicRecord get(Context ctx, UUID id) {
        List<DynamicRecord> rows = jdbc.query("SELECT * FROM " + ctx.qualifiedTable() + " WHERE id=:id AND deleted=FALSE",
                new MapSqlParameterSource("id", id), (rs, n) -> mapRecord(rs, ctx.fields()));
        if (rows.isEmpty()) throw ApiException.notFound("Không tìm thấy hồ sơ.");
        return rows.getFirst();
    }

    private DynamicRecord mapRecord(ResultSet rs, List<FieldDefinition> fields) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        for (FieldDefinition f : fields) data.put(f.getFieldCode(), readValue(rs, f));
        UUID sourceDocumentId = rs.getObject("source_document_id", UUID.class);
        String sourceDocumentUrl = sourceDocumentId == null ? null : "/api/v1/documents/" + sourceDocumentId + "/content";
        return new DynamicRecord(rs.getObject("id", UUID.class), sourceDocumentId, sourceDocumentUrl,
                rs.getString("record_status"), rs.getLong("row_version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), data);
    }

    private Object readValue(ResultSet rs, FieldDefinition field) throws SQLException {
        return switch (field.getDataType()) {
            case DATE -> rs.getObject(field.getColumnName(), java.time.LocalDate.class);
            case DATETIME -> rs.getObject(field.getColumnName(), java.time.OffsetDateTime.class);
            case UUID -> rs.getObject(field.getColumnName(), UUID.class);
            default -> rs.getObject(field.getColumnName());
        };
    }

    private void appendFilter(StringBuilder where, MapSqlParameterSource params, FieldDefinition f, Filter filter, int i) {
        String op = filter.operator().toUpperCase(); String param = "p" + i; String col = f.getColumnName();
        if (filter.value() == null && !Set.of("IS_NULL", "IS_NOT_NULL").contains(op)) {
            throw ApiException.badRequest(op + " cần giá trị lọc");
        }
        switch (op) {
            case "EQ" -> { where.append(" AND ").append(col).append(" = :").append(param); params.addValue(param, convertFilter(f, filter.value())); }
            case "NE" -> { where.append(" AND ").append(col).append(" <> :").append(param); params.addValue(param, convertFilter(f, filter.value())); }
            case "CONTAINS" -> { requireText(f, op); where.append(" AND LOWER(").append(col).append(") LIKE LOWER(:").append(param).append(")"); params.addValue(param, "%" + filter.value() + "%"); }
            case "STARTS_WITH" -> { requireText(f, op); where.append(" AND LOWER(").append(col).append(") LIKE LOWER(:").append(param).append(")"); params.addValue(param, filter.value() + "%"); }
            case "GT", "GREATER_THAN" -> { requireComparable(f, op); comparison(where, params, col, param, ">", convertFilter(f, filter.value())); }
            case "GTE", "GREATER_THAN_OR_EQUAL" -> { requireComparable(f, op); comparison(where, params, col, param, ">=", convertFilter(f, filter.value())); }
            case "LT", "LESS_THAN" -> { requireComparable(f, op); comparison(where, params, col, param, "<", convertFilter(f, filter.value())); }
            case "LTE", "LESS_THAN_OR_EQUAL" -> { requireComparable(f, op); comparison(where, params, col, param, "<=", convertFilter(f, filter.value())); }
            case "IS_NULL" -> where.append(" AND ").append(col).append(" IS NULL");
            case "IS_NOT_NULL" -> where.append(" AND ").append(col).append(" IS NOT NULL");
            case "IN" -> {
                if (!(filter.value() instanceof Collection<?> values) || values.isEmpty()) throw ApiException.badRequest("IN cần danh sách giá trị");
                where.append(" AND ").append(col).append(" IN (:").append(param).append(")");
                params.addValue(param, values.stream().map(v -> convertFilter(f, v)).toList());
            }
            case "BETWEEN" -> {
                requireComparable(f, op);
                if (!(filter.value() instanceof List<?> values) || values.size() != 2) throw ApiException.badRequest("BETWEEN cần đúng 2 giá trị");
                where.append(" AND ").append(col).append(" BETWEEN :").append(param).append("a AND :").append(param).append("b");
                params.addValue(param + "a", convertFilter(f, values.get(0))).addValue(param + "b", convertFilter(f, values.get(1)));
            }
            default -> throw ApiException.badRequest("Điều kiện lọc không được hỗ trợ: " + op);
        }
    }

    private Object convertFilter(FieldDefinition f, Object value) {
        return validator.validateAndConvert(Map.of(f.getFieldCode(), value), List.of(f), List.of(), false, false).get(f.getFieldCode());
    }
    private void comparison(StringBuilder w, MapSqlParameterSource p, String c, String n, String op, Object value) { w.append(" AND ").append(c).append(' ').append(op).append(" :").append(n); p.addValue(n, value); }
    private void requireText(FieldDefinition f, String op) { if (!Set.of(FieldDataType.STRING, FieldDataType.TEXTAREA, FieldDataType.EMAIL, FieldDataType.PHONE, FieldDataType.LIST).contains(f.getDataType())) throw ApiException.badRequest(op + " chỉ dùng cho chuỗi"); }
    private void requireComparable(FieldDefinition f, String op) { if (!Set.of(FieldDataType.INTEGER, FieldDataType.DECIMAL, FieldDataType.DATE, FieldDataType.DATETIME).contains(f.getDataType())) throw ApiException.badRequest(op + " chỉ dùng cho số hoặc ngày"); }
    private String buildOrder(List<Sort> sorts, Map<String, FieldDefinition> fields) {
        if (sorts == null || sorts.isEmpty()) return " ORDER BY created_at DESC";
        return " ORDER BY " + sorts.stream().map(s -> {
            if ("createdAt".equals(s.field())) return "created_at " + direction(s.direction());
            FieldDefinition f = fields.get(s.field());
            if (f == null || !Boolean.TRUE.equals(f.getSortable())) throw ApiException.badRequest("Trường dữ liệu này không được cấu hình để sắp xếp: " + s.field());
            return f.getColumnName() + " " + direction(s.direction());
        }).collect(Collectors.joining(","));
    }
    private String direction(String d) { return "ASC".equalsIgnoreCase(d) ? "ASC" : "DESC"; }
    private String normalizeStatus(String status) {
        String value = status == null ? "DRAFT" : status.toUpperCase();
        if (!Set.of("DRAFT", "COMPLETED").contains(value)) throw ApiException.badRequest("Trạng thái hồ sơ không hợp lệ.");
        return value;
    }

    private Map<String, Object> writablePayload(Map<String, Object> payload, List<FieldDefinition> fields) {
        Set<String> readOnlyCodes = fields.stream()
                .filter(field -> Boolean.TRUE.equals(field.getReadOnly()))
                .map(FieldDefinition::getFieldCode)
                .collect(Collectors.toSet());
        Map<String, Object> writable = new LinkedHashMap<>(payload);
        readOnlyCodes.forEach(writable::remove);
        return writable;
    }

    public record Context(TemplateVersion version, List<FieldDefinition> fields, List<ValidationRule> rules) {
        public String qualifiedTable() { return version.getPhysicalSchema() + "." + version.getPhysicalTable(); }
    }
}
