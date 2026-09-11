package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.response.InterviewRemainingResponse;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;
    @Mock
    private ApplicantService applicantService;
    @InjectMocks
    private InterviewSessionService interviewSessionService;

    private static final String PUBLIC_ID = "applicant-public-id";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Applicant anApplicant() {
        return Applicant.builder().build();
    }

    private InterviewSession aCompletedSessionCreatedAt(LocalDateTime createTime) {
        InterviewSession session = InterviewSession.builder()
                .quarter("2026Q1")
                .sessionStatus(InterviewSessionStatus.REPORT_COMPLETED)
                .build();
        ReflectionTestUtils.setField(session, "createTime", createTime);
        return session;
    }

    /**
     * KST 기준 특정 날짜(정오)에 진행된 완료 세션을 만든다.
     * 서버 기본 TZ와 무관하게 서비스가 해당 KST 날짜로 환산하도록 createTime을 구성한다.
     */
    private InterviewSession aCompletedSessionOnKstDate(LocalDate kstDate) {
        LocalDateTime createTime = ZonedDateTime.of(kstDate, LocalTime.NOON, KST)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        return aCompletedSessionCreatedAt(createTime);
    }

    @Test
    @DisplayName("소진된 면접 세션이 없으면 면접 가능(remainingCount 4, D-day 0, 재지원일 null)이다")
    void getRemainingInterview_NoConsumedSession() {
        // given
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of());

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isEqualTo(4);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("활성 소진 세션이 10일 전 1개면 잔여 3(remainingCount 3, D-day 0, 재지원일 null)이다")
    void getRemainingInterview_OneActiveSession() {
        // given
        LocalDate interviewDate = LocalDate.now(KST).minusDays(10);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(aCompletedSessionOnKstDate(interviewDate)));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isEqualTo(3);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("활성 소진 세션이 4개면 잔여 0이고 D-day는 4번째로 최근(가장 오래된) 세션의 재지원일 기준이다")
    void getRemainingInterview_FourActiveSessions() {
        // given
        LocalDate today = LocalDate.now(KST);
        // 리포지토리는 createTime 내림차순(최근순)으로 반환한다: today-1, today-5, today-10, today-20.
        // 4번째로 최근 = 가장 오래된 today-20 → 재지원일 = (today-20)+31 = today+11.
        LocalDate expectedSlot = today.minusDays(20).plusDays(31);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(
                        aCompletedSessionOnKstDate(today.minusDays(1)),
                        aCompletedSessionOnKstDate(today.minusDays(5)),
                        aCompletedSessionOnKstDate(today.minusDays(10)),
                        aCompletedSessionOnKstDate(today.minusDays(20))));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isZero();
        assertThat(response.reapplyDDay()).isEqualTo(11);
        assertThat(response.reapplyAvailableDate()).isEqualTo(expectedSlot);
    }

    @Test
    @DisplayName("소진 세션이 31일보다 오래되면(40일 전) 창에서 빠져 카운트되지 않아 잔여 4다")
    void getRemainingInterview_OlderThanWindowNotCounted() {
        // given
        LocalDate interviewDate = LocalDate.now(KST).minusDays(40);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(aCompletedSessionOnKstDate(interviewDate)));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isEqualTo(4);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("경계: 소진 세션이 KST 기준 정확히 31일 전이면 오늘이 재지원일이라 비활성이 되어 잔여 4다")
    void getRemainingInterview_Exactly31DaysAgoIsInactive() {
        // given
        LocalDate interviewDate = LocalDate.now(KST).minusDays(31);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(aCompletedSessionOnKstDate(interviewDate)));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isEqualTo(4);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("경계: 소진 세션이 KST 기준 정확히 30일 전이면 아직 활성이라 잔여 3이다")
    void getRemainingInterview_Exactly30DaysAgoIsActive() {
        // given
        LocalDate interviewDate = LocalDate.now(KST).minusDays(30);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(aCompletedSessionOnKstDate(interviewDate)));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isEqualTo(3);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("서비스가 리포지토리에 넘기는 소진 상태 집합에 FINISHED(제출 대기)가 포함된다")
    void getRemainingInterview_ConsumedStatusesContainFinished() {
        // given
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of());

        // when
        interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<InterviewSessionStatus>> statusesCaptor =
                ArgumentCaptor.forClass(Collection.class);
        then(interviewSessionRepository).should()
                .findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                        any(Applicant.class), statusesCaptor.capture(), any(LocalDateTime.class));
        assertThat(statusesCaptor.getValue())
                .contains(
                        InterviewSessionStatus.FINISHED,
                        InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED,
                        InterviewSessionStatus.TRANSCRIPT_SAVED,
                        InterviewSessionStatus.REPORT_REQUESTED,
                        InterviewSessionStatus.REPORT_COMPLETED)
                .doesNotContain(
                        InterviewSessionStatus.FAILED,
                        InterviewSessionStatus.STARTED,
                        InterviewSessionStatus.WAITING_FOR_QUESTION,
                        InterviewSessionStatus.QUESTION_READY);
    }

    @Test
    @DisplayName("활성·비활성 혼합 시 인메모리 필터가 비활성(40일 전)을 걸러내 활성 2개만 집계해 잔여 2다")
    void getRemainingInterview_MixedActiveAndInactive() {
        // given
        // mock은 DB의 createTime 하한 필터를 재현하지 않으므로 today-40을 리스트에 포함시켜
        // 서비스 인메모리 필터(today.isBefore)가 비활성으로 걸러냄을 검증한다.
        LocalDate today = LocalDate.now(KST);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(
                        aCompletedSessionOnKstDate(today.minusDays(1)),
                        aCompletedSessionOnKstDate(today.minusDays(5)),
                        aCompletedSessionOnKstDate(today.minusDays(40))));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isEqualTo(2);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("활성 세션이 정원(4)을 초과해 5개여도 IndexOutOfBounds 없이 4번째 최근 세션의 재지원일로 D-day를 계산한다")
    void getRemainingInterview_MoreThanLimitActiveSessions() {
        // given
        LocalDate today = LocalDate.now(KST);
        // 최근순: today-1, today-5, today-10, today-15, today-25 (모두 활성)
        // 4번째로 최근 = today-15 → 재지원일 = (today-15)+31 = today+16.
        LocalDate expectedSlot = today.minusDays(15).plusDays(31);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findByApplicantAndSessionStatusInAndCreateTimeAfterOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(
                        aCompletedSessionOnKstDate(today.minusDays(1)),
                        aCompletedSessionOnKstDate(today.minusDays(5)),
                        aCompletedSessionOnKstDate(today.minusDays(10)),
                        aCompletedSessionOnKstDate(today.minusDays(15)),
                        aCompletedSessionOnKstDate(today.minusDays(25))));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.remainingCount()).isZero();
        assertThat(response.reapplyDDay()).isEqualTo(16);
        assertThat(response.reapplyAvailableDate()).isEqualTo(expectedSlot);
    }
}
