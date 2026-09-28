package com.example.dynamicform.export;

import com.example.dynamicform.dynamicdata.DynamicDataService;
import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.*;
import com.example.dynamicform.template.domain.FieldDefinition;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ExportService {
    private final DynamicDataService dynamicData;
    @Value("${app.export.max-rows:10000}") private int maxRows;

    public ExportFile export(String templateCode, SearchRequest request, String format) {
        var ctx = dynamicData.context(templateCode);
        List<DynamicRecord> rows = new ArrayList<>(); int page = 0;
        while (rows.size() < maxRows) {
            SearchPage result = dynamicData.search(templateCode, new SearchRequest(
                    request.filters(), request.sort(), page++, Math.min(200, maxRows - rows.size()),
                    request.keyword(), request.fromDate(), request.toDate(), request.dateField(),
                    request.status(), request.hasDocument()));
            rows.addAll(result.items());
            if (result.items().isEmpty() || page >= result.totalPages()) break;
        }
        List<FieldDefinition> fields = ctx.fields().stream().filter(FieldDefinition::getExportable).toList();
        if ("csv".equalsIgnoreCase(format)) return new ExportFile("text/csv", templateCode + ".csv", csv(fields, rows));
        return new ExportFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", templateCode + ".xlsx", xlsx(fields, rows));
    }

    private byte[] csv(List<FieldDefinition> fields, List<DynamicRecord> rows) {
        StringBuilder out = new StringBuilder("\uFEFF");
        out.append("ID,Trạng thái"); fields.forEach(f -> out.append(',').append(escape(f.getLabel()))); out.append('\n');
        for (DynamicRecord row : rows) {
            out.append(escape(row.id())).append(',').append(escape(row.recordStatus()));
            fields.forEach(f -> out.append(',').append(escape(row.data().get(f.getFieldCode())))); out.append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(List<FieldDefinition> fields, List<DynamicRecord> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data"); Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
            header.createCell(0).setCellValue("ID"); header.createCell(1).setCellValue("Trạng thái");
            for (int i = 0; i < fields.size(); i++) { Cell cell = header.createCell(i + 2); cell.setCellValue(fields.get(i).getLabel()); cell.setCellStyle(headerStyle); }
            int rowNo = 1;
            for (DynamicRecord record : rows) {
                Row row = sheet.createRow(rowNo++); row.createCell(0).setCellValue(record.id().toString()); row.createCell(1).setCellValue(record.recordStatus());
                for (int i = 0; i < fields.size(); i++) setCell(row.createCell(i + 2), record.data().get(fields.get(i).getFieldCode()));
            }
            for (int i = 0; i < Math.min(fields.size() + 2, 50); i++) sheet.autoSizeColumn(i);
            workbook.write(out); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Không thể tạo file Excel", e); }
    }

    private void setCell(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof Number n) cell.setCellValue(n.doubleValue());
        else if (value instanceof Boolean b) cell.setCellValue(b);
        else if (value instanceof LocalDate date) cell.setCellValue(date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        else if (value instanceof OffsetDateTime dateTime) cell.setCellValue(dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
        else cell.setCellValue(value.toString());
    }
    private String escape(Object value) {
        String s = value == null ? "" : value instanceof LocalDate date ? date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : value instanceof OffsetDateTime dateTime ? dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : value.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }
    public record ExportFile(String contentType, String filename, byte[] bytes) {}
}
