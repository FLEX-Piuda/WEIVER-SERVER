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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;
    @Mock
    private ApplicantService applicantService;
    @InjectMocks
    private InterviewSessionService interviewSessionService;

    private static final String PUBLIC_ID = "applicant-public-id";

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

    @Test
    @DisplayName("완료된 면접 세션이 없으면 면접 가능(remainingCount 1, D-day 0, 재지원일 null)이다")
    void getRemainingInterview_NoCompletedSession() {
        // given
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findFirstByApplicantAndSessionStatusInOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class)))
                .willReturn(Optional.empty());

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.remainingCount()).isEqualTo(1);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("완료 세션이 10일 전(31일 미만)이면 면접 불가(remainingCount 0, D-day > 0, 재지원일 = 완료일+31)이다")
    void getRemainingInterview_CompletedWithin31Days() {
        // given
        LocalDateTime completedAt = LocalDateTime.now().minusDays(10);
        LocalDate expectedReapplyDate = completedAt.toLocalDate().plusDays(31);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findFirstByApplicantAndSessionStatusInOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class)))
                .willReturn(Optional.of(aCompletedSessionCreatedAt(completedAt)));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.remainingCount()).isZero();
        assertThat(response.reapplyDDay()).isPositive();
        assertThat(response.reapplyAvailableDate()).isEqualTo(expectedReapplyDate);
    }

    @Test
    @DisplayName("완료 세션이 31일 이상 전이면 다시 면접 가능(remainingCount 1, D-day 0, 재지원일 null)이다")
    void getRemainingInterview_CompletedOver31DaysAgo() {
        // given
        LocalDateTime completedAt = LocalDateTime.now().minusDays(40);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findFirstByApplicantAndSessionStatusInOrderByCreateTimeDesc(
                any(Applicant.class), any(Collection.class)))
                .willReturn(Optional.of(aCompletedSessionCreatedAt(completedAt)));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.remainingCount()).isEqualTo(1);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }
}
