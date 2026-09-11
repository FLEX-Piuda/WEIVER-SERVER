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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InterviewSessionService {

    /** 재지원 롤링 창 크기(일). 각 소진 세션은 진행일 + 31일 동안 한 슬롯을 점유한다. */
    private static final int REAPPLY_DAYS = 31;

    /** 롤링 31일 창 안에서 허용되는 최대 면접 횟수(한 달 4회). */
    private static final int MONTHLY_LIMIT = 4;

    /** 재지원 D-day 계산 기준 타임존(KST). 서버 기본 TZ에 의존하지 않기 위해 고정한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 면접이 "완료"된 것으로 간주하는 상태 집합(1회 소진으로 집계).
     *
     * <p>면접 완료란 면접 Q&A만 종료된 상태를 뜻하며, 후속 분석 완료 여부와는 무관하다. 따라서
     * FINISHED("제출 대기")도 면접 Q&A가 끝난 것이므로 1회 소진으로 집계한다. 이후 제출·저장·리포트
     * 단계(TRANSCRIPT_SAVE_REQUESTED ~ REPORT_COMPLETED)도 모두 소진으로 본다.
     *
     * <p>FAILED(재처리 초과/복구 불가)는 소진으로 보지 않아 재시도(재응시)를 허용한다. 진행 중 상태
     * (STARTED/WAITING_FOR_QUESTION/QUESTION_READY)도 아직 소진이 아니다.
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
     * <p>정책: 한 달 4회(롤링 31일 창). 각 소진 세션은 진행일(세션 시작 시각, createTime) + 31일까지
     * 한 슬롯을 점유하며, 오늘(KST)이 그 재지원일 이전이면 아직 "활성"으로 창을 차지한다. 즉 "완료 시각"이
     * 아니라 세션이 시작된 날(createTime)을 기산점으로 쓴다. InterviewSession에는 별도의 완료 타임스탬프
     * 컬럼이 없고 BaseTimeEntity가 createTime/updateTime만 제공하기 때문이다. 정확한 완료 시각을 기준으로
     * 하려면 별도의 완료 타임스탬프 컬럼 추가가 필요하다(후속 과제).
     *
     * <p>잔여 횟수 = max(0, 4 - 활성 세션 수). 잔여가 0이면 다음 슬롯이 열리는 날은 활성 세션들을 최근순
     * (createTime 내림차순, 재지원일 내림차순과 동일)으로 두었을 때 4번째(0-based 인덱스 3) 세션의 재지원일이다.
     *
     * <p>날짜 비교는 서버 기본 TZ에 의존하지 않도록 KST로 고정한다.
     */
    public InterviewRemainingResponse getRemainingInterview(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        LocalDate today = LocalDate.now(KST);

        // 쿼리는 하루 여유(-32일)로 후보를 좁히고, 활성 여부의 최종 판정은 인메모리(today.isBefore)에서 한다.
        // createTime이 서버 기본 TZ 기준 LocalDateTime으로 저장되므로, today-32일 KST 자정을 systemDefault로 환산해 하한을 맞춘다.
        LocalDateTime threshold = today.minusDays(REAPPLY_DAYS + 1)
                .atStartOfDay(KST)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();

        List<InterviewSession> sessions =
                interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                        applicant, COMPLETED_STATUSES, threshold);

        // 저장된 세션 시작 시각(서버 로컬)을 KST 날짜로 변환해 재지원일(진행일+31)을 구하고,
        // 오늘이 재지원일 이전인(=아직 창을 점유하는) 활성 세션만 createTime 내림차순 순서를 유지해 모은다.
        List<LocalDate> activeReapplyDates = sessions.stream()
                .map(session -> session.getCreateTime()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(KST)
                        .toLocalDate()
                        .plusDays(REAPPLY_DAYS))
                .filter(today::isBefore)
                .toList();

        int remaining = Math.max(0, MONTHLY_LIMIT - activeReapplyDates.size());

        if (remaining > 0) {
            return InterviewRemainingResponse.available(remaining);
        }

        // 잔여 0: 활성 세션을 최근순으로 둔 상태에서 4번째(인덱스 3) 세션의 재지원일에 다음 슬롯이 열린다.
        LocalDate slot = activeReapplyDates.get(MONTHLY_LIMIT - 1);
        long dday = ChronoUnit.DAYS.between(today, slot);
        return InterviewRemainingResponse.waiting(dday, slot);
    }
}
