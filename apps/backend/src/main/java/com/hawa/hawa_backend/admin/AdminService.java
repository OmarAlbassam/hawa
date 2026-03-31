package com.hawa.hawa_backend.admin;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered");
        }

        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + request.companyId()));

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
        return userRepository.findAllWithFilters(role, companyId, search, pageable)
                .map(this::toAdminUserResponse);
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

        if (request.companyId() != null) {
            Company company = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Company not found with id: " + request.companyId()));
            user.setCompany(company);
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
        long totalUsers = userRepository.count();
        long totalCompanies = companyRepository.count();
        long totalReports = reportRepository.count();
        long totalPostsAnalyzed = postRepository.count();

        Map<ReportStatusEnum, Long> reportsByStatus = new EnumMap<>(ReportStatusEnum.class);
        for (ReportStatusEnum status : ReportStatusEnum.values()) {
            reportsByStatus.put(status, reportRepository.countByStatus(status));
        }

        return new SystemAnalyticsResponse(
                totalUsers, totalCompanies, totalReports,
                reportsByStatus, totalPostsAnalyzed);
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
        return toBrandResponse(brand);
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + brandId));
        return toBrandResponse(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandResponse> listBrands(Long companyId, Pageable pageable) {
        if (companyId != null) {
            return brandRepository.findByCompanyCompanyId(companyId, pageable)
                    .map(this::toBrandResponse);
        }
        return brandRepository.findAll(pageable)
                .map(this::toBrandResponse);
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
        return toBrandResponse(brand);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brand not found with id: " + brandId));
        brandRepository.delete(brand);
        log.info("Admin deleted brand: {}", brand.getBrandName());
    }

    // ---- Mappers ----

    private CompanyResponse toCompanyResponse(Company company) {
        return new CompanyResponse(
                company.getCompanyId(),
                company.getCompanyName(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private BrandResponse toBrandResponse(Brand brand) {
        return new BrandResponse(
                brand.getBrandId(),
                brand.getBrandName(),
                new AdminUserResponse.CompanyInfo(
                        brand.getCompany().getCompanyId(),
                        brand.getCompany().getCompanyName()),
                brand.getIndustry(),
                brand.getStatusIndicator(),
                brand.getCreatedAt(),
                brand.getUpdatedAt());
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                new AdminUserResponse.CompanyInfo(
                        user.getCompany().getCompanyId(),
                        user.getCompany().getCompanyName()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private ReportedReviewResponse toReportedReviewResponse(Feedback feedback) {
        Review review = feedback.getReview();
        User reporter = feedback.getUser();

        return new ReportedReviewResponse(
                feedback.getFeedbackId(),
                feedback.getBrief(),
                new ReportedReviewResponse.ReviewInfo(
                        review.getReviewId(),
                        review.getScore(),
                        review.getLlmScore(),
                        review.getEmotion(),
                        review.getAspect(),
                        review.getConfidence()),
                review.getPost().getPostText(),
                review.getPost().getReport().getBrand().getBrandName(),
                review.getPost().getReport().getBrand().getCompany().getCompanyName(),
                new ReportedReviewResponse.ReporterInfo(
                        reporter.getUserId(),
                        reporter.getFirstName(),
                        reporter.getLastName(),
                        reporter.getEmail()));
    }

    private Long getCurrentUserId() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getUser().getUserId();
    }
}
