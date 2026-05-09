package com.hawa.hawa_backend.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.dashboard.DashboardRepository.DashboardProjection;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse.BrandSummary;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse.DashboardStats;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse.RecentReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final TypeReference<List<BrandSummary>> BRAND_LIST = new TypeReference<>() {};
    private static final TypeReference<List<RecentReport>> REPORT_LIST = new TypeReference<>() {};

    private final AuthenticatedUserService authenticatedUserService;
    private final DashboardRepository dashboardRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(int recentReportsLimit) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Fetching dashboard for companyId={}", companyId);

        DashboardProjection projection = dashboardRepository.loadDashboard(companyId, recentReportsLimit);

        List<BrandSummary> brands = parseList(projection.getBrandsJson(), BRAND_LIST);
        List<RecentReport> recentReports = parseList(projection.getRecentReportsJson(), REPORT_LIST);

        DashboardStats stats = new DashboardStats(
                projection.getReportsCount(),
                projection.getPostsCount(),
                projection.getBrandsCount());

        return new DashboardResponse(brands, recentReports, stats);
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (json == null) {
            return List.of();
        }
        return objectMapper.readValue(json, type);
    }
}
