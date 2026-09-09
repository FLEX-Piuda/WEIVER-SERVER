package com.weiver.jobposting.repository;

import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("""
        SELECT jp
        FROM JobPosting jp
        WHERE jp.company.companyId = (
            SELECT c.companyId
            FROM Company c
            WHERE c.publicId = :publicId
        )
        """)
    Slice<JobPosting> findByCompany_PublicId(@Param("publicId") String publicId, Pageable pageable);

    @Query("""
        SELECT jp
        FROM JobPosting jp
        WHERE jp.company.companyId = (
            SELECT c.companyId
            FROM Company c
            WHERE c.publicId = :publicId
        )
        AND jp.status = :status
        """)
    Slice<JobPosting> findByCompany_PublicIdAndStatus(
            @Param("publicId") String publicId,
            @Param("status") JobPostingStatus status,
            Pageable pageable
    );

    boolean existsByJdIdAndCompany_PublicId(Long jdId, String publicId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPosting j SET j.status = 'CLOSED' " +
            "WHERE j.status = 'ACTIVE' AND j.deadline < :now")
    int closeExpiredJobPostings(@Param("now") LocalDate now); // deadline 기준으로 status 변경
}
