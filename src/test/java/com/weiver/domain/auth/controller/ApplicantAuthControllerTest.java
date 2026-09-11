package com.weiver.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.auth.controller.ApplicantAuthController;
import com.weiver.auth.dto.request.ApplicantAgreementRequestDTO;
import com.weiver.auth.dto.request.ApplicantEmailSendRequestDTO;
import com.weiver.auth.dto.request.ApplicantEmailVerifyRequestDTO;
import com.weiver.auth.dto.request.ApplicantLoginRequestDTO;
import com.weiver.auth.dto.request.ApplicantPasswordChangeRequestDTO;
import com.weiver.auth.dto.request.ApplicantPasswordUpdateRequestDTO;
import com.weiver.auth.dto.request.ApplicantSignupCompleteRequestDTO;
import com.weiver.auth.dto.request.ApplicantSignupInitRequestDTO;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupInitResponseDTO;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.cookie.RefreshTokenCookieResolver;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicantAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ApplicantAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private ApplicantAuthService applicantAuthService;
    @MockitoBean private CookieProvider cookieProvider;
    @MockitoBean private BearerTokenResolver bearerTokenResolver;
    @MockitoBean private RefreshTokenCookieResolver refreshTokenCookieResolver;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("이메일 인증번호 전송 성공 시 200 응답")
    public void sendEmailCode_success() throws Exception {
        // given
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO("user@test.com");

        // when & then
        mockMvc.perform(post("/api/auth/applicants/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("이메일 인증번호 전송에 성공했습니다."));

        verify(applicantAuthService).sendEmailCode(any(ApplicantEmailSendRequestDTO.class));
    }

    @Test
    @DisplayName("엣지 케이스: 이메일 형식 오류 -> 400 Bad Request")
    public void sendEmailCode_invalidEmail() throws Exception {
        // given
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO("not-email");

        // when & then
        mockMvc.perform(post("/api/auth/applicants/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 인증번호 확인 성공 시 verificationToken 반환")
    public void verifyEmailCode_success() throws Exception {
        // given
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO("user@test.com", "123456");
        when(applicantAuthService.verifyEmailCode(any()))
                .thenReturn(new ApplicantEmailVerifyResponseDTO("verification-token"));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationToken").value("verification-token"))
                .andExpect(jsonPath("$.message").value("이메일 인증번호 확인에 성공했습니다."));
    }

    @Test
    @DisplayName("엣지 케이스: 인증번호 만료 시 400 VERIFICATION_CODE_EXPIRED")
    public void verifyEmailCode_expired() throws Exception {
        // given
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO("user@test.com", "123456");
        when(applicantAuthService.verifyEmailCode(any()))
                .thenThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("엣지 케이스: 인증번호가 6자리 숫자가 아니면 400 Bad Request")
    public void verifyEmailCode_invalidCodeFormat() throws Exception {
        // given
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO("user@test.com", "12ab56");

        // when & then
        mockMvc.perform(post("/api/auth/applicants/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 1단계 성공 시 200 응답과 signupToken 반환")
    public void initSignup_success() throws Exception {
        // given
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                "new@test.com", "Pass1234!", "Pass1234!", "verification-token"
        );
        when(applicantAuthService.initSignup(any()))
                .thenReturn(new ApplicantSignupInitResponseDTO("signup-token"));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/signup/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.signupToken").value("signup-token"))
                .andExpect(jsonPath("$.message").value("회원가입 1단계에 성공했습니다."));
    }

    @Test
    @DisplayName("엣지 케이스: 회원가입 1단계 비밀번호 복잡도 미달 -> 400 Bad Request")
    public void initSignup_weakPassword() throws Exception {
        // given - 영문만, 숫자/특수문자 없음
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                "new@test.com", "onlyletters", "onlyletters", "token"
        );

        // when & then
        mockMvc.perform(post("/api/auth/applicants/signup/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("엣지 케이스: 회원가입 1단계에 이미 ACTIVE 가입된 이메일이면 409 EMAIL_ALREADY_EXISTS")
    public void initSignup_emailAlreadyExists() throws Exception {
        // given
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                "exists@test.com", "Pass1234!", "Pass1234!", "token"
        );
        when(applicantAuthService.initSignup(any()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/signup/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("회원가입 2단계 성공 시 accessToken 반환 + RefreshToken 쿠키 설정")
    public void completeSignup_success() throws Exception {
        // given
        ApplicantSignupCompleteRequestDTO request = new ApplicantSignupCompleteRequestDTO(
                "signup-token", allTrueAgreements()
        );
        when(applicantAuthService.completeSignup(any()))
                .thenReturn(new ApplicantLoginResult("access", "refresh", UserRole.APPLICANT));

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "refresh")
                .path("/").httpOnly(true).secure(false).sameSite("Lax").maxAge(1209600).build();
        when(cookieProvider.createRefreshTokenCookie("refresh")).thenReturn(refreshCookie);

        // when & then
        mockMvc.perform(post("/api/auth/applicants/signup/agreements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh")))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.role").value("APPLICANT"))
                .andExpect(jsonPath("$.message").value("회원가입에 성공했습니다."));
    }

    @Test
    @DisplayName("엣지 케이스: 회원가입 2단계에서 signupToken 만료/위변조 시 400 INVALID_SIGNUP_TOKEN")
    public void completeSignup_invalidSignupToken() throws Exception {
        // given
        ApplicantSignupCompleteRequestDTO request = new ApplicantSignupCompleteRequestDTO(
                "expired-token", allTrueAgreements()
        );
        when(applicantAuthService.completeSignup(any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/signup/agreements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SIGNUP_TOKEN"));
    }

    @Test
    @DisplayName("로그인 성공 시 accessToken 반환 + RefreshToken 쿠키 설정")
    public void login_success() throws Exception {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("user@test.com", "Pass1234!");
        when(applicantAuthService.login(any()))
                .thenReturn(new ApplicantLoginResult("access", "refresh", UserRole.APPLICANT));

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "refresh")
                .path("/").httpOnly(true).secure(false).sameSite("Lax").maxAge(1209600).build();
        when(cookieProvider.createRefreshTokenCookie("refresh")).thenReturn(refreshCookie);

        // when & then
        mockMvc.perform(post("/api/auth/applicants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh")))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.role").value("APPLICANT"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."));
    }

    @Test
    @DisplayName("엣지 케이스: 비밀번호 불일치 시 401 INVALID_PASSWORD")
    public void login_invalidPassword() throws Exception {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("user@test.com", "wrong");
        when(applicantAuthService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_PASSWORD));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));
    }

    @Test
    @DisplayName("회원탈퇴 성공 시 200 응답 + RefreshToken 쿠키 만료")
    public void withdraw_success() throws Exception {
        // given
        String publicId = "uuid-applicant-1";
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, UserRole.APPLICANT);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .path("/").httpOnly(true).secure(false).sameSite("Lax").maxAge(0).build();
        when(cookieProvider.createExpiredRefreshTokenCookie()).thenReturn(expiredCookie);

        try {
            // when & then
            mockMvc.perform(delete("/api/auth/applicants/me"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                    .andExpect(jsonPath("$.message").value("회원탈퇴에 성공했습니다."));

            verify(applicantAuthService).withdraw(publicId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("비밀번호 재설정 인증번호 전송 성공 시 200 응답")
    public void sendPasswordResetCode_success() throws Exception {
        // given
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO("user@test.com");

        // when & then
        mockMvc.perform(post("/api/auth/applicants/password/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("비밀번호 재설정 인증번호 전송에 성공했습니다."));

        verify(applicantAuthService).sendPasswordResetCode(any(ApplicantEmailSendRequestDTO.class));
    }

    @Test
    @DisplayName("엣지 케이스: 비밀번호 재설정 인증번호 전송 시 이메일 형식 오류 -> 400 Bad Request")
    public void sendPasswordResetCode_invalidEmail() throws Exception {
        // given
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO("not-email");

        // when & then
        mockMvc.perform(post("/api/auth/applicants/password/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("비밀번호 재설정 인증번호 확인 성공 시 verificationToken 반환")
    public void verifyPasswordResetCode_success() throws Exception {
        // given
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO("user@test.com", "123456");
        when(applicantAuthService.verifyEmailCode(any()))
                .thenReturn(new ApplicantEmailVerifyResponseDTO("verification-token"));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/password/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationToken").value("verification-token"))
                .andExpect(jsonPath("$.message").value("비밀번호 재설정 인증번호 확인에 성공했습니다."));
    }

    @Test
    @DisplayName("엣지 케이스: 비밀번호 재설정 인증번호 확인 시 인증번호 만료 -> 400 VERIFICATION_CODE_EXPIRED")
    public void verifyPasswordResetCode_expired() throws Exception {
        // given
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO("user@test.com", "123456");
        when(applicantAuthService.verifyEmailCode(any()))
                .thenThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        // when & then
        mockMvc.perform(post("/api/auth/applicants/password/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("비밀번호 변경 성공 시 200 응답")
    public void changePassword_success() throws Exception {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                "user@test.com", "verification-token", "Pass1234!", "Pass1234!"
        );

        // when & then
        mockMvc.perform(patch("/api/auth/applicants/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("비밀번호 변경에 성공했습니다."));

        verify(applicantAuthService).changePassword(any(ApplicantPasswordChangeRequestDTO.class));
    }

    @Test
    @DisplayName("엣지 케이스: 비밀번호 변경 시 비밀번호 복잡도 미달 -> 400 Bad Request")
    public void changePassword_weakPassword() throws Exception {
        // given - 영문만, 숫자/특수문자 없음
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                "user@test.com", "verification-token", "onlyletters", "onlyletters"
        );

        // when & then
        mockMvc.perform(patch("/api/auth/applicants/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("엣지 케이스: 비밀번호 변경 시 이메일 인증 토큰이 유효하지 않으면 403 EMAIL_NOT_VERIFIED")
    public void changePassword_emailNotVerified() throws Exception {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                "user@test.com", "invalid-token", "Pass1234!", "Pass1234!"
        );
        doThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(applicantAuthService).changePassword(any());

        // when & then
        mockMvc.perform(patch("/api/auth/applicants/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("로그인 상태 비밀번호 변경 성공 시 200 응답")
    public void changeMyPassword_success() throws Exception {
        // given
        String publicId = "uuid-applicant-1";
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, UserRole.APPLICANT);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        ApplicantPasswordUpdateRequestDTO request = new ApplicantPasswordUpdateRequestDTO(
                "Pass1234!", "Pass1234!"
        );

        try {
            // when & then
            mockMvc.perform(patch("/api/auth/applicants/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("비밀번호 변경에 성공했습니다."));

            verify(applicantAuthService).changeMyPassword(eq(publicId), any(ApplicantPasswordUpdateRequestDTO.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("엣지 케이스: 로그인 상태 비밀번호 변경 시 미인증이면 401 UNAUTHORIZED")
    public void changeMyPassword_unauthorized() throws Exception {
        // given
        ApplicantPasswordUpdateRequestDTO request = new ApplicantPasswordUpdateRequestDTO(
                "Pass1234!", "Pass1234!"
        );

        // when & then
        mockMvc.perform(patch("/api/auth/applicants/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("엣지 케이스: 로그인 상태 비밀번호 변경 시 비밀번호 복잡도 미달 -> 400 VALIDATION_FAILED")
    public void changeMyPassword_weakPassword() throws Exception {
        // given
        String publicId = "uuid-applicant-1";
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, UserRole.APPLICANT);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 영문만, 숫자/특수문자 없음
        ApplicantPasswordUpdateRequestDTO request = new ApplicantPasswordUpdateRequestDTO(
                "onlyletters", "onlyletters"
        );

        try {
            // when & then
            mockMvc.perform(patch("/api/auth/applicants/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static ApplicantAgreementRequestDTO allTrueAgreements() {
        return new ApplicantAgreementRequestDTO(true, true, true, true, true, true);
    }
}
