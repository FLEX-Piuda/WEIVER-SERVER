package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.domain.InterviewErrorReport;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.request.InterviewErrorReportRequestDTO;
import com.weiver.interview.repository.InterviewErrorReportRepository;
import com.weiver.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InterviewErrorReportServiceTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewErrorReportRepository interviewErrorReportRepository;

    @InjectMocks
    private InterviewErrorReportService interviewErrorReportService;

    @Captor
    private ArgumentCaptor<InterviewErrorReport> errorReportCaptor;

    private static final String OWNER_PUBLIC_ID = "applicant-pub-1";

    @Test
    @DisplayName("세션 소유자가 오류를 신고하면 InterviewErrorReport가 저장된다")
    void createErrorReport_Success() {
        // given
        UUID sessionId = UUID.randomUUID();
        Applicant applicant = Applicant.builder().publicId(OWNER_PUBLIC_ID).build();
        InterviewSession session = InterviewSession.builder()
                .applicant(applicant)
                .quarter("2026Q1")
                .build();
        InterviewErrorReportRequestDTO request =
                new InterviewErrorReportRequestDTO("질문 음성이 재생되지 않았습니다.");

        given(interviewSessionRepository.findByInterviewSessionId(sessionId))
                .willReturn(Optional.of(session));

        // when
        interviewErrorReportService.createErrorReport(sessionId, OWNER_PUBLIC_ID, request);

        // then
        then(interviewErrorReportRepository).should().save(errorReportCaptor.capture());
        InterviewErrorReport saved = errorReportCaptor.getValue();
        assertThat(saved.getContent()).isEqualTo("질문 음성이 재생되지 않았습니다.");
        assertThat(saved.getInterviewSession()).isSameAs(session);
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 INTERVIEW_SESSION_NOT_FOUND 예외가 발생한다")
    void createErrorReport_SessionNotFound() {
        // given
        UUID sessionId = UUID.randomUUID();
        InterviewErrorReportRequestDTO request =
                new InterviewErrorReportRequestDTO("오류 내용");

        given(interviewSessionRepository.findByInterviewSessionId(sessionId))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() ->
                interviewErrorReportService.createErrorReport(sessionId, OWNER_PUBLIC_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);

        then(interviewErrorReportRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("세션 소유자가 아니면 FORBIDDEN 예외가 발생한다")
    void createErrorReport_Forbidden() {
        // given
        UUID sessionId = UUID.randomUUID();
        Applicant owner = Applicant.builder().publicId(OWNER_PUBLIC_ID).build();
        InterviewSession session = InterviewSession.builder()
                .applicant(owner)
                .quarter("2026Q1")
                .build();
        InterviewErrorReportRequestDTO request =
                new InterviewErrorReportRequestDTO("오류 내용");

        given(interviewSessionRepository.findByInterviewSessionId(sessionId))
                .willReturn(Optional.of(session));

        // when, then
        assertThatThrownBy(() ->
                interviewErrorReportService.createErrorReport(sessionId, "other-applicant", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        then(interviewErrorReportRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
