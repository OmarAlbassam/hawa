package com.hawa.hawa_backend.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.brand.dto.BrandDetailResponse;
import com.hawa.hawa_backend.brand.dto.BrandStatusIndicatorResponse;
import com.hawa.hawa_backend.brand.dto.BrandSummaryResponse;
import com.hawa.hawa_backend.brand.dto.CreateKeywordRequest;
import com.hawa.hawa_backend.brand.dto.KeywordResponse;
import com.hawa.hawa_backend.brand.dto.UpdateKeywordRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<Page<BrandSummaryResponse>> listBrands(Pageable pageable) {
        return ResponseEntity.ok(brandService.listBrands(pageable));
    }

    @GetMapping("/{brandId}")
    public ResponseEntity<BrandDetailResponse> getBrand(@PathVariable Long brandId) {
        return ResponseEntity.ok(brandService.getBrand(brandId));
    }

    @GetMapping("/{brandId}/status-indicator")
    public ResponseEntity<BrandStatusIndicatorResponse> getStatusIndicator(@PathVariable Long brandId) {
        return ResponseEntity.ok(brandService.getStatusIndicator(brandId));
    }

    // ---- Keyword Management ----

    @PostMapping("/{brandId}/keywords")
    public ResponseEntity<KeywordResponse> createKeyword(
            @PathVariable Long brandId,
            @Valid @RequestBody CreateKeywordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(brandService.createKeyword(brandId, request));
    }

    @GetMapping("/{brandId}/keywords")
    public ResponseEntity<Page<KeywordResponse>> listKeywords(
            @PathVariable Long brandId,
            Pageable pageable) {
        return ResponseEntity.ok(brandService.listKeywords(brandId, pageable));
    }

    @PutMapping("/{brandId}/keywords/{keywordId}")
    public ResponseEntity<KeywordResponse> updateKeyword(
            @PathVariable Long brandId,
            @PathVariable Long keywordId,
            @Valid @RequestBody UpdateKeywordRequest request) {
        return ResponseEntity.ok(brandService.updateKeyword(brandId, keywordId, request));
    }

    @DeleteMapping("/{brandId}/keywords/{keywordId}")
    public ResponseEntity<Void> deleteKeyword(
            @PathVariable Long brandId,
            @PathVariable Long keywordId) {
        brandService.deleteKeyword(brandId, keywordId);
        return ResponseEntity.noContent().build();
    }
}
