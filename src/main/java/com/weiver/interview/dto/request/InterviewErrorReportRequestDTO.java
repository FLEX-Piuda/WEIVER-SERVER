package com.weiver.interview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterviewErrorReportRequestDTO(

        @Schema(description = "면접 중 발생한 오류 내용(자유 텍스트)", example = "질문 음성이 재생되지 않고 화면이 멈췄습니다.")
        @NotBlank(message = "오류 내용은 필수입니다.")
        @Size(max = 2000, message = "오류 내용은 2000자 이하로 입력해주세요.")
        String content
) {
}
