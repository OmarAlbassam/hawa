package com.hawa.hawa_backend.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(value = """
            SELECT u.* FROM "user" u
            JOIN company c ON c.company_id = u.company_id
            WHERE (CAST(:role AS user_role) IS NULL OR u.role = CAST(:role AS user_role))
            AND (CAST(:companyId AS BIGINT) IS NULL OR c.company_id = CAST(:companyId AS BIGINT))
            AND (CAST(:search AS VARCHAR) IS NULL OR (
                LOWER(u.first_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))))
            """,
            countQuery = """
            SELECT COUNT(*) FROM "user" u
            JOIN company c ON c.company_id = u.company_id
            WHERE (CAST(:role AS user_role) IS NULL OR u.role = CAST(:role AS user_role))
            AND (CAST(:companyId AS BIGINT) IS NULL OR c.company_id = CAST(:companyId AS BIGINT))
            AND (CAST(:search AS VARCHAR) IS NULL OR (
                LOWER(u.first_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))))
            """,
            nativeQuery = true)
    Page<User> findAllWithFilters(
            @Param("role") String role,
            @Param("companyId") Long companyId,
            @Param("search") String search,
            Pageable pageable);
}
