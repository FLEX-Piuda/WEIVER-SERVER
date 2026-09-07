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
import java.time.ZoneId;
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

    private static final int REAPPLY_DAYS = 31;

    /** 재지원 D-day 계산 기준 타임존(KST). 서버 기본 TZ에 의존하지 않기 위해 고정한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 면접이 "완료"된 것으로 간주하는 상태 집합(재지원 대기 대상).
     *
     * <p>FINISHED~REPORT_COMPLETED만 완료로 본다. FAILED(재처리 초과/복구 불가)는 완료로 보지 않아
     * 재시도(재응시)를 허용한다. 진행 중 상태(STARTED/WAITING_FOR_QUESTION/QUESTION_READY)도 완료가 아니다.
     */
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
     *
     * <p>정책: 면접 총 1회 + 재지원 기준일 = 최근 면접 세션의 진행일(세션 시작 시각, createTime) + 31일.
     * 즉 "완료 시각"이 아니라 세션이 시작된 날(createTime)을 기산점으로 쓴다. InterviewSession에는
     * 별도의 완료 타임스탬프 컬럼이 없고 BaseTimeEntity가 createTime/updateTime만 제공하기 때문이다.
     * 정확한 완료 시각을 기준으로 하려면 별도의 완료 타임스탬프 컬럼 추가가 필요하다(후속 과제).
     *
     * <p>날짜 비교는 서버 기본 TZ에 의존하지 않도록 KST로 고정한다.
     */
    public InterviewRemainingResponse getRemainingInterview(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        Optional<InterviewSession> completedSession =
                interviewSessionRepository.findFirstByApplicantAndSessionStatusInOrderByCreateTimeDesc(
                        applicant, COMPLETED_STATUSES);

        if (completedSession.isEmpty()) {
            return InterviewRemainingResponse.available();
        }

        // 저장된 세션 시작 시각(서버 로컬)을 KST 날짜로 변환해 today(KST)와 같은 기준으로 비교한다.
        LocalDate interviewDate = completedSession.get().getCreateTime()
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(KST)
                .toLocalDate();
        LocalDate reapplyDate = interviewDate.plusDays(REAPPLY_DAYS);
        LocalDate today = LocalDate.now(KST);

        if (!today.isBefore(reapplyDate)) {
            return InterviewRemainingResponse.available();
        }

        long dday = ChronoUnit.DAYS.between(today, reapplyDate);
        return InterviewRemainingResponse.waiting(dday, reapplyDate);
    }
}
