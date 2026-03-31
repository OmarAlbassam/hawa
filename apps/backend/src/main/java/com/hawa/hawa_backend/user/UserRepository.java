package com.hawa.hawa_backend.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.enums.UserRoleEnum;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(value = """
            SELECT u FROM User u JOIN FETCH u.company
            WHERE (:role IS NULL OR u.role = :role)
            AND (:companyId IS NULL OR u.company.companyId = :companyId)
            AND (:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE (:role IS NULL OR u.role = :role)
            AND (:companyId IS NULL OR u.company.companyId = :companyId)
            AND (:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> findAllWithFilters(
            @Param("role") UserRoleEnum role,
            @Param("companyId") Long companyId,
            @Param("search") String search,
            Pageable pageable);
}
