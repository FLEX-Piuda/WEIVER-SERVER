package com.weiver.auth.controller;

import com.weiver.auth.dto.request.ApplicantEmailSendRequestDTO;
import com.weiver.auth.dto.request.ApplicantEmailVerifyRequestDTO;
import com.weiver.auth.dto.request.ApplicantLoginRequestDTO;
import com.weiver.auth.dto.request.ApplicantPasswordChangeRequestDTO;
import com.weiver.auth.dto.request.ApplicantSignupCompleteRequestDTO;
import com.weiver.auth.dto.request.ApplicantSignupInitRequestDTO;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantLoginResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupInitResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupResponseDTO;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.global.security.cookie.CookieProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "구직자(Applicant) 인증(Auth) API", description = "구직자 이메일 인증, 회원가입, 로그인, 회원탈퇴를 처리하는 API입니다.")
@RestController
@RequestMapping("/api/auth/applicants")
@RequiredArgsConstructor
public class ApplicantAuthController {

    private final ApplicantAuthService applicantAuthService;
    private final CookieProvider cookieProvider;

    @Operation(
            summary = "이메일 인증번호 전송",
            description = "회원가입시 인증에 필요한 코드를 회원의 이메일로 전송합니다."
    )
    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(
            @RequestBody
            @Valid
            ApplicantEmailSendRequestDTO requestDTO
    ) {
        applicantAuthService.sendEmailCode(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, null, "이메일 인증번호 전송에 성공했습니다."));
    }

    @Operation(
            summary = "이메일 인증번호 검증",
            description = "이메일로 전송된 인증번호를 검증합니다.<br>"+
                    "인증번호가 일치하면 이메일 인증 성공 결과를 반환합니다."
    )
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<ApplicantEmailVerifyResponseDTO>> verifyEmailCode(
            @RequestBody
            @Valid
            ApplicantEmailVerifyRequestDTO requestDTO
    ) {
        ApplicantEmailVerifyResponseDTO responseDTO = applicantAuthService.verifyEmailCode(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "이메일 인증번호 확인에 성공했습니다."));
    }

    @Operation(
            summary = "회원가입 1단계 - 계정 정보 등록",
            description = "이메일, 비밀번호, 비밀번호 확인, 이메일 인증 토큰을 검증한 뒤 PENDING 상태의 구직자를 생성합니다.<br>" +
                    "동일 이메일의 PENDING 계정이 이미 존재하면 비밀번호를 갱신합니다.<br>" +
                    "응답으로 약관 동의 단계에서 사용할 signupToken을 반환합니다."
    )
    @PostMapping("/signup/init")
    public ResponseEntity<ApiResponse<ApplicantSignupInitResponseDTO>> initSignup(
            @RequestBody
            @Valid
            ApplicantSignupInitRequestDTO requestDTO
    ) {
        ApplicantSignupInitResponseDTO responseDTO = applicantAuthService.initSignup(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "회원가입 1단계에 성공했습니다."));
    }

    @Operation(
            summary = "회원가입 2단계 - 약관 동의",
            description = "signupToken과 약관 동의 정보를 받아 회원가입을 최종 완료합니다.<br>" +
                    "필수 약관 동의 검증 후 PENDING 계정을 ACTIVE로 전환합니다.<br>" +
                    "성공 시 Access Token과 Role을 반환하고, Refresh Token은 Cookie로 발급합니다."
    )
    @PostMapping("/signup/agreements")
    public ResponseEntity<ApiResponse<ApplicantSignupResponseDTO>> completeSignup(
            @RequestBody
            @Valid
            ApplicantSignupCompleteRequestDTO requestDTO,

            @Parameter(hidden = true)
            HttpServletResponse httpServletResponse
    ) {
        ApplicantLoginResult loginResult = applicantAuthService.completeSignup(requestDTO);

        ResponseCookie refreshTokenCookie = cookieProvider.createRefreshTokenCookie(loginResult.refreshToken());

        httpServletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie.toString()
        );

        ApplicantSignupResponseDTO responseDTO = new ApplicantSignupResponseDTO(loginResult.role(), loginResult.accessToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(responseDTO, "회원가입에 성공했습니다."));
    }

    @Operation(
            summary = "로그인",
            description = "구직자의 이메일과 비밀번호를 검증하여 로그인을 처리합니다.<br>" +
                    "로그인 성공 시 Access Token과 Role을 응답 body로 반환하고, Refresh Token은 HttpOnly Cookie로 발급합니다."
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<ApplicantLoginResponseDTO>> login(
            @RequestBody
            @Valid
            ApplicantLoginRequestDTO requestDTO,

            @Parameter(hidden = true)
            HttpServletResponse httpServletResponse
    ) {
        ApplicantLoginResult loginResult = applicantAuthService.login(requestDTO);

        ResponseCookie refreshTokenCookie = cookieProvider.createRefreshTokenCookie(loginResult.refreshToken());

        httpServletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie.toString()
        );

        ApplicantLoginResponseDTO responseDTO = new ApplicantLoginResponseDTO(loginResult.accessToken(), loginResult.role());

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "로그인에 성공했습니다."));

    }

    @Operation(
            summary = "회원 탈퇴",
            description = "현재 로그인한 구직자 계정을 탈퇴 처리합니다.<br>" +
                    "탈퇴 성공 시 저장된 Refresh Token을 삭제하고 Refresh Token Cookie를 즉시 만료시킵니다.<br>" +
                    "Authorization Header에 Bearer Access Token이 필요합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthenticatedPrincipal principal,

            @Parameter(hidden = true)
            HttpServletResponse httpServletResponse
    ) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        applicantAuthService.withdraw(principal.publicId());

        ResponseCookie expiredRefreshTokenCookie = cookieProvider.createExpiredRefreshTokenCookie();

        httpServletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredRefreshTokenCookie.toString()
        );

        return ResponseEntity.ok(ApiResponse.success(200, null, "회원탈퇴에 성공했습니다."));
    }

    @Operation(
            summary = "비밀번호 재설정 인증번호 전송",
            description = "비밀번호 찾기/변경을 위해 인증번호를 회원의 이메일로 전송합니다.<br>" +
                    "가입되지 않은 이메일도 이메일 열거 방지를 위해 성공 응답을 반환하되 실제 코드는 발송하지 않습니다."
    )
    @PostMapping("/password/email/send")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
            @RequestBody
            @Valid
            ApplicantEmailSendRequestDTO requestDTO
    ) {
        applicantAuthService.sendPasswordResetCode(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, null, "비밀번호 재설정 인증번호 전송에 성공했습니다."));
    }

    @Operation(
            summary = "비밀번호 재설정 인증번호 검증",
            description = "이메일로 전송된 비밀번호 재설정 인증번호를 검증합니다.<br>" +
                    "인증번호가 일치하면 비밀번호 변경에 사용할 verificationToken을 반환합니다."
    )
    @PostMapping("/password/email/verify")
    public ResponseEntity<ApiResponse<ApplicantEmailVerifyResponseDTO>> verifyPasswordResetCode(
            @RequestBody
            @Valid
            ApplicantEmailVerifyRequestDTO requestDTO
    ) {
        ApplicantEmailVerifyResponseDTO responseDTO = applicantAuthService.verifyEmailCode(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "비밀번호 재설정 인증번호 확인에 성공했습니다."));
    }

    @Operation(
            summary = "비밀번호 변경",
            description = "이메일 인증 토큰을 검증한 뒤 구직자의 비밀번호를 변경합니다.<br>" +
                    "비밀번호 찾기와 로그인 후 비밀번호 변경에 공용으로 사용됩니다."
    )
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody
            @Valid
            ApplicantPasswordChangeRequestDTO requestDTO
    ) {
        applicantAuthService.changePassword(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, null, "비밀번호 변경에 성공했습니다."));
    }
}
