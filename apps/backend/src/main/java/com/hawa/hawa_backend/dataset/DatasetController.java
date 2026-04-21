package com.hawa.hawa_backend.dataset;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private static final String TEMPLATE_FILENAME = "hawa-dataset-template.csv";
    private static final String TEMPLATE_BODY = """
            text,url,language
            "Example post text","https://example.com/post/1","EN"
            """;

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] bytes = TEMPLATE_BODY.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + TEMPLATE_FILENAME + "\"")
                .body(bytes);
    }
}
