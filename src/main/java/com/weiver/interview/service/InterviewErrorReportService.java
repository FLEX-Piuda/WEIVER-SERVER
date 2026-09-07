package com.weiver.interview.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.domain.InterviewErrorReport;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.request.InterviewErrorReportRequestDTO;
import com.weiver.interview.repository.InterviewErrorReportRepository;
import com.weiver.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class InterviewErrorReportService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewErrorReportRepository interviewErrorReportRepository;

    public void createErrorReport(UUID interviewSessionId, String applicantPublicId,
                                  InterviewErrorReportRequestDTO request) {
        InterviewSession session = interviewSessionRepository.findByInterviewSessionId(interviewSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        if (!Objects.equals(session.getApplicant().getPublicId(), applicantPublicId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        interviewErrorReportRepository.save(InterviewErrorReport.of(session, request.content()));
    }
}
