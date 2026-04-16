package com.hawa.hawa_backend.brand;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.brand.dto.BrandDetailResponse;
import com.hawa.hawa_backend.brand.dto.BrandDetailResponse.KeywordInfo;
import com.hawa.hawa_backend.brand.dto.BrandSummaryResponse;
import com.hawa.hawa_backend.brand.dto.CreateKeywordRequest;
import com.hawa.hawa_backend.brand.dto.KeywordResponse;
import com.hawa.hawa_backend.brand.dto.UpdateKeywordRequest;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    private final AuthenticatedUserService authenticatedUserService;
    private final BrandRepository brandRepository;
    private final KeywordRepository keywordRepository;

    @Transactional(readOnly = true)
    public Page<BrandSummaryResponse> listBrands(Pageable pageable) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Listing brands for companyId={}", companyId);

        Page<Brand> brands = brandRepository.findByCompanyCompanyId(companyId, pageable);

        List<Long> brandIds = brands.getContent().stream()
                .map(Brand::getBrandId)
                .toList();

        Map<Long, Long> keywordCounts = brandIds.isEmpty()
                ? Map.of()
                : keywordRepository.countByBrandIds(brandIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (Long) row[1]));

        return brands.map(brand -> new BrandSummaryResponse(
                brand.getBrandId(),
                brand.getBrandName(),
                brand.getIndustry(),
                brand.getStatusIndicator(),
                keywordCounts.getOrDefault(brand.getBrandId(), 0L),
                brand.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BrandDetailResponse getBrand(Long brandId) {
        Brand brand = getCompanyBrand(brandId);

        List<KeywordInfo> keywords = keywordRepository.findAllByBrandBrandId(brandId)
                .stream()
                .map(this::toKeywordInfo)
                .toList();

        return new BrandDetailResponse(
                brand.getBrandId(),
                brand.getBrandName(),
                brand.getIndustry(),
                brand.getStatusIndicator(),
                keywords,
                brand.getCreatedAt(),
                brand.getUpdatedAt());
    }

    // ---- Keyword CRUD ----

    @Transactional
    public KeywordResponse createKeyword(Long brandId, CreateKeywordRequest request) {
        Brand brand = getCompanyBrand(brandId);

        Keyword keyword = Keyword.builder()
                .brand(brand)
                .keyword(request.keyword())
                .keywordType(request.keywordType())
                .build();
        keyword = keywordRepository.save(keyword);
        log.info("Keyword created: '{}' for brand={}", keyword.getKeyword(), brand.getBrandName());
        return toKeywordResponse(keyword);
    }

    @Transactional(readOnly = true)
    public Page<KeywordResponse> listKeywords(Long brandId, Pageable pageable) {
        getCompanyBrand(brandId);
        return keywordRepository.findByBrandBrandId(brandId, pageable)
                .map(this::toKeywordResponse);
    }

    @Transactional
    public KeywordResponse updateKeyword(Long brandId, Long keywordId, UpdateKeywordRequest request) {
        getCompanyBrand(brandId);
        Keyword keyword = getKeywordForBrand(brandId, keywordId);

        keyword.setKeyword(request.keyword());
        keyword.setKeywordType(request.keywordType());
        keyword = keywordRepository.save(keyword);
        log.info("Keyword updated: id={}", keyword.getKeywordId());
        return toKeywordResponse(keyword);
    }

    @Transactional
    public void deleteKeyword(Long brandId, Long keywordId) {
        getCompanyBrand(brandId);
        Keyword keyword = getKeywordForBrand(brandId, keywordId);
        keywordRepository.delete(keyword);
        log.info("Keyword deleted: id={}", keywordId);
    }

    // ---- Helpers ----

    private Brand getCompanyBrand(Long brandId) {
        Long companyId = authenticatedUserService.getCompanyId();
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + brandId));
        if (!brand.getCompany().getCompanyId().equals(companyId)) {
            throw new BadRequestException("Brand does not belong to your company");
        }
        return brand;
    }

    private Keyword getKeywordForBrand(Long brandId, Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyword not found with id: " + keywordId));
        if (!keyword.getBrand().getBrandId().equals(brandId)) {
            throw new ResourceNotFoundException("Keyword " + keywordId + " does not belong to brand " + brandId);
        }
        return keyword;
    }

    private KeywordInfo toKeywordInfo(Keyword keyword) {
        return new KeywordInfo(
                keyword.getKeywordId(),
                keyword.getKeyword(),
                keyword.getKeywordType());
    }

    private KeywordResponse toKeywordResponse(Keyword keyword) {
        return new KeywordResponse(
                keyword.getKeywordId(),
                keyword.getBrand().getBrandId(),
                keyword.getKeyword(),
                keyword.getKeywordType(),
                keyword.getCreatedAt(),
                keyword.getUpdatedAt());
    }
}
