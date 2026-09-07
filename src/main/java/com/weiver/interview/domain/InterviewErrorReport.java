package com.weiver.interview.domain;

import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "interview_error_reports")
public class InterviewErrorReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_report_id")
    private Long id;

    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private InterviewSession interviewSession;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 오류 리포트 생성 정적 팩토리
     */
    public static InterviewErrorReport of(InterviewSession session, String content) {
        return InterviewErrorReport.builder()
                .interviewSession(session)
                .content(content)
                .build();
    }
}
