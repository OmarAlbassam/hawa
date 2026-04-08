package com.hawa.hawa_backend.config;

import org.springframework.beans.factory.annotation.Value;
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

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:admin@hawa.com}")
    private String adminEmail;

    @Value("${admin.seed.password:Admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(UserRoleEnum.ADMIN)) {
            log.info("Admin user already exists — skipping seed");
            return;
        }

        Company company = companyRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Company c = new Company();
                    c.setCompanyName("Hawa");
                    c = companyRepository.save(c);
                    log.info("Seeded default company: Hawa (id={})", c.getCompanyId());
                    return c;
                });

        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .company(company)
                .role(UserRoleEnum.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Seeded admin user: {}", adminEmail);
    }
}
