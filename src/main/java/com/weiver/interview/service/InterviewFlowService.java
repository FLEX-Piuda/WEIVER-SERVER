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
import com.weiver.global.event.util.EventIds;
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
import com.weiver.interview.event.dto.InterviewReportRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSaveRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSavedData;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class InterviewFlowService {

    private static final String SKILL_QUESTION_PREFIX = "S_";
    private static final String CULTURE_QUESTION_PREFIX = "C_";
    private static final String END_QUESTION_PREFIX = "E_";

    private final ApplicantRepository applicantRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final TechnicalSkillReportRepository technicalSkillReportRepository;
    private final DetailAnalysisReportRepository detailAnalysisReportRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 새 면접 세션을 만들고 첫 질문 생성 요청 이벤트를 발행한다.
     */
    public InterviewStartResponse startInterview(String applicantPublicId, InterviewStartRequest request) {
        Applicant applicant = applicantRepository.findByPublicId(applicantPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        InterviewSession session = InterviewSession.builder()
                .applicant(applicant)
                .quarter(currentQuarter())
                .sessionStatus(InterviewSessionStatus.STARTED)
                .build();

        interviewSessionRepository.save(session);
        session.updateStatus(InterviewSessionStatus.WAITING_FOR_QUESTION);
        publishQuestionRequested(session, null, 1);

        return new InterviewStartResponse(
                session.getInterviewSessionId(),
                session.getSessionStatus().name()
        );
    }

    /**
     * 지원자 답변을 기존 질문 turn에 반영하고 다음 질문 생성 요청 이벤트를 발행한다.
     */
    public InterviewWebSocketMessageResponse submitAnswer(
            UUID interviewSessionId,
            String applicantPublicId,
            InterviewAnswerSubmitRequest request
    ) {
        InterviewSession session = getSessionForApplicant(interviewSessionId, applicantPublicId);
        if (isAnswerClosed(session.getSessionStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        boolean alreadyWaitingForQuestion = session.getSessionStatus() == InterviewSessionStatus.WAITING_FOR_QUESTION;
        InterviewTurnDTO answeredTurn = session.updateAnswer(
                request.questionCode(),
                request.sequence(),
                request.answer()
        );
        if (answeredTurn == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "답변 대상 질문을 찾을 수 없습니다.");
        }

        if (alreadyWaitingForQuestion) {
            return InterviewWebSocketMessageResponse.answerAccepted(session.getInterviewSessionId());
        }

        session.updateStatus(InterviewSessionStatus.WAITING_FOR_QUESTION);
        publishQuestionRequested(session, answeredTurn, answeredTurn.sequence() + 1);
        return InterviewWebSocketMessageResponse.answerAccepted(session.getInterviewSessionId());
    }

    /**
     * AI가 생성한 질문을 transcript에 멱등 append하고, 종료 질문이면 transcript 저장 요청으로 이어간다.
     */
    public void handleQuestionGenerated(InterviewQuestionGeneratedData data) {
        validateQuestionGenerated(data);

        InterviewSession session = getSession(data.interviewSessionId());
        validateEventApplicant(session, data.applicantId());

        boolean appended = session.appendQuestion(
                data.nextQuestionCode(),
                data.sequence(),
                data.question()
        );
        if (!appended) {
            return;
        }

        if (isEndQuestion(data.nextQuestionCode())) {
            session.updateStatus(InterviewSessionStatus.FINISHED);
            publishTranscriptSaveRequested(session);
            session.updateStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED);
            sendInterviewMessage(
                    session,
                    InterviewWebSocketMessageResponse.interviewFinished(session.getInterviewSessionId())
            );
            return;
        }

        session.updateStatus(InterviewSessionStatus.QUESTION_READY);
        sendInterviewMessage(
                session,
                InterviewWebSocketMessageResponse.questionReady(
                        session.getInterviewSessionId(),
                        session.getSessionStatus(),
                        session.getTranscript().get(session.getTranscript().size() - 1)
                )
        );
    }

    /**
     * AI 서버의 transcript 저장 완료를 반영하고 최종 리포트 생성 요청 이벤트를 발행한다.
     */
    public void handleTranscriptSaved(InterviewTranscriptSavedData data) {
        validateTranscriptSaved(data);

        InterviewSession session = getSession(data.interviewSessionId());
        validateEventApplicant(session, data.applicantId());

        if (isTranscriptAlreadyProcessed(session.getSessionStatus())) {
            return;
        }
        if (session.getSessionStatus() != InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED) {
            throw new NonRetryableEventException("interview transcript saved event is out of order");
        }
        if (!Boolean.TRUE.equals(data.saved())) {
            throw new NonRetryableEventException("interview transcript was not saved");
        }

        session.updateStatus(InterviewSessionStatus.TRANSCRIPT_SAVED);
        publishReportRequested(session);
        session.updateStatus(InterviewSessionStatus.REPORT_REQUESTED);
    }

    /**
     * AI 최종 평가 결과를 interview_session_id 기준으로 저장하거나 갱신한다.
     */
    public void handleReportCompleted(InterviewReportCompletedData data) {
        validateReportCompleted(data);

        InterviewSession session = getSession(data.interviewSessionId());
        validateEventApplicant(session, data.applicantId());

        if (session.getSessionStatus() != InterviewSessionStatus.REPORT_REQUESTED
                && session.getSessionStatus() != InterviewSessionStatus.REPORT_COMPLETED) {
            throw new NonRetryableEventException("interview report completed event is out of order");
        }

        Applicant applicant = applicantRepository.findById(data.applicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        ReportAnalysis analysis = resolveAnalysis(data.evaluation());
        detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(data.interviewSessionId())
                .ifPresentOrElse(
                        report -> report.updateAnalysis(analysis.skillAnalysis(), analysis.cultureAnalysis()),
                        () -> detailAnalysisReportRepository.save(DetailAnalysisReport.builder()
                                .applicant(applicant)
                                .interviewSession(session)
                                .skillAnalysis(analysis.skillAnalysis())
                                .cultureAnalysis(analysis.cultureAnalysis())
                                .build())
                );

        session.updateStatus(InterviewSessionStatus.REPORT_COMPLETED);
    }

    /**
     * 다음 질문 생성 요청 payload를 현재 세션 상태와 마지막 답변 기준으로 구성한다.
     */
    private void publishQuestionRequested(InterviewSession session, InterviewTurnDTO lastTurn, Integer nextSequence) {
        Applicant applicant = session.getApplicant();
        TechnicalSkillReport technicalSkillReport = getCompletedAnalysis(applicant);

        InterviewQuestionRequestedData data = new InterviewQuestionRequestedData(
                applicant.getApplicantId(),
                applicant.getName(),
                session.getInterviewSessionId(),
                lastTurn != null ? lastTurn.questionCode() : null,
                nextSequence,
                technicalSkillReport.getJob(),
                technicalSkillReport.getRole(),
                new InterviewQuestionRequestedData.LastInterviewData(
                        lastTurn != null ? lastTurn.question() : null,
                        lastTurn != null ? lastTurn.answer() : null
                )
        );

        domainEventPublisher.publishAfterCommit(EventEnvelope.request(
                EventType.INTERVIEW_QUESTION_REQUESTED,
                data,
                EventIds.newEventId()
        ));
    }

    private TechnicalSkillReport getCompletedAnalysis(Applicant applicant) {
        TechnicalSkillReport report = technicalSkillReportRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED));

        if (!StringUtils.hasText(report.getJob()) || !StringUtils.hasText(report.getRole())) {
            throw new BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED);
        }
        return report;
    }

    /**
     * 종료된 면접 transcript를 기술/컬처 섹션으로 나누어 AI 서버 저장 요청 이벤트로 발행한다.
     */
    private void publishTranscriptSaveRequested(InterviewSession session) {
        InterviewTranscriptSaveRequestedData data = new InterviewTranscriptSaveRequestedData(
                session.getApplicant().getApplicantId(),
                session.getInterviewSessionId(),
                new InterviewTranscriptSaveRequestedData.TranscriptSectionData(toTranscriptTurns(session, SKILL_QUESTION_PREFIX)),
                new InterviewTranscriptSaveRequestedData.TranscriptSectionData(toTranscriptTurns(session, CULTURE_QUESTION_PREFIX))
        );

        domainEventPublisher.publishAfterCommit(EventEnvelope.request(
                EventType.INTERVIEW_TRANSCRIPT_SAVE_REQUESTED,
                data,
                EventIds.newEventId()
        ));
    }

    /**
     * 저장된 transcript를 기반으로 AI 서버에 최종 리포트 생성을 요청한다.
     */
    private void publishReportRequested(InterviewSession session) {
        InterviewReportRequestedData data = new InterviewReportRequestedData(
                session.getApplicant().getApplicantId(),
                session.getInterviewSessionId()
        );

        domainEventPublisher.publishAfterCommit(EventEnvelope.request(
                EventType.INTERVIEW_REPORT_REQUESTED,
                data,
                EventIds.newEventId()
        ));
    }

    /**
     * 면접 진행 상태를 연결된 지원자 WebSocket 구독 채널로 전송한다.
     */
    private void sendInterviewMessage(InterviewSession session, InterviewWebSocketMessageResponse response) {
        String applicantPublicId = session.getApplicant().getPublicId();
        Runnable sender = () -> messagingTemplate.convertAndSendToUser(
                applicantPublicId,
                "/queue/interviews",
                response
        );

        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.run();
            }
        });
    }

    private List<InterviewTranscriptSaveRequestedData.TranscriptTurnData> toTranscriptTurns(
            InterviewSession session,
            String questionPrefix
    ) {
        return session.getTranscript().stream()
                .filter(Objects::nonNull)
                .filter(turn -> turn.questionCode() != null && turn.questionCode().startsWith(questionPrefix))
                .map(turn -> new InterviewTranscriptSaveRequestedData.TranscriptTurnData(
                        turn.question(),
                        turn.answer()
                ))
                .toList();
    }

    private InterviewSession getSession(UUID interviewSessionId) {
        return interviewSessionRepository.findByInterviewSessionId(interviewSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private InterviewSession getSessionForApplicant(UUID interviewSessionId, String applicantPublicId) {
        InterviewSession session = getSession(interviewSessionId);
        if (!Objects.equals(session.getApplicant().getPublicId(), applicantPublicId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private void validateEventApplicant(InterviewSession session, Long applicantId) {
        if (!Objects.equals(session.getApplicant().getApplicantId(), applicantId)) {
            throw new NonRetryableEventException("applicant_id does not match interview_session_id");
        }
    }

    private void validateQuestionGenerated(InterviewQuestionGeneratedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.interviewSessionId() == null) {
            throw new NonRetryableEventException("interview_session_id is required");
        }
        if (data.nextQuestionCode() == null || data.nextQuestionCode().isBlank()) {
            throw new NonRetryableEventException("next_question_code is required");
        }
        if (data.sequence() == null) {
            throw new NonRetryableEventException("sequence is required");
        }
        if (data.question() == null || data.question().isBlank()) {
            throw new NonRetryableEventException("question is required");
        }
    }

    private void validateTranscriptSaved(InterviewTranscriptSavedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.interviewSessionId() == null) {
            throw new NonRetryableEventException("interview_session_id is required");
        }
        if (data.saved() == null) {
            throw new NonRetryableEventException("saved is required");
        }
    }

    private void validateReportCompleted(InterviewReportCompletedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.interviewSessionId() == null) {
            throw new NonRetryableEventException("interview_session_id is required");
        }
        if (data.evaluation() == null) {
            throw new NonRetryableEventException("evaluation is required");
        }
    }

    private ReportAnalysis resolveAnalysis(Map<String, Object> evaluation) {
        // AI 서버 전환기 호환을 위해 nested(skill_analysis/culture_analysis)와 flat skill map을 모두 수용한다.
        Map<String, Object> skillAnalysis = mapValue(evaluation.get("skill_analysis"));
        Map<String, Object> cultureAnalysis = mapValue(evaluation.get("culture_analysis"));

        if (skillAnalysis == null && hasSkillAnalysisKeys(evaluation)) {
            skillAnalysis = evaluation;
        }
        if (cultureAnalysis == null && hasCultureAnalysisKeys(evaluation)) {
            cultureAnalysis = evaluation;
        }
        if (skillAnalysis == null && cultureAnalysis == null) {
            skillAnalysis = evaluation;
        }

        return new ReportAnalysis(
                skillAnalysis != null ? skillAnalysis : Map.of(),
                cultureAnalysis != null ? cultureAnalysis : Map.of()
        );
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key != null) {
                result.put(key.toString(), mapValue);
            }
        });
        return result;
    }

    private boolean hasSkillAnalysisKeys(Map<String, Object> evaluation) {
        return evaluation.containsKey("criteria_summary");
    }

    private boolean hasCultureAnalysisKeys(Map<String, Object> evaluation) {
        return evaluation.containsKey("culture_axis")
                || evaluation.containsKey("extracted_culturefit");
    }

    private boolean isAnswerClosed(InterviewSessionStatus status) {
        return status == InterviewSessionStatus.FINISHED
                || status == InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED
                || status == InterviewSessionStatus.TRANSCRIPT_SAVED
                || status == InterviewSessionStatus.REPORT_REQUESTED
                || status == InterviewSessionStatus.REPORT_COMPLETED
                || status == InterviewSessionStatus.FAILED;
    }

    private boolean isTranscriptAlreadyProcessed(InterviewSessionStatus status) {
        return status == InterviewSessionStatus.TRANSCRIPT_SAVED
                || status == InterviewSessionStatus.REPORT_REQUESTED
                || status == InterviewSessionStatus.REPORT_COMPLETED;
    }

    private boolean isEndQuestion(String questionCode) {
        return questionCode != null && questionCode.startsWith(END_QUESTION_PREFIX);
    }

    private String currentQuarter() {
        LocalDate today = LocalDate.now();
        int quarter = ((today.getMonthValue() - 1) / 3) + 1;
        return today.getYear() + "Q" + quarter;
    }

    private record ReportAnalysis(
            Map<String, Object> skillAnalysis,
            Map<String, Object> cultureAnalysis
    ) {
    }
}
