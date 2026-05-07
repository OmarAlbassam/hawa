package com.hawa.hawa_backend.admin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import com.hawa.hawa_backend.admin.dto.AdminUserResponse;
import com.hawa.hawa_backend.admin.dto.BrandResponse;
import com.hawa.hawa_backend.admin.dto.CompanyResponse;
import com.hawa.hawa_backend.admin.dto.CreateBrandRequest;
import com.hawa.hawa_backend.admin.dto.CreateCompanyRequest;
import com.hawa.hawa_backend.admin.dto.CreateUserRequest;
import com.hawa.hawa_backend.admin.dto.ReportedReviewResponse;
import com.hawa.hawa_backend.admin.dto.SystemAnalyticsResponse;
import com.hawa.hawa_backend.admin.dto.UpdateBrandRequest;
import com.hawa.hawa_backend.admin.dto.UpdateCompanyRequest;
import com.hawa.hawa_backend.admin.dto.UpdateUserRequest;
import com.hawa.hawa_backend.auth.CustomUserDetails;
import com.hawa.hawa_backend.admin.dto.CreateKeywordRequest;
import com.hawa.hawa_backend.admin.dto.KeywordResponse;
import com.hawa.hawa_backend.admin.dto.UpdateKeywordRequest;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.DuplicateEmailException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.feedback.Feedback;
import com.hawa.hawa_backend.feedback.FeedbackRepository;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.review.Review;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final FeedbackRepository feedbackRepository;
    private final BrandRepository brandRepository;
    private final KeywordRepository keywordRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered");
        }

        Company company = resolveCompanyForRole(request.role(), request.companyId());

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .company(company)
                .role(request.role())
                .build();

        user = userRepository.save(user);
        log.info("Admin created user: {}", user.getEmail());
        return toAdminUserResponse(user);
    }

    private Company resolveCompanyForRole(UserRoleEnum role, Long companyId) {
        if (role == UserRoleEnum.ADMIN) {
            if (companyId != null) {
                throw new BadRequestException("ADMIN users must not be assigned to a company");
            }
            return null;
        }
        if (companyId == null) {
            throw new BadRequestException("Non-admin users must belong to a company");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + companyId));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));
        return toAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UserRoleEnum role, Long companyId,
                                              String search, Pageable pageable) {
        String roleStr = role != null ? role.name() : null;
        Pageable nativePageable = toNativeSortPageable(pageable);
        log.info("listUsers called — role={}, companyId={}, search={}, pageable={}",
                roleStr, companyId, search, nativePageable);
        Page<AdminUserResponse> result = userRepository.findAllWithFilters(roleStr, companyId, search, nativePageable)
                .map(this::toAdminUserResponse);
        log.info("listUsers returned {} results (total={})", result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    private Pageable toNativeSortPageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        var mappedOrders = pageable.getSort().stream()
                .map(order -> {
                    String mapped = switch (order.getProperty()) {
                        case "createdAt" -> "created_at";
                        case "updatedAt" -> "updated_at";
                        case "firstName" -> "first_name";
                        case "lastName" -> "last_name";
                        case "companyId" -> "company_id";
                        case "userId" -> "user_id";
                        default -> order.getProperty();
                    };
                    return new Sort.Order(order.getDirection(), mapped);
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(mappedOrders));
    }

    @Transactional
    public AdminUserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered");
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());

        if (request.role() != null) {
            user.setRole(request.role());
        }

        UserRoleEnum effectiveRole = user.getRole();
        if (effectiveRole == UserRoleEnum.ADMIN) {
            if (request.companyId() != null) {
                throw new BadRequestException("ADMIN users must not be assigned to a company");
            }
            user.setCompany(null);
        } else if (request.companyId() != null) {
            Company company = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Company not found with id: " + request.companyId()));
            user.setCompany(company);
        } else if (user.getCompany() == null) {
            throw new BadRequestException("Non-admin users must belong to a company");
        }

        user = userRepository.save(user);
        log.info("Admin updated user: {}", user.getEmail());
        return toAdminUserResponse(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        Long currentUserId = getCurrentUserId();
        if (user.getUserId().equals(currentUserId)) {
            throw new BadRequestException("Cannot delete your own account");
        }

        userRepository.delete(user);
        log.info("Admin deleted user: {}", user.getEmail());
    }

    @Transactional(readOnly = true)
    public SystemAnalyticsResponse getAnalytics() {
        ReportRepository.AnalyticsProjection a = reportRepository.loadAnalytics();

        Map<ReportStatusEnum, Long> reportsByStatus = new EnumMap<>(ReportStatusEnum.class);
        for (ReportStatusEnum status : ReportStatusEnum.values()) {
            reportsByStatus.put(status, 0L);
        }
        if (a.getStatusCountsJson() != null) {
            Map<String, Long> raw = objectMapper.readValue(
                    a.getStatusCountsJson(),
                    new tools.jackson.core.type.TypeReference<Map<String, Long>>() {});
            for (Map.Entry<String, Long> entry : raw.entrySet()) {
                reportsByStatus.put(ReportStatusEnum.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return new SystemAnalyticsResponse(
                a.getUsersCount(),
                a.getCompaniesCount(),
                a.getReportsCount(),
                reportsByStatus,
                a.getPostsCount());
    }

    @Transactional(readOnly = true)
    public Page<ReportedReviewResponse> getReportedReviews(Pageable pageable) {
        return feedbackRepository.findAllWithFullContext(pageable)
                .map(this::toReportedReviewResponse);
    }

    // ---- Company Management ----

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        Company company = new Company();
        company.setCompanyName(request.companyName());
        company = companyRepository.save(company);
        log.info("Admin created company: {}", company.getCompanyName());
        return toCompanyResponse(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + companyId));
        return toCompanyResponse(company);
    }

    @Transactional(readOnly = true)
    public Page<CompanyResponse> listCompanies(Pageable pageable) {
        return companyRepository.findAll(pageable)
                .map(this::toCompanyResponse);
    }

    @Transactional
    public CompanyResponse updateCompany(Long companyId, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + companyId));
        company.setCompanyName(request.companyName());
        company = companyRepository.save(company);
        log.info("Admin updated company: {}", company.getCompanyName());
        return toCompanyResponse(company);
    }

    @Transactional
    public void deleteCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + companyId));
        companyRepository.delete(company);
        log.info("Admin deleted company: {}", company.getCompanyName());
    }

    // ---- Brand Management ----

    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + request.companyId()));

        Brand brand = Brand.builder()
                .brandName(request.brandName())
                .company(company)
                .industry(request.industry())
                .build();
        brand = brandRepository.save(brand);
        log.info("Admin created brand: {}", brand.getBrandName());
        return toBrandResponse(brand, 0L);
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + brandId));
        long keywordsCount = keywordRepository.countByBrandBrandId(brandId);
        return toBrandResponse(brand, keywordsCount);
    }

    @Transactional(readOnly = true)
    public Page<BrandResponse> listBrands(Long companyId, Pageable pageable) {
        Page<Brand> brands;
        if (companyId != null) {
            brands = brandRepository.findByCompanyCompanyId(companyId, pageable);
        } else {
            brands = brandRepository.findAllWithCompany(pageable);
        }

        List<Long> brandIds = brands.getContent().stream()
                .map(Brand::getBrandId)
                .toList();
        Map<Long, Long> keywordCounts = getKeywordCountsByBrandIds(brandIds);

        return brands.map(brand -> toBrandResponse(brand,
                keywordCounts.getOrDefault(brand.getBrandId(), 0L)));
    }

    @Transactional
    public BrandResponse updateBrand(Long brandId, UpdateBrandRequest request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + brandId));

        brand.setBrandName(request.brandName());

        if (request.industry() != null) {
            brand.setIndustry(request.industry());
        }

        if (request.companyId() != null) {
            Company company = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Company not found with id: " + request.companyId()));
            brand.setCompany(company);
        }

        brand = brandRepository.save(brand);
        log.info("Admin updated brand: {}", brand.getBrandName());
        long keywordsCount = keywordRepository.countByBrandBrandId(brandId);
        return toBrandResponse(brand, keywordsCount);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + brandId));
        brandRepository.delete(brand);
        log.info("Admin deleted brand: {}", brand.getBrandName());
    }

    // ---- Keyword Management ----

    @Transactional
    public KeywordResponse createKeyword(Long brandId, CreateKeywordRequest request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + brandId));

        Keyword keyword = Keyword.builder()
                .brand(brand)
                .keyword(request.keyword())
                .keywordType(request.keywordType())
                .build();
        keyword = keywordRepository.save(keyword);
        log.info("Admin created keyword '{}' for brand: {}", keyword.getKeyword(), brand.getBrandName());
        return toKeywordResponse(keyword);
    }

    @Transactional(readOnly = true)
    public Page<KeywordResponse> listKeywords(Long brandId, Pageable pageable) {
        if (!brandRepository.existsById(brandId)) {
            throw new ResourceNotFoundException("Brand not found with id: " + brandId);
        }
        return keywordRepository.findByBrandBrandId(brandId, pageable)
                .map(this::toKeywordResponse);
    }

    @Transactional(readOnly = true)
    public KeywordResponse getKeyword(Long brandId, Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Keyword not found with id: " + keywordId));
        if (!keyword.getBrand().getBrandId().equals(brandId)) {
            throw new ResourceNotFoundException(
                    "Keyword " + keywordId + " does not belong to brand " + brandId);
        }
        return toKeywordResponse(keyword);
    }

    @Transactional
    public KeywordResponse updateKeyword(Long brandId, Long keywordId, UpdateKeywordRequest request) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Keyword not found with id: " + keywordId));
        if (!keyword.getBrand().getBrandId().equals(brandId)) {
            throw new ResourceNotFoundException(
                    "Keyword " + keywordId + " does not belong to brand " + brandId);
        }
        keyword.setKeyword(request.keyword());
        keyword.setKeywordType(request.keywordType());
        keyword = keywordRepository.save(keyword);
        log.info("Admin updated keyword: {}", keyword.getKeywordId());
        return toKeywordResponse(keyword);
    }

    @Transactional
    public void deleteKeyword(Long brandId, Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Keyword not found with id: " + keywordId));
        if (!keyword.getBrand().getBrandId().equals(brandId)) {
            throw new ResourceNotFoundException(
                    "Keyword " + keywordId + " does not belong to brand " + brandId);
        }
        keywordRepository.delete(keyword);
        log.info("Admin deleted keyword: {}", keywordId);
    }

    // ---- Mappers ----

    private CompanyResponse toCompanyResponse(Company company) {
        return new CompanyResponse(
                company.getCompanyId(),
                company.getCompanyName(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private BrandResponse toBrandResponse(Brand brand, long keywordsCount) {
        return new BrandResponse(
                brand.getBrandId(),
                brand.getBrandName(),
                new AdminUserResponse.CompanyInfo(
                        brand.getCompany().getCompanyId(),
                        brand.getCompany().getCompanyName()),
                brand.getIndustry(),
                brand.getStatusIndicator(),
                keywordsCount,
                brand.getCreatedAt(),
                brand.getUpdatedAt());
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

    private Map<Long, Long> getKeywordCountsByBrandIds(List<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return keywordRepository.countByBrandIds(brandIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        AdminUserResponse.CompanyInfo companyInfo = user.getCompany() == null ? null
                : new AdminUserResponse.CompanyInfo(
                        user.getCompany().getCompanyId(),
                        user.getCompany().getCompanyName());
        return new AdminUserResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                companyInfo,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private ReportedReviewResponse toReportedReviewResponse(Feedback feedback) {
        Review review = feedback.getReview();
        User reporter = feedback.getUser();
        com.hawa.hawa_backend.post.Post post = review.getPost();

        return new ReportedReviewResponse(
                feedback.getFeedbackId(),
                feedback.getBrief(),
                new ReportedReviewResponse.ReviewInfo(
                        review.getReviewId(),
                        review.getScore(),
                        review.getLlmScore(),
                        review.getEmotion(),
                        review.getAspect()),
                new ReportedReviewResponse.PostInfo(
                        post.getPostId(),
                        post.getPostText(),
                        post.getPostUrl(),
                        post.getLanguage(),
                        post.getCreatedAt()),
                post.getReport().getBrand().getBrandName(),
                post.getReport().getBrand().getCompany().getCompanyName(),
                new ReportedReviewResponse.ReporterInfo(
                        reporter.getUserId(),
                        reporter.getFirstName(),
                        reporter.getLastName(),
                        reporter.getEmail()));
    }

    private Long getCurrentUserId() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }
}
