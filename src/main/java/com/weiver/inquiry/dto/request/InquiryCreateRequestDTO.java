package com.weiver.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문의사항 제출 요청 DTO")
public record InquiryCreateRequestDTO(

        @Schema(description = "문의 제목", example = "면접 일정 문의")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 최대 100자까지 입력할 수 있습니다.")
        String title,

        @Schema(description = "문의 내용", example = "면접 일정을 변경하고 싶습니다.")
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 2000, message = "내용은 최대 2000자까지 입력할 수 있습니다.")
        String content
) {
}
