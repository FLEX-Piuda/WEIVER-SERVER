package com.weiver.interview.repository;

import com.weiver.interview.domain.InterviewErrorReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewErrorReportRepository extends JpaRepository<InterviewErrorReport, Long> {
}
