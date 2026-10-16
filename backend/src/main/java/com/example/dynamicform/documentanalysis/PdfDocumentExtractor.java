package com.example.dynamicform.documentanalysis;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfDocumentExtractor {
    @Value("${app.document-analysis.max-text-characters:200000}")
    private int maxTextCharacters;

    public ExtractedDocument extract(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            List<PageText> pages = new ArrayList<>();
            StringBuilder combined = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                pages.add(new PageText(page, text));
                if (!text.isBlank() && combined.length() < maxTextCharacters) {
                    if (!combined.isEmpty()) combined.append('\n');
                    combined.append(text, 0, Math.min(text.length(), maxTextCharacters - combined.length()));
                }
            }

            List<AcroField> acroFields = new ArrayList<>();
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form != null) {
                for (PDField field : form.getFieldTree()) {
                    String name = firstNonBlank(field.getAlternateFieldName(), field.getPartialName(), field.getFullyQualifiedName());
                    if (name == null) continue;
                    String value = field.getValueAsString();
                    int page = 1;
                    if (!field.getWidgets().isEmpty() && field.getWidgets().getFirst().getPage() != null) {
                        page = document.getPages().indexOf(field.getWidgets().getFirst().getPage()) + 1;
                    }
                    acroFields.add(new AcroField(name.trim(), value == null ? "" : value.trim(), field.getFieldType(), page));
                }
            }
            DocumentSourceKind kind = acroFields.isEmpty() ? DocumentSourceKind.DIGITAL_TEXT : DocumentSourceKind.ACROFORM;
            return new ExtractedDocument(kind, combined.toString(), pages, acroFields);
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u0000', ' ').replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" +", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    public record ExtractedDocument(DocumentSourceKind sourceKind, String text,
                                    List<PageText> pages, List<AcroField> acroFields) {}
    public record PageText(int page, String text) {}
    public record AcroField(String label, String value, String fieldType, int page) {}
}
