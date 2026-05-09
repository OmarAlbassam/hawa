package com.hawa.hawa_backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AnalysisStartupRecoveryTest {

    @Autowired private AnalysisStartupRecovery recovery;
    @Autowired private ReportRepository reportRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user;
    private Brand brand;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feedback");
        jdbcTemplate.execute("DELETE FROM review");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM keyword");
        jdbcTemplate.execute("DELETE FROM brand");
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("DELETE FROM \"user\"");
        jdbcTemplate.execute("DELETE FROM company");

        Company company = new Company();
        company.setCompanyName("Acme");
        company = companyRepository.save(company);

        user = userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email("user@example.com")
                .password("x")
                .company(company)
                .role(UserRoleEnum.MARKETING_USER)
                .build());

        brand = brandRepository.save(Brand.builder()
                .brandName("Nike")
                .company(company)
                .industry("Sportswear")
                .build());
    }

    @Test
    void shouldFlipNonTerminalReportsToFailed_whenStartupRecoveryRuns() {
        Long pendingId = seedReport(ReportStatusEnum.PENDING);
        Long queuedId = seedReport(ReportStatusEnum.QUEUED);
        Long processingId = seedReport(ReportStatusEnum.PROCESSING);
        Long completedId = seedReport(ReportStatusEnum.COMPLETED);
        Long alreadyFailedId = seedReport(ReportStatusEnum.FAILED);

        recovery.run(null);

        assertReportFailedWithRecoveryReason(pendingId);
        assertReportFailedWithRecoveryReason(queuedId);
        assertReportFailedWithRecoveryReason(processingId);

        Report completed = reportRepository.findById(completedId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ReportStatusEnum.COMPLETED);
        assertThat(completed.getFailureReason()).isNull();

        Report alreadyFailed = reportRepository.findById(alreadyFailedId).orElseThrow();
        assertThat(alreadyFailed.getStatus()).isEqualTo(ReportStatusEnum.FAILED);
        // pre-existing failure reason should not be overwritten
        assertThat(alreadyFailed.getFailureReason()).isEqualTo("original reason");
    }

    @Test
    void shouldBeNoOp_whenNoStuckReportsExist() {
        seedReport(ReportStatusEnum.COMPLETED);

        recovery.run(null);

        Map<ReportStatusEnum, Long> counts = Map.of(
                ReportStatusEnum.COMPLETED, reportRepository.countByStatus(ReportStatusEnum.COMPLETED),
                ReportStatusEnum.FAILED, reportRepository.countByStatus(ReportStatusEnum.FAILED));
        assertThat(counts.get(ReportStatusEnum.COMPLETED)).isEqualTo(1L);
        assertThat(counts.get(ReportStatusEnum.FAILED)).isZero();
    }

    private Long seedReport(ReportStatusEnum status) {
        Report report = Report.builder()
                .user(user)
                .brand(brand)
                .dataSource(DataSourceEnum.REDDIT)
                .status(status)
                .build();
        if (status == ReportStatusEnum.FAILED) {
            report.setFailureReason("original reason");
            report.setFinishedAt(LocalDateTime.now().minusHours(1));
        }
        return reportRepository.save(report).getReportId();
    }

    private void assertReportFailedWithRecoveryReason(Long reportId) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ReportStatusEnum.FAILED);
        assertThat(report.getFailureReason()).isEqualTo(AnalysisStartupRecovery.FAILURE_REASON);
        assertThat(report.getFinishedAt()).isNotNull();
    }
}
