package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.type.InterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findAllByApplicantOrderByCreateTimeDesc(Applicant applicant);
    Optional<InterviewSession> findByInterviewSessionId(UUID interviewSessionId);
    @Query("select s.createTime from InterviewSession s " +
           "where s.applicant = :applicant and s.sessionStatus in :statuses and s.createTime > :createTimeAfter " +
           "order by s.createTime desc")
    List<LocalDateTime> findActiveSessionCreateTimes(@Param("applicant") Applicant applicant,
                                                     @Param("statuses") Collection<InterviewSessionStatus> statuses,
                                                     @Param("createTimeAfter") LocalDateTime createTimeAfter);
}
