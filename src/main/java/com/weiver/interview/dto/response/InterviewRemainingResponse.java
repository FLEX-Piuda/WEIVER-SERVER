package com.weiver.interview.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "AI 면접 잔여 횟수 · 재지원 D-day 조회 응답")
public record InterviewRemainingResponse(
        @Schema(description = "면접 총 가능 횟수(롤링 31일 창당 4회)", example = "4")
        int totalCount,

        @Schema(description = "잔여 면접 가능 횟수(0~4)", example = "3")
        int remainingCount,

        @Schema(description = "재지원까지 남은 일수(지금 가능하면 0)", example = "0")
        long reapplyDDay,

        @Schema(description = "재지원 가능 날짜(지금 가능하면 null)", example = "2026-10-08")
        LocalDate reapplyAvailableDate
) {

    /** 롤링 31일 창당 허용되는 면접 총 횟수(한 달 4회). */
    private static final int TOTAL_COUNT = 4;

    /**
     * 면접(재지원) 가능 상태 응답.
     *
     * <p>롤링 31일 창에서 활성 소진 세션이 4회 미만이라 지금 바로 면접이 가능한 경우.
     * total=4, remaining=잔여 횟수(1~4), D-day=0, 재지원 날짜 없음(null).
     */
    public static InterviewRemainingResponse available(int remainingCount) {
        return new InterviewRemainingResponse(TOTAL_COUNT, remainingCount, 0, null);
    }

    /**
     * 재지원 대기 상태 응답.
     *
     * <p>롤링 31일 창에서 활성 소진 세션이 4회에 도달해 대기 중인 경우.
     * total=4, remaining=0, 다음 슬롯이 열리는 날까지 남은 일수(D-day)와 재지원 가능 날짜를 함께 담는다.
     */
    public static InterviewRemainingResponse waiting(long reapplyDDay, LocalDate reapplyAvailableDate) {
        return new InterviewRemainingResponse(TOTAL_COUNT, 0, reapplyDDay, reapplyAvailableDate);
    }
}
