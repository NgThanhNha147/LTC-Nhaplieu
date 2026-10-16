package com.example.dynamicform.documentanalysis;

import com.example.dynamicform.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ScanTextRecognitionClient {
    private final ObjectMapper objectMapper;
    @Value("${app.document-analysis.scan-recognition-enabled:true}") private boolean enabled;
    @Value("${app.ocr.service-url:http://localhost:8090}") private String serviceUrl;

    public String extract(Path path) {
        if (!enabled) {
            throw ApiException.badRequest("Tài liệu không có lớp chữ. Hãy bật dịch vụ phân tích tài liệu scan để tiếp tục.");
        }
        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(path));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<String> response = new RestTemplate().postForEntity(serviceUrl + "/ocr/text",
                    new HttpEntity<>(parts, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Dịch vụ phân tích không trả về nội dung.");
            }
            JsonNode body = objectMapper.readTree(response.getBody());
            String text = body.path("text").asText("").trim();
            if (text.isBlank()) throw new IllegalStateException("Không nhận diện được nội dung chữ trong tài liệu.");
            return text;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Không thể phân tích tài liệu scan. Hãy kiểm tra chất lượng file hoặc thử lại sau.");
        }
    }
}
