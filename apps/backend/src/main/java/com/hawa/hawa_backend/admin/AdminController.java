package com.hawa.hawa_backend.admin;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.admin.dto.AdminUserResponse;
import com.hawa.hawa_backend.admin.dto.BrandResponse;
import com.hawa.hawa_backend.admin.dto.CompanyResponse;
import com.hawa.hawa_backend.admin.dto.CreateBrandRequest;
import com.hawa.hawa_backend.admin.dto.CreateCompanyRequest;
import com.hawa.hawa_backend.admin.dto.CreateKeywordRequest;
import com.hawa.hawa_backend.admin.dto.CreateUserRequest;
import com.hawa.hawa_backend.admin.dto.KeywordResponse;
import com.hawa.hawa_backend.admin.dto.ReportedReviewResponse;
import com.hawa.hawa_backend.admin.dto.SystemAnalyticsResponse;
import com.hawa.hawa_backend.admin.dto.UpdateBrandRequest;
import com.hawa.hawa_backend.admin.dto.UpdateCompanyRequest;
import com.hawa.hawa_backend.admin.dto.UpdateKeywordRequest;
import com.hawa.hawa_backend.admin.dto.UpdateUserRequest;
import com.hawa.hawa_backend.enums.UserRoleEnum;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // --- User Management ---

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createUser(request));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @RequestParam(required = false) UserRoleEnum role,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.listUsers(role, companyId, search, pageable));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUser(userId));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminService.updateUser(userId, request));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // --- Company Management ---

    @PostMapping("/companies")
    public ResponseEntity<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createCompany(request));
    }

    @GetMapping("/companies")
    public ResponseEntity<Page<CompanyResponse>> listCompanies(Pageable pageable) {
        return ResponseEntity.ok(adminService.listCompanies(pageable));
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(adminService.getCompany(companyId));
    }

    @PutMapping("/companies/{companyId}")
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable Long companyId,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(adminService.updateCompany(companyId, request));
    }

    @DeleteMapping("/companies/{companyId}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long companyId) {
        adminService.deleteCompany(companyId);
        return ResponseEntity.noContent().build();
    }

    // --- Brand Management ---

    @PostMapping("/brands")
    public ResponseEntity<BrandResponse> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createBrand(request));
    }

    @GetMapping("/brands")
    public ResponseEntity<Page<BrandResponse>> listBrands(
            @RequestParam(required = false) Long companyId,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.listBrands(companyId, pageable));
    }

    @GetMapping("/brands/{brandId}")
    public ResponseEntity<BrandResponse> getBrand(@PathVariable Long brandId) {
        return ResponseEntity.ok(adminService.getBrand(brandId));
    }

    @PutMapping("/brands/{brandId}")
    public ResponseEntity<BrandResponse> updateBrand(
            @PathVariable Long brandId,
            @Valid @RequestBody UpdateBrandRequest request) {
        return ResponseEntity.ok(adminService.updateBrand(brandId, request));
    }

    @DeleteMapping("/brands/{brandId}")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long brandId) {
        adminService.deleteBrand(brandId);
        return ResponseEntity.noContent().build();
    }

    // --- Keyword Management ---

    @PostMapping("/brands/{brandId}/keywords")
    public ResponseEntity<KeywordResponse> createKeyword(
            @PathVariable Long brandId,
            @Valid @RequestBody CreateKeywordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createKeyword(brandId, request));
    }

    @GetMapping("/brands/{brandId}/keywords")
    public ResponseEntity<Page<KeywordResponse>> listKeywords(
            @PathVariable Long brandId,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.listKeywords(brandId, pageable));
    }

    @GetMapping("/brands/{brandId}/keywords/{keywordId}")
    public ResponseEntity<KeywordResponse> getKeyword(
            @PathVariable Long brandId,
            @PathVariable Long keywordId) {
        return ResponseEntity.ok(adminService.getKeyword(brandId, keywordId));
    }

    @PutMapping("/brands/{brandId}/keywords/{keywordId}")
    public ResponseEntity<KeywordResponse> updateKeyword(
            @PathVariable Long brandId,
            @PathVariable Long keywordId,
            @Valid @RequestBody UpdateKeywordRequest request) {
        return ResponseEntity.ok(adminService.updateKeyword(brandId, keywordId, request));
    }

    @DeleteMapping("/brands/{brandId}/keywords/{keywordId}")
    public ResponseEntity<Void> deleteKeyword(
            @PathVariable Long brandId,
            @PathVariable Long keywordId) {
        adminService.deleteKeyword(brandId, keywordId);
        return ResponseEntity.noContent().build();
    }

    // --- System Analytics ---

    @GetMapping("/analytics")
    public ResponseEntity<SystemAnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalytics());
    }

    // --- Reported Reviews ---

    @GetMapping("/reported-reviews")
    public ResponseEntity<Page<ReportedReviewResponse>> getReportedReviews(Pageable pageable) {
        return ResponseEntity.ok(adminService.getReportedReviews(pageable));
    }
}
