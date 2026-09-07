package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.type.InterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findAllByApplicantOrderByCreateTimeDesc(Applicant applicant);
    Optional<InterviewSession> findByInterviewSessionId(UUID interviewSessionId);
    Optional<InterviewSession> findFirstByApplicantAndSessionStatusInOrderByCreateTimeDesc(
            Applicant applicant, Collection<InterviewSessionStatus> statuses);
}
