package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.example.dynamicform.security.FunctionGuard;
import com.example.dynamicform.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates/{templateCode}/records")
@RequiredArgsConstructor
public class DynamicDataController {
    private final DynamicDataService service;
    private final FunctionGuard functionGuard;
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public DynamicRecord create(@PathVariable String templateCode, @Valid @RequestBody CreateRecordRequest request) { throw legacyWriteDisabled(); }
    @GetMapping("/{id}") public DynamicRecord get(@PathVariable String templateCode, @PathVariable UUID id) { functionGuard.require("RECORD_EXPORT"); return service.get(templateCode, id); }
    @PutMapping("/{id}") public DynamicRecord update(@PathVariable String templateCode, @PathVariable UUID id, @Valid @RequestBody UpdateRecordRequest request) { throw legacyWriteDisabled(); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String templateCode, @PathVariable UUID id, @RequestParam(required = false) Long rowVersion) { throw legacyWriteDisabled(); }
    @PostMapping("/search") public SearchPage search(@PathVariable String templateCode, @Valid @RequestBody SearchRequest request) { functionGuard.require("RECORD_EXPORT"); return service.search(templateCode, request); }

    private ApiException legacyWriteDisabled() {
        return ApiException.conflict("Dữ liệu nghiệp vụ chỉ được cập nhật từ hồ sơ thuộc một đợt được giao. Hãy thao tác tại mục Việc của tôi.");
    }
}
