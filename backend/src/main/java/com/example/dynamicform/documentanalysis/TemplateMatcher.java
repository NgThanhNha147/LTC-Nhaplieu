package com.example.dynamicform.documentanalysis;

import com.example.dynamicform.documentanalysis.DocumentAnalysisDtos.SuggestedForm;
import com.example.dynamicform.template.domain.FieldDefinition;
import com.example.dynamicform.template.domain.TemplateVersion;
import com.example.dynamicform.template.domain.VersionStatus;
import com.example.dynamicform.template.repository.FieldDefinitionRepository;
import com.example.dynamicform.template.repository.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TemplateMatcher {
    private final TemplateVersionRepository versions;
    private final FieldDefinitionRepository fields;

    @Transactional(readOnly = true)
    public Optional<MatchResult> bestMatch(SuggestedForm form) {
        Set<String> suggestedCodes = new HashSet<>();
        Set<String> suggestedLabels = new HashSet<>();
        form.groups().forEach(group -> group.fields().stream().filter(DocumentAnalysisDtos.SuggestedField::selected).forEach(field -> {
            suggestedCodes.add(normalize(field.fieldCode()));
            suggestedLabels.add(normalize(field.label()));
        }));
        if (suggestedCodes.isEmpty()) return Optional.empty();

        MatchResult best = null;
        for (TemplateVersion version : versions.findByStatus(VersionStatus.PUBLISHED)) {
            List<FieldDefinition> definitions = fields.findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId());
            Set<String> codes = new HashSet<>();
            Set<String> labels = new HashSet<>();
            definitions.forEach(field -> {
                codes.add(normalize(field.getFieldCode()));
                labels.add(normalize(field.getLabel()));
            });
            double codeScore = jaccard(suggestedCodes, codes);
            double labelScore = jaccard(suggestedLabels, labels);
            double score = codeScore * 0.7 + labelScore * 0.3;
            if (best == null || score > best.confidence().doubleValue()) {
                best = new MatchResult(version, BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
            }
        }
        return best == null || best.confidence().compareTo(new BigDecimal("0.35")) < 0 ? Optional.empty() : Optional.of(best);
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private String normalize(String value) {
        if (value == null) return "";
        String replaced = value.replace('đ', 'd').replace('Đ', 'D');
        return Normalizer.normalize(replaced, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    public record MatchResult(TemplateVersion version, BigDecimal confidence) {}
}
