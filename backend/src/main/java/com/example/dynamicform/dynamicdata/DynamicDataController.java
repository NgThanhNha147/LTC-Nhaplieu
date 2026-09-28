package com.example.dynamicform.dynamicdata;

import com.example.dynamicform.dynamicdata.dto.DynamicDataDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates/{templateCode}/records")
@RequiredArgsConstructor
public class DynamicDataController {
    private final DynamicDataService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public DynamicRecord create(@PathVariable String templateCode, @Valid @RequestBody CreateRecordRequest request) { return service.create(templateCode, request); }
    @GetMapping("/{id}") public DynamicRecord get(@PathVariable String templateCode, @PathVariable UUID id) { return service.get(templateCode, id); }
    @PutMapping("/{id}") public DynamicRecord update(@PathVariable String templateCode, @PathVariable UUID id, @Valid @RequestBody UpdateRecordRequest request) { return service.update(templateCode, id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String templateCode, @PathVariable UUID id, @RequestParam(required = false) Long rowVersion) { service.delete(templateCode, id, rowVersion); }
    @PostMapping("/search") public SearchPage search(@PathVariable String templateCode, @Valid @RequestBody SearchRequest request) { return service.search(templateCode, request); }
}
