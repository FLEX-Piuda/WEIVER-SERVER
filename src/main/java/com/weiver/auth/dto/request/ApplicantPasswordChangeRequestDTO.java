package com.weiver.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplicantPasswordChangeRequestDTO(
        @Schema(description = "구직자 이메일", example = "applicant@example.com")
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "이메일 인증 토큰", example = "123e4567-e89b-12d3-a456-426614174000")
        @NotBlank(message = "이메일 인증 토큰은 필수 입력값입니다.")
        String verificationToken,

        @Schema(description = "새 비밀번호", example = "Password123!")
        @NotBlank(message = "비밀번호는 필수 입력값입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}:;\"'<>,.?/]).{8,64}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다."
        )
        String newPassword,

        @Schema(description = "새 비밀번호 확인값", example = "Password123!")
        @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
        String newPasswordConfirm
) {
}
