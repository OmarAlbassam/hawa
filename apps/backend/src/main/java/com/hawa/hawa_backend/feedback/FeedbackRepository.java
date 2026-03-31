package com.hawa.hawa_backend.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Query(value = """
            SELECT f FROM Feedback f
            JOIN FETCH f.review r
            JOIN FETCH r.post p
            JOIN FETCH p.report rpt
            JOIN FETCH rpt.brand b
            JOIN FETCH b.company c
            JOIN FETCH f.user u
            """,
            countQuery = "SELECT COUNT(f) FROM Feedback f")
    Page<Feedback> findAllWithFullContext(Pageable pageable);
}
