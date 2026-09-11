package com.weiver.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplicantPasswordUpdateRequestDTO(
        @Schema(description = "현재 비밀번호", example = "OldPassword123!")
        // 현재 비밀번호는 과거 복잡도 정책 이전에 설정된 값도 재인증에 통과해야 하므로 형식(@Pattern) 검증을 걸지 않는다.
        @NotBlank(message = "현재 비밀번호는 필수 입력값입니다.")
        String currentPassword,

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
