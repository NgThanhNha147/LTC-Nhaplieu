package com.example.dynamicform.template.controller;

import com.example.dynamicform.common.ApiException;
import com.example.dynamicform.template.domain.VersionStatus;
import com.example.dynamicform.template.dto.TemplateDtos.FormMetadataResponse;
import com.example.dynamicform.template.repository.TemplateVersionRepository;
import com.example.dynamicform.template.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
public class FormMetadataController {
    private final TemplateVersionRepository versions;
    private final MetadataService metadata;

    @GetMapping("/{templateCode}")
    public FormMetadataResponse published(@PathVariable String templateCode) {
        var version = versions.findFirstByTemplateCodeIgnoreCaseAndStatusOrderByVersionNoDesc(templateCode, VersionStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Biểu mẫu chưa được đưa vào sử dụng. Hãy kiểm tra cấu hình, tạo bảng dữ liệu và chọn 'Đưa vào sử dụng'."));
        return metadata.formMetadata(version.getId());
    }

    @GetMapping("/{templateCode}/versions/{versionNo}")
    public FormMetadataResponse version(@PathVariable String templateCode, @PathVariable Integer versionNo) {
        var version = versions.findByTemplateCodeIgnoreCaseAndVersionNo(templateCode, versionNo)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản biểu mẫu."));
        return metadata.formMetadata(version.getId());
    }
}
