package com.hawa.hawa_backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            if (userRepository.count() > 0) {
                log.info("Database already has users — skipping seed");
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check user count (tables may not exist yet) — skipping seed");
            return;
        }

        Company hawa = new Company();
        hawa.setCompanyName("Hawa");
        hawa = companyRepository.save(hawa);
        log.info("Created company: Hawa (id={})", hawa.getCompanyId());

        Company almarai = new Company();
        almarai.setCompanyName("Almarai");
        almarai = companyRepository.save(almarai);
        log.info("Created company: Almarai (id={})", almarai.getCompanyId());

        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@hawa.com")
                .password(passwordEncoder.encode("Admin123"))
                .company(hawa)
                .role(UserRoleEnum.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Created admin user: admin@hawa.com (password: Admin123)");
    }
}
