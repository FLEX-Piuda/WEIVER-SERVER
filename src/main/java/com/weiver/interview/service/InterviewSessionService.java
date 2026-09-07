package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.response.InterviewRemainingResponse;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InterviewSessionService {

    private static final int TOTAL_COUNT = 1;
    private static final int REAPPLY_DAYS = 31;

    /** 면접이 "완료"된 것으로 간주하는 상태 집합(재지원 대기 대상) */
    private static final Set<InterviewSessionStatus> COMPLETED_STATUSES = EnumSet.of(
            InterviewSessionStatus.FINISHED,
            InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED,
            InterviewSessionStatus.TRANSCRIPT_SAVED,
            InterviewSessionStatus.REPORT_REQUESTED,
            InterviewSessionStatus.REPORT_COMPLETED
    );

    private final InterviewSessionRepository interviewSessionRepository;
    private final ApplicantService applicantService;

    /**
     * 지원자의 가장 최근 면접 세션 transcript 전체 조회
     */
    public List<InterviewTurnDTO> getLatestInterviewTurns(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        return interviewSessionRepository.findAllByApplicantOrderByCreateTimeDesc(applicant).stream()
                .findFirst()
                .map(session -> {
                    List<InterviewTurnDTO> transcript = session.getTranscript();
                    if (transcript == null) {
                        return Collections.<InterviewTurnDTO>emptyList();
                    }
                    return transcript;
                })
                .orElseGet(Collections::emptyList);
    }

    /**
     * 로그인 구직자의 AI 면접 잔여 횟수 및 재지원 D-day 조회
     * 정책: 면접 총 1회 + 완료 후 31일 뒤 재지원 가능
     */
    public InterviewRemainingResponse getRemainingInterview(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        Optional<InterviewSession> completedSession =
                interviewSessionRepository.findFirstByApplicantAndSessionStatusInOrderByCreateTimeDesc(
                        applicant, COMPLETED_STATUSES);

        if (completedSession.isEmpty()) {
            return new InterviewRemainingResponse(TOTAL_COUNT, 1, 0, null);
        }

        LocalDate completedDate = completedSession.get().getCreateTime().toLocalDate();
        LocalDate reapplyDate = completedDate.plusDays(REAPPLY_DAYS);
        LocalDate today = LocalDate.now();

        if (!today.isBefore(reapplyDate)) {
            return new InterviewRemainingResponse(TOTAL_COUNT, 1, 0, null);
        }

        long dday = ChronoUnit.DAYS.between(today, reapplyDate);
        return new InterviewRemainingResponse(TOTAL_COUNT, 0, dday, reapplyDate);
    }
}
