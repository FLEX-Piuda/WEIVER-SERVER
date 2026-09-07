package com.weiver.interview.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "AI 면접 잔여 횟수 · 재지원 D-day 조회 응답")
public record InterviewRemainingResponse(
        @Schema(description = "면접 총 가능 횟수", example = "1")
        int totalCount,

        @Schema(description = "잔여 면접 가능 횟수(0 또는 1)", example = "1")
        int remainingCount,

        @Schema(description = "재지원까지 남은 일수(지금 가능하면 0)", example = "0")
        long reapplyDDay,

        @Schema(description = "재지원 가능 날짜(지금 가능하면 null)", example = "2026-10-08")
        LocalDate reapplyAvailableDate
) {

    /**
     * 면접(재지원) 가능 상태 응답.
     *
     * <p>완료된 면접이 없거나 재지원 대기 기간이 지나 지금 바로 면접이 가능한 경우.
     * total=1, remaining=1, D-day=0, 재지원 날짜 없음(null).
     */
    public static InterviewRemainingResponse available() {
        return new InterviewRemainingResponse(1, 1, 0, null);
    }

    /**
     * 재지원 대기 상태 응답.
     *
     * <p>최근 면접 이후 재지원 기준일이 아직 도래하지 않아 대기 중인 경우.
     * total=1, remaining=0, 남은 일수(D-day)와 재지원 가능 날짜를 함께 담는다.
     */
    public static InterviewRemainingResponse waiting(long reapplyDDay, LocalDate reapplyAvailableDate) {
        return new InterviewRemainingResponse(1, 0, reapplyDDay, reapplyAvailableDate);
    }
}
