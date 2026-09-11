package com.weiver.interview.service;

import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.repository.DetailAnalysisReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.request.InterviewStartRequest;
import com.weiver.interview.dto.response.InterviewStartResponse;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.event.dto.InterviewQuestionGeneratedData;
import com.weiver.interview.event.dto.InterviewQuestionRequestedData;
import com.weiver.interview.event.dto.InterviewReportCompletedData;
import com.weiver.interview.event.dto.InterviewTranscriptSaveRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSavedData;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InterviewFlowServiceTest {

    private static final String APPLICANT_PUBLIC_ID = "applicant-public-id";

    @InjectMocks
    private InterviewFlowService interviewFlowService;

    @Mock private ApplicantRepository applicantRepository;
    @Mock private InterviewSessionRepository interviewSessionRepository;
    @Mock private TechnicalSkillReportRepository technicalSkillReportRepository;
    @Mock private DetailAnalysisReportRepository detailAnalysisReportRepository;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("면접 시작 시 새 세션을 만들고 첫 질문 생성 요청 이벤트를 발행한다")
    void startInterview_CreatesSessionAndPublishesFirstQuestionRequest() {
        Applicant applicant = applicant();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder()
                .job("DEVELOPER")
                .role("BACKEND")
                .build();

        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(technicalSkillReport));

        InterviewStartResponse response = interviewFlowService.startInterview(
                APPLICANT_PUBLIC_ID,
                new InterviewStartRequest("TECHNICAL")
        );

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository).save(sessionCaptor.capture());
        InterviewSession session = sessionCaptor.getValue();

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        assertThat(response.interviewSessionId()).isEqualTo(session.getInterviewSessionId());
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION.name());
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);
        assertThat(session.getTranscript()).isEmpty();
        assertThat(session.getQuarter()).matches("\\d{4}Q[1-4]");

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_QUESTION_REQUESTED);
        InterviewQuestionRequestedData data = (InterviewQuestionRequestedData) envelope.data();
        assertThat(data.applicantId()).isEqualTo(1L);
        assertThat(data.interviewSessionId()).isEqualTo(session.getInterviewSessionId());
        assertThat(data.sequence()).isEqualTo(1);
        assertThat(data.lastQuestionCode()).isNull();
        assertThat(data.lastInterview().question()).isNull();
        assertThat(data.lastInterview().answer()).isNull();
        assertThat(data.job()).isEqualTo("DEVELOPER");
        assertThat(data.role()).isEqualTo("BACKEND");
    }

    @Test
    @DisplayName("같은 지원자가 면접을 여러 번 시작해도 매번 다른 interview_session_id를 만든다")
    void startInterview_AllowsMultipleSessionsForSameApplicant() {
        Applicant applicant = applicant();
        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(completedAnalysis()));

        interviewFlowService.startInterview(APPLICANT_PUBLIC_ID, new InterviewStartRequest("TECHNICAL"));
        interviewFlowService.startInterview(APPLICANT_PUBLIC_ID, new InterviewStartRequest("TECHNICAL"));

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());

        List<InterviewSession> sessions = sessionCaptor.getAllValues();
        assertThat(sessions.get(0).getInterviewSessionId()).isNotEqualTo(sessions.get(1).getInterviewSessionId());
        verify(domainEventPublisher, times(2)).publishAfterCommit(any());
    }

    @Test
    @DisplayName("지원자 분석이 완료되지 않으면 면접을 시작할 수 없다")
    void startInterview_RejectsWhenApplicantAnalysisIsNotCompleted() {
        Applicant applicant = applicant();
        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interviewFlowService.startInterview(
                APPLICANT_PUBLIC_ID,
                new InterviewStartRequest("TECHNICAL")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED);

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("답변 제출 시 기존 질문 turn을 overwrite하고 다음 질문 요청 이벤트를 발행한다")
    void submitAnswer_UpdatesTurnAndPublishesNextQuestionRequest() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(completedAnalysis()));

        interviewFlowService.submitAnswer(
                sessionId,
                APPLICANT_PUBLIC_ID,
                new InterviewAnswerSubmitRequest("S_01_00", 1, "첫 답변")
        );

        assertThat(session.getTranscript()).hasSize(1);
        assertThat(session.getTranscript().get(0).answer()).isEqualTo("첫 답변");
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_QUESTION_REQUESTED);
        InterviewQuestionRequestedData data = (InterviewQuestionRequestedData) envelope.data();
        assertThat(data.sequence()).isEqualTo(2);
        assertThat(data.lastQuestionCode()).isEqualTo("S_01_00");
        assertThat(data.lastInterview().question()).isEqualTo("첫 질문");
        assertThat(data.lastInterview().answer()).isEqualTo("첫 답변");
    }

    @Test
    @DisplayName("답변 제출 재시도 중 이미 다음 질문 대기 상태면 다음 질문 요청 이벤트를 재발행하지 않는다")
    void submitAnswer_DoesNotRepublishQuestionRequestWhenAlreadyWaiting() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                List.of(new InterviewTurnDTO("S_01_00", 1, "첫 질문", "첫 답변")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.submitAnswer(
                sessionId,
                APPLICANT_PUBLIC_ID,
                new InterviewAnswerSubmitRequest("S_01_00", 1, "수정 답변")
        );

        assertThat(session.getTranscript().get(0).answer()).isEqualTo("수정 답변");
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("답변 제출 대상은 questionCode와 sequence가 모두 일치해야 한다")
    void submitAnswer_RequiresExactQuestionCodeAndSequenceMatch() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.submitAnswer(
                sessionId,
                APPLICANT_PUBLIC_ID,
                new InterviewAnswerSubmitRequest("S_01_00", 2, "잘못된 답변")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("답변 대상 질문을 찾을 수 없습니다.");

        assertThat(session.getTranscript().get(0).answer()).isNull();
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("이미 저장된 질문 생성 이벤트가 중복 수신되면 transcript에 다시 append하지 않는다")
    void handleQuestionGenerated_IgnoresDuplicateQuestion() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleQuestionGenerated(new InterviewQuestionGeneratedData(
                1L,
                sessionId,
                "S_01_00",
                1,
                "첫 질문"
        ));

        assertThat(session.getTranscript()).hasSize(1);
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("E_ 질문이 생성되면 면접 종료 메시지를 보내고 transcript 저장 요청 이벤트를 발행한다")
    void handleQuestionGenerated_PublishesTranscriptSaveRequestForEndQuestion() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                List.of(
                        new InterviewTurnDTO("S_01_00", 1, "기술 질문", "기술 답변"),
                        new InterviewTurnDTO("C_01_00", 2, "컬처 질문", "컬처 답변")
                ));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleQuestionGenerated(new InterviewQuestionGeneratedData(
                1L,
                sessionId,
                "E_00_00",
                3,
                "면접이 종료되었습니다."
        ));

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED);
        assertThat(session.getTranscript()).hasSize(3);

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_TRANSCRIPT_SAVE_REQUESTED);
        InterviewTranscriptSaveRequestedData data = (InterviewTranscriptSaveRequestedData) envelope.data();
        assertThat(data.interviewSessionId()).isEqualTo(sessionId);
        assertThat(data.skillInterview().turns()).hasSize(1);
        assertThat(data.skillInterview().turns().get(0).answer()).isEqualTo("기술 답변");
        assertThat(data.cultureInterview().turns()).hasSize(1);
        assertThat(data.cultureInterview().turns().get(0).question()).isEqualTo("컬처 질문");

        ArgumentCaptor<InterviewWebSocketMessageResponse> messageCaptor =
                ArgumentCaptor.forClass(InterviewWebSocketMessageResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(APPLICANT_PUBLIC_ID),
                eq("/queue/interviews"),
                messageCaptor.capture()
        );

        InterviewWebSocketMessageResponse message = messageCaptor.getValue();
        assertThat(message.type()).isEqualTo("INTERVIEW_FINISHED");
        assertThat(message.status()).isEqualTo(InterviewSessionStatus.FINISHED.name());
        assertThat(message.interviewSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("transcript 저장 완료 이벤트 수신 시 report 요청 이벤트를 이어서 발행한다")
    void handleTranscriptSaved_PublishesReportRequest() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleTranscriptSaved(new InterviewTranscriptSavedData(1L, sessionId, true));

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.REPORT_REQUESTED);

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_REPORT_REQUESTED);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("transcript 저장 완료 이벤트는 transcript 저장 요청 상태에서만 처리한다")
    void handleTranscriptSaved_RejectsOutOfOrderEvent() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.handleTranscriptSaved(
                new InterviewTranscriptSavedData(1L, sessionId, true)
        ))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("interview transcript saved event is out of order");

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("nested evaluation map 수신 시 DetailAnalysisReport를 interview_session_id 기준으로 insert한다")
    void handleReportCompleted_InsertsDetailAnalysisReportWithNestedEvaluation() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.REPORT_REQUESTED, List.of());
        Map<String, Object> skillAnalysis = Map.of("criteria_summary", Map.of("logic", Map.of("average_score", 4.5)));
        Map<String, Object> cultureAnalysis = Map.of("culture_axis", Map.of("openness_to_change", 0.8));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(sessionId)).willReturn(Optional.empty());

        interviewFlowService.handleReportCompleted(new InterviewReportCompletedData(
                1L,
                sessionId,
                "홍길동",
                List.of("Java"),
                List.of("Spring"),
                Map.of("skill_analysis", skillAnalysis, "culture_analysis", cultureAnalysis)
        ));

        ArgumentCaptor<DetailAnalysisReport> reportCaptor = ArgumentCaptor.forClass(DetailAnalysisReport.class);
        verify(detailAnalysisReportRepository).save(reportCaptor.capture());

        DetailAnalysisReport saved = reportCaptor.getValue();
        assertThat(saved.getApplicant()).isEqualTo(applicant);
        assertThat(saved.getInterviewSession()).isEqualTo(session);
        assertThat(saved.getSkillAnalysis()).isEqualTo(skillAnalysis);
        assertThat(saved.getCultureAnalysis()).isEqualTo(cultureAnalysis);
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.REPORT_COMPLETED);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("flat skill evaluation map 수신 시 전체 evaluation을 skillAnalysis로 update한다")
    void handleReportCompleted_UpdatesExistingDetailAnalysisReportWithFlatSkillEvaluation() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.REPORT_COMPLETED, List.of());
        DetailAnalysisReport existing = DetailAnalysisReport.builder()
                .applicant(applicant)
                .interviewSession(session)
                .skillAnalysis(Map.of())
                .cultureAnalysis(Map.of())
                .build();
        Map<String, Object> evaluation = Map.of("criteria_summary", Map.of("logic", Map.of("average_score", 5.0)));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(sessionId)).willReturn(Optional.of(existing));

        interviewFlowService.handleReportCompleted(new InterviewReportCompletedData(
                1L,
                sessionId,
                "홍길동",
                List.of(),
                List.of(),
                evaluation
        ));

        verify(detailAnalysisReportRepository, never()).save(any());
        assertThat(existing.getSkillAnalysis()).isEqualTo(evaluation);
        assertThat(existing.getCultureAnalysis()).isEmpty();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("report 완료 이벤트는 report 요청 이후 상태에서만 처리한다")
    void handleReportCompleted_RejectsOutOfOrderEvent() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.handleReportCompleted(new InterviewReportCompletedData(
                1L,
                sessionId,
                "홍길동",
                List.of(),
                List.of(),
                Map.of("criteria_summary", Map.of())
        )))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("interview report completed event is out of order");

        verifyNoInteractions(detailAnalysisReportRepository);
    }

    private Applicant applicant() {
        return Applicant.builder()
                .applicantId(1L)
                .publicId(APPLICANT_PUBLIC_ID)
                .name("홍길동")
                .build();
    }

    private TechnicalSkillReport completedAnalysis() {
        return TechnicalSkillReport.builder()
                .job("DEVELOPER")
                .role("BACKEND")
                .build();
    }

    private InterviewSession session(
            UUID sessionId,
            Applicant applicant,
            InterviewSessionStatus status,
            List<InterviewTurnDTO> transcript
    ) {
        return InterviewSession.builder()
                .interviewSessionId(sessionId)
                .applicant(applicant)
                .quarter("2026Q2")
                .sessionStatus(status)
                .transcript(transcript)
                .build();
    }
}
