package com.example.dynamicform.lookup.controller;

import com.example.dynamicform.lookup.dto.LookupDtos.LookupResponse;
import com.example.dynamicform.lookup.service.LookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lookup-sources")
@RequiredArgsConstructor
public class LookupSourceAliasController {
    private final LookupService service;
    @GetMapping public List<LookupResponse> list() { return service.list(); }
}
