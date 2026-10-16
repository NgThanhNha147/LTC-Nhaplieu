package com.example.dynamicform.documentanalysis;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.document.DocumentService;
import com.example.dynamicform.document.DocumentRepository;
import com.example.dynamicform.documentanalysis.DocumentAnalysisDtos.*;
import com.example.dynamicform.template.domain.ComponentType;
import com.example.dynamicform.template.domain.FieldDataType;
import com.example.dynamicform.template.dto.TemplateDtos.*;
import com.example.dynamicform.template.service.MetadataService;
import com.example.dynamicform.template.service.TemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {
    private final DocumentAnalysisRepository analyses;
    private final DocumentDraftRecordRepository drafts;
    private final DocumentService documents;
    private final DocumentRepository documentRepository;
    private final PdfDocumentExtractor pdfExtractor;
    private final ScanTextRecognitionClient scanRecognition;
    private final DocumentSemanticAnalyzer semanticAnalyzer;
    private final TemplateMatcher templateMatcher;
    private final TemplateService templateService;
    private final MetadataService metadataService;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.document-analysis.minimum-digital-text-length:80}")
    private int minimumDigitalTextLength;

    public AnalysisResponse analyze(UUID documentId) {
        var download = documents.download(documentId);
        if (!"application/pdf".equalsIgnoreCase(download.metadata().contentType())) {
            throw ApiException.badRequest("Bản thử nghiệm phân tích tự động hiện hỗ trợ tài liệu PDF.");
        }

        Instant now = Instant.now();
        DocumentAnalysis analysis = new DocumentAnalysis();
        analysis.setId(UUID.randomUUID());
        analysis.setDocumentFile(documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài liệu.")));
        analysis.setStatus(AnalysisStatus.PROCESSING);
        analysis.setSuggestedSchema(objectMapper.createObjectNode().set("groups", objectMapper.createArrayNode()));
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        analysis.setCreatedBy(currentUser.username());
        analysis.setUpdatedBy(currentUser.username());
        analysis = analyses.save(analysis);

        try {
            Path path = download.resource().getFile().toPath().toAbsolutePath().normalize();
            PdfDocumentExtractor.ExtractedDocument extracted = pdfExtractor.extract(path);
            String text = extracted.text();
            DocumentSourceKind sourceKind = extracted.sourceKind();
            if (text.replaceAll("\\s+", "").length() < minimumDigitalTextLength && extracted.acroFields().isEmpty()) {
                text = scanRecognition.extract(path);
                sourceKind = DocumentSourceKind.SCAN_TEXT;
                extracted = new PdfDocumentExtractor.ExtractedDocument(sourceKind, text,
                        List.of(new PdfDocumentExtractor.PageText(1, text)), List.of());
            }

            DocumentSemanticAnalyzer.AnalysisResult result = semanticAnalyzer.analyze(extracted);
            analysis.setSourceKind(sourceKind);
            analysis.setExtractedText(text);
            analysis.setDocumentType(result.documentType());
            analysis.setConfidence(result.confidence());
            analysis.setSuggestedSchema(objectMapper.valueToTree(result.form()));
            Optional<TemplateMatcher.MatchResult> templateMatch = templateMatcher.bestMatch(result.form());
            if (templateMatch.isPresent()) {
                analysis.setMatchedTemplateVersion(templateMatch.get().version());
                analysis.setMatchConfidence(templateMatch.get().confidence());
            }
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setErrorMessage(null);
            analysis.setUpdatedAt(Instant.now());
            analysis.setUpdatedBy(currentUser.username());
            analysis = analyses.save(analysis);
            UUID analysisId = analysis.getId();
            return transactionTemplate.execute(status -> map(require(analysisId)));
        } catch (ApiException e) {
            markFailed(analysis, e.getMessage());
            throw e;
        } catch (Exception e) {
            markFailed(analysis, "Không thể phân tích cấu trúc tài liệu. Hãy kiểm tra lại file hoặc thử với tài liệu rõ hơn.");
            throw ApiException.badRequest("Không thể phân tích cấu trúc tài liệu. Hãy kiểm tra lại file hoặc thử với tài liệu rõ hơn.");
        }
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(UUID id) {
        DocumentAnalysis analysis = require(id);
        documents.assertCanView(analysis.getDocumentFile().getId());
        return map(analysis);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> recent() {
        return analyses.findTop30ByOrderByUpdatedAtDesc().stream()
                .filter(analysis -> canView(analysis.getDocumentFile().getId()))
                .map(this::map).toList();
    }

    public AnalysisResponse review(UUID id, ReviewRequest request) {
        transactionTemplate.executeWithoutResult(status -> reviewInTransaction(id, request));
        return transactionTemplate.execute(status -> map(require(id)));
    }

    private void reviewInTransaction(UUID id, ReviewRequest request) {
        DocumentAnalysis analysis = require(id);
        documents.assertCanView(analysis.getDocumentFile().getId());
        requireVersion(analysis, request.rowVersion());
        SuggestedForm normalized = normalizeAndValidate(request.form());
        JsonNode schema = objectMapper.valueToTree(normalized);
        analysis.setReviewedSchema(schema);
        analysis.setStatus(AnalysisStatus.REVIEWED);
        analysis.setUpdatedAt(Instant.now());
        analysis.setUpdatedBy(currentUser.username());

        DocumentDraftRecord draft = drafts.findByAnalysisId(id).orElseGet(() -> {
            DocumentDraftRecord created = new DocumentDraftRecord();
            created.setId(UUID.randomUUID());
            created.setAnalysis(analysis);
            created.setCreatedAt(Instant.now());
            created.setCreatedBy(currentUser.username());
            return created;
        });
        draft.setSchemaSnapshot(schema);
        draft.setData(extractData(normalized));
        draft.setStatus("CONFIRMED");
        draft.setUpdatedAt(Instant.now());
        draft.setUpdatedBy(currentUser.username());
        drafts.save(draft);
        analyses.save(analysis);
    }

    @Transactional
    public PromoteResponse promote(UUID id, PromoteRequest request) {
        DocumentAnalysis analysis = require(id);
        documents.assertCanView(analysis.getDocumentFile().getId());
        requireVersion(analysis, request.rowVersion());
        SuggestedForm form = readForm(analysis.getReviewedSchema() == null ? analysis.getSuggestedSchema() : analysis.getReviewedSchema());
        form = normalizeAndValidate(form);

        TemplateResponse template = templateService.create(new TemplateRequest(
                request.templateCode(), request.templateName(), request.description()));
        UUID versionId = template.currentVersionId();
        if (versionId == null) throw new IllegalStateException("Không xác định được phiên bản biểu mẫu vừa tạo.");

        int groupOrder = 0;
        int fieldCount = 0;
        int groupCount = 0;
        for (SuggestedGroup group : form.groups()) {
            List<SuggestedField> selected = group.fields().stream().filter(SuggestedField::selected).toList();
            if (selected.isEmpty()) continue;
            GroupResponse createdGroup = metadataService.createGroup(versionId, new GroupRequest(
                    group.code(), group.label(), null, groupOrder++, 2, false, false, false));
            int fieldOrder = 0;
            for (SuggestedField field : selected) {
                FieldDataType dataType = parseDataType(field.dataType());
                ComponentType componentType = parseComponent(field.componentType(), dataType);
                metadataService.createField(versionId, new FieldRequest(
                        createdGroup.id(), field.fieldCode(), field.fieldCode(), field.label(), dataType, componentType,
                        null, null, field.required(), false, false, false, true, true, true,
                        stringLength(dataType), dataType == FieldDataType.DECIMAL ? 18 : null,
                        dataType == FieldDataType.DECIMAL ? 2 : null, null, fieldOrder++, 6,
                        false, false, field.evidence(), null, null, List.of()));
                fieldCount++;
            }
            groupCount++;
        }

        analysis.setReviewedSchema(objectMapper.valueToTree(form));
        analysis.setStatus(AnalysisStatus.PROMOTED);
        analysis.setUpdatedAt(Instant.now());
        analysis.setUpdatedBy(currentUser.username());
        drafts.findByAnalysisId(id).ifPresent(draft -> {
            draft.setStatus("PROMOTED");
            draft.setUpdatedAt(Instant.now());
            draft.setUpdatedBy(currentUser.username());
        });
        return new PromoteResponse(template.id(), versionId, template.code(), template.name(), groupCount, fieldCount);
    }

    private SuggestedForm normalizeAndValidate(SuggestedForm form) {
        if (form == null || form.groups() == null || form.groups().isEmpty()) {
            throw ApiException.badRequest("Biểu mẫu đề xuất phải có ít nhất một nhóm thông tin.");
        }
        Set<String> groupCodes = new HashSet<>();
        Set<String> fieldCodes = new HashSet<>();
        List<SuggestedGroup> groups = new ArrayList<>();
        int groupOrder = 0;
        for (SuggestedGroup group : form.groups()) {
            String groupCode = safeCode(group.code(), true);
            if (!groupCodes.add(groupCode)) throw ApiException.badRequest("Mã nhóm bị trùng: " + groupCode);
            List<SuggestedField> normalizedFields = new ArrayList<>();
            for (SuggestedField field : group.fields()) {
                String fieldCode = safeCode(field.fieldCode(), false);
                if (field.selected() && !fieldCodes.add(fieldCode)) throw ApiException.badRequest("Mã trường bị trùng: " + fieldCode);
                FieldDataType dataType = parseDataType(field.dataType());
                ComponentType componentType = parseComponent(field.componentType(), dataType);
                normalizedFields.add(new SuggestedField(field.id() == null || field.id().isBlank() ? UUID.randomUUID().toString() : field.id(),
                        fieldCode, field.label().trim(), dataType.name(), componentType.name(), field.value(),
                        field.confidence() == null ? BigDecimal.ZERO : field.confidence(), field.page(), field.evidence(),
                        field.required(), field.selected()));
            }
            if (normalizedFields.isEmpty()) throw ApiException.badRequest("Nhóm '" + group.label() + "' chưa có trường dữ liệu.");
            groups.add(new SuggestedGroup(groupCode, group.label().trim(), groupOrder++, normalizedFields));
        }
        if (fieldCodes.isEmpty()) throw ApiException.badRequest("Cần chọn ít nhất một trường dữ liệu để lưu.");
        return new SuggestedForm(groups);
    }

    private ObjectNode extractData(SuggestedForm form) {
        ObjectNode data = objectMapper.createObjectNode();
        form.groups().forEach(group -> group.fields().stream().filter(SuggestedField::selected).forEach(field -> {
            if (field.value() == null) data.putNull(field.fieldCode());
            else data.put(field.fieldCode(), field.value());
        }));
        return data;
    }

    private AnalysisResponse map(DocumentAnalysis analysis) {
        SuggestedForm suggested = readForm(analysis.getSuggestedSchema());
        SuggestedForm reviewed = analysis.getReviewedSchema() == null ? null : readForm(analysis.getReviewedSchema());
        TemplateMatch match = null;
        if (analysis.getMatchedTemplateVersion() != null) {
            var version = analysis.getMatchedTemplateVersion();
            match = new TemplateMatch(version.getTemplate().getId(), version.getId(), version.getTemplate().getCode(),
                    version.getTemplate().getName(), version.getVersionNo(), analysis.getMatchConfidence());
        }
        UUID draftId = drafts.findByAnalysisId(analysis.getId()).map(DocumentDraftRecord::getId).orElse(null);
        var document = analysis.getDocumentFile();
        return new AnalysisResponse(analysis.getId(), document.getId(), document.getOriginalName(),
                "/api/v1/documents/" + document.getId() + "/content", analysis.getStatus(), analysis.getSourceKind(),
                analysis.getDocumentType(), analysis.getConfidence(), suggested, reviewed, match, draftId,
                analysis.getErrorMessage(), analysis.getRowVersion(), analysis.getCreatedAt(), analysis.getUpdatedAt());
    }

    private SuggestedForm readForm(JsonNode node) {
        try { return objectMapper.treeToValue(node, SuggestedForm.class); }
        catch (Exception e) { throw new IllegalStateException("Không thể đọc cấu trúc biểu mẫu đề xuất.", e); }
    }

    private DocumentAnalysis require(UUID id) {
        return analyses.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy kết quả phân tích tài liệu."));
    }

    private void requireVersion(DocumentAnalysis analysis, Long supplied) {
        long current = analysis.getRowVersion() == null ? 0 : analysis.getRowVersion();
        if (supplied == null || supplied != current) {
            throw ApiException.conflict("Kết quả phân tích đã được cập nhật ở nơi khác. Hãy tải lại trước khi lưu.");
        }
    }

    private boolean canView(UUID documentId) {
        try { documents.assertCanView(documentId); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    private void markFailed(DocumentAnalysis analysis, String message) {
        // A successful intermediate save can advance the optimistic-lock version on another instance.
        DocumentAnalysis latest = analyses.findById(analysis.getId()).orElse(analysis);
        latest.setStatus(AnalysisStatus.FAILED);
        latest.setErrorMessage(message == null ? "Không thể phân tích tài liệu." : message.substring(0, Math.min(1900, message.length())));
        latest.setUpdatedAt(Instant.now());
        latest.setUpdatedBy(currentUser.username());
        analyses.save(latest);
    }

    private String safeCode(String value, boolean upper) {
        String code = value == null ? "" : value.trim();
        if (!code.matches("^[A-Za-z][A-Za-z0-9_]{1,99}$")) throw ApiException.badRequest("Mã trường/nhóm không hợp lệ: " + value);
        return upper ? code.toUpperCase(Locale.ROOT) : code.toLowerCase(Locale.ROOT);
    }

    private FieldDataType parseDataType(String value) {
        try { return FieldDataType.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw ApiException.badRequest("Kiểu dữ liệu không được hỗ trợ: " + value); }
    }

    private ComponentType parseComponent(String value, FieldDataType dataType) {
        try { return ComponentType.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) {
            return switch (dataType) {
                case TEXTAREA -> ComponentType.TEXTAREA;
                case INTEGER, DECIMAL -> ComponentType.NUMBER;
                case DATE -> ComponentType.DATE;
                case DATETIME -> ComponentType.DATETIME;
                case BOOLEAN -> ComponentType.CHECKBOX;
                case LIST -> ComponentType.SELECT;
                case EMAIL -> ComponentType.EMAIL;
                case PHONE -> ComponentType.PHONE;
                default -> ComponentType.TEXT;
            };
        }
    }

    private Integer stringLength(FieldDataType type) {
        return switch (type) {
            case STRING, LIST, EMAIL, PHONE -> 500;
            case TEXTAREA -> 5000;
            default -> null;
        };
    }
}
