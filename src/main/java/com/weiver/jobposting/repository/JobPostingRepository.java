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

    Slice<JobPosting> findByCompany_PublicId(String publicId, Pageable pageable);

    Slice<JobPosting> findByCompany_PublicIdAndStatus(String publicId, JobPostingStatus status, Pageable pageable);

    boolean existsByJdIdAndCompany_PublicId(Long jdId, String publicId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPosting j SET j.status = 'CLOSED' " +
            "WHERE j.status = 'ACTIVE' AND j.deadline < :now")
    int closeExpiredJobPostings(@Param("now") LocalDate now); // deadline 기준으로 status 변경
}
