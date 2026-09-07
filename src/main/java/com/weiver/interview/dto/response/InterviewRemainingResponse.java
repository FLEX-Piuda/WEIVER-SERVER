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
}
