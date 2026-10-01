package com.example.dynamicform.batch;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.dynamicdata.DynamicDataService;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.DynamicRecord;
import com.example.dynamicform.template.domain.FieldDefinition;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BatchExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final IngestionBatchRepository batches;
    private final BatchDocumentRepository documents;
    private final DynamicDataService dynamicData;

    @Value("${app.export.max-rows:10000}")
    private int maxRows;

    @Transactional(readOnly = true)
    public ExportFile export(UUID batchId, WorkflowStatus status, LocalDate fromDate, LocalDate toDate, String format) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw ApiException.badRequest("Ngày bắt đầu không được sau ngày kết thúc.");
        }
        IngestionBatch batch = batches.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt hồ sơ."));
        List<BatchDocument> items = documents.findByBatchIdAndDeletedFalseOrderBySequenceNoAsc(batchId).stream()
                .filter(item -> status == null || item.getWorkflowStatus() == status)
                .filter(item -> inRange(item.getUpdatedAt(), fromDate, toDate))
                .limit(maxRows)
                .toList();
        var context = dynamicData.context(batch.getTemplateVersion().getId());
        List<FieldDefinition> fields = context.fields().stream().filter(FieldDefinition::getExportable).toList();
        List<ExportRow> rows = items.stream().map(item -> new ExportRow(item, loadRecord(batch, item))).toList();
        String extension = "csv".equalsIgnoreCase(format) ? "csv" : "xlsx";
        byte[] bytes = "csv".equals(extension) ? csv(fields, rows) : xlsx(fields, rows);
        String contentType = "csv".equals(extension) ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return new ExportFile(contentType, batch.getBatchCode() + "." + extension, bytes);
    }

    private DynamicRecord loadRecord(IngestionBatch batch, BatchDocument item) {
        return item.getDynamicRecordId() == null ? null
                : dynamicData.getForVersion(batch.getTemplateVersion().getId(), item.getDynamicRecordId());
    }

    private boolean inRange(Instant value, LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        if (fromDate != null && value.isBefore(fromDate.atStartOfDay(zone).toInstant())) return false;
        return toDate == null || value.isBefore(toDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private byte[] csv(List<FieldDefinition> fields, List<ExportRow> rows) {
        StringBuilder out = new StringBuilder("\uFEFF");
        appendCsvHeader(out, fields);
        for (ExportRow row : rows) {
            List<Object> values = systemValues(row);
            fields.forEach(field -> values.add(row.record() == null ? null : row.record().data().get(field.getFieldCode())));
            out.append(values.stream().map(this::escape).reduce((left, right) -> left + "," + right).orElse(""))
                    .append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvHeader(StringBuilder out, List<FieldDefinition> fields) {
        List<String> headers = new ArrayList<>(List.of("Mã đợt", "STT", "Tên file", "Đường dẫn", "Trạng thái",
                "Người nhập", "Ngày gửi duyệt", "Người duyệt", "Ngày duyệt", "Lý do trả lại"));
        fields.forEach(field -> headers.add(field.getLabel()));
        out.append(headers.stream().map(this::escape).reduce((left, right) -> left + "," + right).orElse(""))
                .append('\n');
    }

    private byte[] xlsx(List<FieldDefinition> fields, List<ExportRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Du lieu lo");
            List<String> headers = new ArrayList<>(List.of("Mã đợt", "STT", "Tên file", "Đường dẫn", "Trạng thái",
                    "Người nhập", "Ngày gửi duyệt", "Người duyệt", "Ngày duyệt", "Lý do trả lại"));
            fields.forEach(field -> headers.add(field.getLabel()));
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            int rowNo = 1;
            for (ExportRow exportRow : rows) {
                Row row = sheet.createRow(rowNo++);
                List<Object> values = systemValues(exportRow);
                fields.forEach(field -> values.add(exportRow.record() == null ? null
                        : exportRow.record().data().get(field.getFieldCode())));
                for (int i = 0; i < values.size(); i++) setCell(row.createCell(i), values.get(i));
            }
            for (int i = 0; i < Math.min(headers.size(), 50); i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo file Excel theo đợt.", e);
        }
    }

    private List<Object> systemValues(ExportRow row) {
        BatchDocument item = row.item();
        return new ArrayList<>(List.of(
                item.getBatch().getBatchCode(), item.getSequenceNo(), item.getDisplayName(),
                Objects.toString(item.getRelativePath(), ""), item.getWorkflowStatus().name(),
                Objects.toString(item.getAssignedUserId(), ""), format(item.getSubmittedAt()),
                Objects.toString(item.getApprovedBy(), ""), format(item.getApprovedAt()),
                Objects.toString(item.getRejectionReason(), "")
        ));
    }

    private String format(Instant value) { return value == null ? "" : DATE_TIME.format(value); }

    private void setCell(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (value instanceof Boolean bool) cell.setCellValue(bool);
        else if (value instanceof LocalDate date) cell.setCellValue(DATE.format(date));
        else if (value instanceof OffsetDateTime dateTime) cell.setCellValue(dateTime.format(DATE_TIME));
        else cell.setCellValue(value.toString());
    }

    private String escape(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private record ExportRow(BatchDocument item, DynamicRecord record) {}
    public record ExportFile(String contentType, String filename, byte[] bytes) {}
}
