package com.weiver.domain.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAgreement;
import com.weiver.applicant.repository.ApplicantAgreementRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ApplicantStatus;
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
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.repository.ApplicantSignupTokenRepository;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.auth.service.ApplicantVerificationCodeGenerator;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.auth.service.EmailVerificationService;
import com.weiver.applicant.service.ApplicantProvider;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicantAuthServiceTest {

    @InjectMocks
    private ApplicantAuthService applicantAuthService;

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAgreementRepository applicantAgreementRepository;
    @Mock private ApplicantEmailVerificationRepository emailVerificationRepository;
    @Mock private ApplicantSignupTokenRepository signupTokenRepository;
    @Mock private ApplicantVerificationCodeGenerator codeGenerator;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenVersionRepository tokenVersionRepository;
    @Mock private ApplicantProvider applicantProvider;

    @BeforeEach
    void enableTestEmailBypass() {
        // @Value 프로퍼티는 Mockito가 주입하지 않으므로, 테스트 도메인 우회 검증을 위해 플래그를 활성화한다.
        ReflectionTestUtils.setField(applicantAuthService, "testEmailBypassEnabled", true);
    }

    @Test
    @DisplayName("이메일 인증번호 전송 성공 시 시도 카운터 초기화 후 코드 저장 + 메일 발송")
    public void sendEmailCode_success() {
        // given
        String email = "user@test.com";
        String code = "123456";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.empty());
        when(codeGenerator.generateCode()).thenReturn(code);

        // when
        applicantAuthService.sendEmailCode(request);

        // then
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository).saveCode(eq(email), eq(code), any(Duration.class));
        verify(emailVerificationService).sendVerificationCode(email, code);
        verify(emailVerificationRepository, never()).deleteCode(anyString());
    }

    @Test
    @DisplayName("ACTIVE 상태의 동일 이메일이면 EMAIL_ALREADY_EXISTS 예외 발생")
    public void sendEmailCode_emailAlreadyExists() {
        // given
        String email = "exists@test.com";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        Applicant active = activeApplicant(email);
        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(active));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.sendEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage);

        verify(codeGenerator, never()).generateCode();
        verify(emailVerificationRepository, never()).saveCode(anyString(), anyString(), any(Duration.class));
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    @DisplayName("PENDING 상태의 동일 이메일이면 인증번호 전송을 허용한다.")
    public void sendEmailCode_allowsPending() {
        // given
        String email = "pending@test.com";
        String code = "123456";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        Applicant pending = pendingApplicant(email);
        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(pending));
        when(codeGenerator.generateCode()).thenReturn(code);

        // when
        applicantAuthService.sendEmailCode(request);

        // then
        verify(emailVerificationRepository).saveCode(eq(email), eq(code), any(Duration.class));
        verify(emailVerificationService).sendVerificationCode(email, code);
    }

    @Test
    @DisplayName("테스트 도메인 이메일이면 인증번호 생성/저장/메일 발송 없이 성공한다.")
    public void sendEmailCode_testBypassEmailSkipsEmailSend() {
        // given
        String email = "qa@weiver.test";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.empty());

        // when
        applicantAuthService.sendEmailCode(request);

        // then
        verify(emailVerificationRepository).deleteCode(email);
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(codeGenerator, never()).generateCode();
        verify(emailVerificationRepository, never()).saveCode(anyString(), anyString(), any(Duration.class));
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    @DisplayName("메일 발송 실패 시 저장한 코드를 삭제하고 EMAIL_SEND_FAILED 예외 발생")
    public void sendEmailCode_mailSendFails() {
        // given
        String email = "user@test.com";
        String code = "123456";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.empty());
        when(codeGenerator.generateCode()).thenReturn(code);
        doThrow(new RuntimeException("smtp down")).when(emailVerificationService).sendVerificationCode(anyString(), anyString());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.sendEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_SEND_FAILED.defaultMessage);

        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository).saveCode(eq(email), eq(code), any(Duration.class));
        verify(emailVerificationRepository).deleteCode(email);
    }

    @Test
    @DisplayName("이메일 인증번호 검증 성공 시 코드/카운터를 정리하고 verificationToken을 발급한다.")
    public void verifyEmailCode_success() {
        // given
        String email = "user@test.com";
        String code = "123456";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, code);

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.of(code));
        when(emailVerificationRepository.incrementAttemptCount(eq(email), any(Duration.class))).thenReturn(1L);

        // when
        ApplicantEmailVerifyResponseDTO response = applicantAuthService.verifyEmailCode(request);

        // then
        assertThat(response.verificationToken()).isNotBlank();
        verify(emailVerificationRepository).findCodeByEmail(email);
        verify(emailVerificationRepository).incrementAttemptCount(eq(email), any(Duration.class));
        verify(emailVerificationRepository).saveVerifiedToken(eq(response.verificationToken()), eq(email), any(Duration.class));
        verify(emailVerificationRepository).deleteCode(email);
        verify(emailVerificationRepository).deleteAttemptCount(email);
    }

    @Test
    @DisplayName("테스트 도메인 이메일은 고정 인증번호로 verificationToken을 발급한다.")
    public void verifyEmailCode_testBypassEmailSuccess() {
        // given
        String email = "qa@weiver.test";
        String code = "000000";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, code);

        // when
        ApplicantEmailVerifyResponseDTO response = applicantAuthService.verifyEmailCode(request);

        // then
        assertThat(response.verificationToken()).isNotBlank();
        verify(emailVerificationRepository).deleteCode(email);
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository).saveVerifiedToken(eq(response.verificationToken()), eq(email), any(Duration.class));
        verify(emailVerificationRepository, never()).findCodeByEmail(anyString());
        verify(emailVerificationRepository, never()).incrementAttemptCount(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("테스트 도메인 이메일도 고정 인증번호가 틀리면 INVALID_VERIFICATION_CODE 예외")
    public void verifyEmailCode_testBypassEmailInvalidCode() {
        // given
        String email = "qa@weiver.test";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "111111");

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_VERIFICATION_CODE.defaultMessage);

        verify(emailVerificationRepository, never()).findCodeByEmail(anyString());
        verify(emailVerificationRepository, never()).incrementAttemptCount(anyString(), any(Duration.class));
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("저장된 코드가 없으면 VERIFICATION_CODE_EXPIRED 예외 (카운터 증가 없음)")
    public void verifyEmailCode_expired() {
        // given
        String email = "user@test.com";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "123456");

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.VERIFICATION_CODE_EXPIRED.defaultMessage);

        verify(emailVerificationRepository, never()).incrementAttemptCount(anyString(), any(Duration.class));
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("코드 불일치 시 INVALID_VERIFICATION_CODE 예외 (코드/카운터는 살아있음)")
    public void verifyEmailCode_invalidCode() {
        // given
        String email = "user@test.com";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "111111");

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.of("999999"));
        when(emailVerificationRepository.incrementAttemptCount(eq(email), any(Duration.class))).thenReturn(2L);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_VERIFICATION_CODE.defaultMessage);

        verify(emailVerificationRepository).findCodeByEmail(email);
        verify(emailVerificationRepository).incrementAttemptCount(eq(email), any(Duration.class));
        verify(emailVerificationRepository, never()).deleteCode(anyString());
        verify(emailVerificationRepository, never()).deleteAttemptCount(anyString());
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("시도 횟수 한도 초과 시 코드/카운터 소각 후 TOO_MANY_VERIFICATION_ATTEMPTS 예외")
    public void verifyEmailCode_tooManyAttempts() {
        // given
        String email = "user@test.com";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "111111");

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.of("999999"));
        // 최대 5회 -> 6번째 시도부터 차단
        when(emailVerificationRepository.incrementAttemptCount(eq(email), any(Duration.class))).thenReturn(6L);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS.defaultMessage);

        verify(emailVerificationRepository).deleteCode(email);
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 성공 시 PENDING 회원을 저장하고 signupToken을 발급한다.")
    public void initSignup_success() {
        // given
        String email = "new@test.com";
        String password = "Pass1234!";
        String verificationToken = "verification-token";
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                email, password, password, verificationToken
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(verificationToken))
                .thenReturn(Optional.of(email));
        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("encoded");

        String expectedPublicId = "test-public-id";
        Applicant saved = Applicant.builder()
                .email(email)
                .password("encoded")
                .role(UserRole.APPLICANT)
                .publicId(expectedPublicId)
                .build();
        ReflectionTestUtils.setField(saved, "applicantId", 1L);
        when(applicantRepository.save(any(Applicant.class))).thenReturn(saved);

        // when
        ApplicantSignupInitResponseDTO response = applicantAuthService.initSignup(request);

        // then
        assertThat(response.signupToken()).isNotBlank();
        verify(emailVerificationRepository).findAndDeleteVerifiedToken(verificationToken);
        verify(applicantRepository).save(any(Applicant.class));
        verify(applicantRepository).flush();
        verify(signupTokenRepository).save(eq(response.signupToken()), eq(expectedPublicId), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 재시도: 기존 PENDING 회원이 있으면 비밀번호를 갱신하고 새 signupToken을 발급한다.")
    public void initSignup_overwritesPending() {
        // given
        String email = "pending@test.com";
        String password = "NewPass1234!";
        String verificationToken = "verification-token";
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                email, password, password, verificationToken
        );

        Applicant existingPending = pendingApplicant(email);
        when(emailVerificationRepository.findAndDeleteVerifiedToken(verificationToken))
                .thenReturn(Optional.of(email));
        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(existingPending));
        when(passwordEncoder.encode(password)).thenReturn("encoded-new");

        // when
        ApplicantSignupInitResponseDTO response = applicantAuthService.initSignup(request);

        // then
        assertThat(response.signupToken()).isNotBlank();
        assertThat(existingPending.getPassword()).isEqualTo("encoded-new");
        verify(applicantRepository, never()).save(any(Applicant.class));
        verify(applicantRepository, never()).flush();
        verify(signupTokenRepository).save(eq(response.signupToken()), eq(existingPending.getPublicId()), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 - ACTIVE 상태 회원이 있으면 EMAIL_ALREADY_EXISTS 예외")
    public void initSignup_activeAlreadyExists() {
        // given
        String email = "active@test.com";
        String password = "Pass1234!";
        String verificationToken = "verification-token";
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                email, password, password, verificationToken
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(verificationToken))
                .thenReturn(Optional.of(email));
        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(activeApplicant(email)));
        when(passwordEncoder.encode(password)).thenReturn("encoded");

        // when & then
        assertThatThrownBy(() -> applicantAuthService.initSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage);

        verify(signupTokenRepository, never()).save(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 - 비밀번호와 비밀번호 확인이 다르면 PASSWORD_CONFIRM_NOT_MATCH 예외 (토큰 소비 안 함)")
    public void initSignup_passwordConfirmMismatch() {
        // given
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                "user@test.com", "Pass1234!", "Different1!", "token"
        );

        // when & then
        assertThatThrownBy(() -> applicantAuthService.initSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH.defaultMessage);

        verify(emailVerificationRepository, never()).findAndDeleteVerifiedToken(anyString());
        verify(applicantRepository, never()).save(any(Applicant.class));
        verify(signupTokenRepository, never()).save(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 - verification 토큰이 Redis에 없으면 EMAIL_NOT_VERIFIED 예외")
    public void initSignup_verificationTokenNotFound() {
        // given
        String token = "missing-token";
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                "user@test.com", "Pass1234!", "Pass1234!", token
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.initSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_NOT_VERIFIED.defaultMessage);

        verify(applicantRepository, never()).save(any(Applicant.class));
        verify(signupTokenRepository, never()).save(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 - verification 토큰의 이메일과 요청 이메일이 다르면 EMAIL_NOT_VERIFIED 예외")
    public void initSignup_emailMismatch() {
        // given
        String token = "token";
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                "request@test.com", "Pass1234!", "Pass1234!", token
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.of("other@test.com"));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.initSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_NOT_VERIFIED.defaultMessage);

        verify(applicantRepository, never()).save(any(Applicant.class));
        verify(signupTokenRepository, never()).save(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 1단계 - DB unique 제약 위반(race condition) 시 EMAIL_ALREADY_EXISTS 예외")
    public void initSignup_uniqueViolationOnRace() {
        // given
        String email = "race@test.com";
        String token = "token";
        ApplicantSignupInitRequestDTO request = new ApplicantSignupInitRequestDTO(
                email, "Pass1234!", "Pass1234!", token
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.of(email));
        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(applicantRepository.save(any(Applicant.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.initSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage);

        verify(signupTokenRepository, never()).save(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 2단계 성공 시 signupToken 소비 -> Agreement 저장 -> Applicant ACTIVE 전환 + 토큰 발급")
    public void completeSignup_success() {
        // given
        String signupToken = "signup-token";
        String publicId = "applicant-public-id";
        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 14;
        ApplicantSignupCompleteRequestDTO request = new ApplicantSignupCompleteRequestDTO(
                signupToken, allTrueAgreements()
        );

        Applicant pending = pendingApplicant("new@test.com");
        ReflectionTestUtils.setField(pending, "publicId", publicId);

        when(signupTokenRepository.findAndDelete(signupToken)).thenReturn(Optional.of(publicId));
        when(applicantRepository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.of(pending));
        when(tokenVersionRepository.getCurrentVersion(publicId, UserRole.APPLICANT)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, UserRole.APPLICANT, tokenVersion)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(publicId, UserRole.APPLICANT, tokenVersion)).thenReturn("refresh");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);

        // when
        ApplicantLoginResult result = applicantAuthService.completeSignup(request);

        // then
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.role()).isEqualTo(UserRole.APPLICANT);
        assertThat(pending.getStatus()).isEqualTo(ApplicantStatus.ACTIVE);
        verify(signupTokenRepository).findAndDelete(signupToken);
        verify(applicantAgreementRepository).save(any(ApplicantAgreement.class));
        verify(refreshTokenRepository).save(publicId, UserRole.APPLICANT, "refresh", ttlMillis);
    }

    @Test
    @DisplayName("회원가입 2단계 - 필수 약관 미동의 시 REQUIRED_AGREEMENT_NOT_ACCEPTED 예외 (토큰 소비 안 함)")
    public void completeSignup_requiredAgreementNotAccepted() {
        // given
        ApplicantAgreementRequestDTO partial = new ApplicantAgreementRequestDTO(
                true, true, true, true, false, true
        );
        ApplicantSignupCompleteRequestDTO request = new ApplicantSignupCompleteRequestDTO(
                "signup-token", partial
        );

        // when & then
        assertThatThrownBy(() -> applicantAuthService.completeSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED.defaultMessage);

        verify(signupTokenRepository, never()).findAndDelete(anyString());
        verify(applicantAgreementRepository, never()).save(any(ApplicantAgreement.class));
    }

    @Test
    @DisplayName("회원가입 2단계 - signupToken이 만료/존재하지 않으면 INVALID_SIGNUP_TOKEN 예외")
    public void completeSignup_invalidSignupToken() {
        // given
        String signupToken = "missing-token";
        ApplicantSignupCompleteRequestDTO request = new ApplicantSignupCompleteRequestDTO(
                signupToken, allTrueAgreements()
        );

        when(signupTokenRepository.findAndDelete(signupToken)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.completeSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_SIGNUP_TOKEN.defaultMessage);

        verify(applicantAgreementRepository, never()).save(any(ApplicantAgreement.class));
    }

    @Test
    @DisplayName("회원가입 2단계 - Applicant가 PENDING이 아니면 INVALID_SIGNUP_TOKEN 예외")
    public void completeSignup_applicantNotPending() {
        // given
        String signupToken = "signup-token";
        String publicId = "applicant-public-id";
        ApplicantSignupCompleteRequestDTO request = new ApplicantSignupCompleteRequestDTO(
                signupToken, allTrueAgreements()
        );

        Applicant active = activeApplicant("active@test.com");
        ReflectionTestUtils.setField(active, "publicId", publicId);

        when(signupTokenRepository.findAndDelete(signupToken)).thenReturn(Optional.of(publicId));
        when(applicantRepository.findByPublicIdAndDeletedFalse(publicId)).thenReturn(Optional.of(active));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.completeSignup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_SIGNUP_TOKEN.defaultMessage);

        verify(applicantAgreementRepository, never()).save(any(ApplicantAgreement.class));
    }

    @Test
    @DisplayName("로그인 성공 시 현재 tokenVersion으로 access/refresh 토큰을 발급하고 RefreshToken을 저장한다.")
    public void login_success() {
        // given
        String email = "user@test.com";
        String rawPassword = "Pass1234!";
        String publicId = "uuid-applicant-7";
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO(email, rawPassword);

        Applicant applicant = Applicant.builder()
                .email(email)
                .password("encoded")
                .role(UserRole.APPLICANT)
                .publicId(publicId)
                .status(ApplicantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 7L);

        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 14;

        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(applicant));
        when(passwordEncoder.matches(rawPassword, "encoded")).thenReturn(true);
        when(tokenVersionRepository.getCurrentVersion(publicId, UserRole.APPLICANT)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, UserRole.APPLICANT, tokenVersion)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(publicId, UserRole.APPLICANT, tokenVersion)).thenReturn("refresh");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);

        // when
        ApplicantLoginResult result = applicantAuthService.login(request);

        // then
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.role()).isEqualTo(UserRole.APPLICANT);
        verify(refreshTokenRepository).save(publicId, UserRole.APPLICANT, "refresh", ttlMillis);
        verify(tokenVersionRepository, never()).increaseVersion(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 이메일 열거 방지를 위해 INVALID_PASSWORD 예외")
    public void login_applicantNotFound() {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("none@test.com", "Pass1234!");
        when(applicantRepository.findByEmailAndDeletedFalse("none@test.com")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_PASSWORD.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외")
    public void login_invalidPassword() {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("user@test.com", "wrong");
        Applicant applicant = Applicant.builder()
                .email("user@test.com")
                .password("encoded")
                .role(UserRole.APPLICANT)
                .status(ApplicantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 1L);

        when(applicantRepository.findByEmailAndDeletedFalse("user@test.com")).thenReturn(Optional.of(applicant));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_PASSWORD.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("PENDING 상태의 회원이 로그인 시도하면 SIGNUP_NOT_COMPLETED 예외")
    public void login_pendingBlocked() {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("pending@test.com", "Pass1234!");
        Applicant pending = pendingApplicant("pending@test.com");

        when(applicantRepository.findByEmailAndDeletedFalse("pending@test.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Pass1234!", pending.getPassword())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SIGNUP_NOT_COMPLETED.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("회원탈퇴 성공 시 RefreshToken 삭제 후 Applicant를 soft-delete 처리한다.")
    public void withdraw_success() {
        // given
        String publicId = "uuid-applicant-5";
        Applicant applicant = Applicant.builder()
                .email("user@test.com")
                .password("encoded")
                .role(UserRole.APPLICANT)
                .publicId(publicId)
                .status(ApplicantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 5L);

        when(applicantProvider.findByPublicId(publicId)).thenReturn(applicant);

        // when
        applicantAuthService.withdraw(publicId);

        // then
        verify(refreshTokenRepository).deleteByPublicId(publicId, UserRole.APPLICANT);
        assertThat(applicant.isDeleted()).isTrue();
        assertThat(applicant.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴 대상 사용자가 없으면 APPLICANT_NOT_FOUND 예외")
    public void withdraw_applicantNotFound() {
        // given
        String publicId = "uuid-missing";
        when(applicantProvider.findByPublicId(publicId))
                .thenThrow(new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.withdraw(publicId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.APPLICANT_NOT_FOUND.defaultMessage);

        verify(refreshTokenRepository, never()).deleteByPublicId(anyString(), any(UserRole.class));
    }

    // ----------- sendPasswordResetCode -----------

    @Test
    @DisplayName("sendPasswordResetCode: ACTIVE 계정이 존재하면 코드 저장 및 인증번호 발송을 수행한다")
    void sendPasswordResetCode_activeAccount_sendsCode() {
        // given
        String email = "reset@example.com";
        given(applicantRepository.findByEmailAndDeletedFalse(email))
                .willReturn(Optional.of(activeApplicant(email)));
        given(codeGenerator.generateCode()).willReturn("123456");

        // when
        applicantAuthService.sendPasswordResetCode(new ApplicantEmailSendRequestDTO(email));

        // then
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository).saveCode(eq(email), eq("123456"), any(Duration.class));
        verify(emailVerificationService).sendVerificationCode(email, "123456");
    }

    @Test
    @DisplayName("sendPasswordResetCode: 가입되지 않은 이메일이면 이메일 열거 방지를 위해 발송 없이 성공 반환한다")
    void sendPasswordResetCode_unregisteredEmail_doesNotSend() {
        // given
        String email = "reset@example.com";
        given(applicantRepository.findByEmailAndDeletedFalse(email))
                .willReturn(Optional.empty());

        // when
        applicantAuthService.sendPasswordResetCode(new ApplicantEmailSendRequestDTO(email));

        // then
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
        verify(emailVerificationRepository, never()).saveCode(anyString(), anyString(), any(Duration.class));
        verify(codeGenerator, never()).generateCode();
    }

    @Test
    @DisplayName("sendPasswordResetCode: PENDING 계정만 있으면 ACTIVE가 아니므로 발송하지 않는다")
    void sendPasswordResetCode_pendingAccount_doesNotSend() {
        // given
        String email = "reset@example.com";
        given(applicantRepository.findByEmailAndDeletedFalse(email))
                .willReturn(Optional.of(pendingApplicant(email)));

        // when
        applicantAuthService.sendPasswordResetCode(new ApplicantEmailSendRequestDTO(email));

        // then
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
        verify(codeGenerator, never()).generateCode();
    }

    // ----------- changePassword -----------

    @Test
    @DisplayName("changePassword: 정상 요청이면 토큰을 소비하고 새 비밀번호를 인코딩해 갱신한다")
    void changePassword_success() {
        // given
        String email = "reset@example.com";
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                email, "verification-token", "Pass1234!", "Pass1234!"
        );
        given(emailVerificationRepository.findAndDeleteVerifiedToken("verification-token"))
                .willReturn(Optional.of(email));
        Applicant applicant = activeApplicant(email);
        given(applicantRepository.findByEmailAndDeletedFalse(email))
                .willReturn(Optional.of(applicant));
        given(passwordEncoder.encode("Pass1234!")).willReturn("encoded-new");

        // when
        applicantAuthService.changePassword(request);

        // then
        verify(emailVerificationRepository, times(1)).findAndDeleteVerifiedToken("verification-token");
        verify(passwordEncoder).encode("Pass1234!");
        assertThat(applicant.getPassword()).isEqualTo("encoded-new");
        verify(tokenVersionRepository).increaseVersion(applicant.getPublicId(), UserRole.APPLICANT);
        verify(refreshTokenRepository).deleteByPublicId(applicant.getPublicId(), UserRole.APPLICANT);
    }

    @Test
    @DisplayName("changePassword: 인증 토큰이 존재하지 않으면 EMAIL_NOT_VERIFIED 예외가 발생한다")
    void changePassword_tokenNotFound_throwsEmailNotVerified() {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                "reset@example.com", "invalid-token", "Pass1234!", "Pass1234!"
        );
        given(emailVerificationRepository.findAndDeleteVerifiedToken("invalid-token"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("changePassword: 토큰의 이메일과 요청 이메일이 다르면 EMAIL_NOT_VERIFIED 예외가 발생한다")
    void changePassword_emailMismatch_throwsEmailNotVerified() {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                "reset@example.com", "verification-token", "Pass1234!", "Pass1234!"
        );
        given(emailVerificationRepository.findAndDeleteVerifiedToken("verification-token"))
                .willReturn(Optional.of("other@example.com"));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("changePassword: 새 비밀번호와 확인값이 다르면 PASSWORD_CONFIRM_NOT_MATCH 예외가 발생한다")
    void changePassword_confirmMismatch_throwsPasswordConfirmNotMatch() {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                "reset@example.com", "verification-token", "Pass1234!", "Different1!"
        );

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);

        verify(emailVerificationRepository, never()).findAndDeleteVerifiedToken(anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("changePassword: ACTIVE 상태의 구직자가 없으면 APPLICANT_NOT_FOUND 예외가 발생한다")
    void changePassword_noActiveApplicant_throwsApplicantNotFound() {
        // given
        String email = "reset@example.com";
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                email, "verification-token", "Pass1234!", "Pass1234!"
        );
        given(emailVerificationRepository.findAndDeleteVerifiedToken("verification-token"))
                .willReturn(Optional.of(email));
        given(applicantRepository.findByEmailAndDeletedFalse(email))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);

        verify(passwordEncoder, never()).encode(anyString());
    }

    // ----------- changeMyPassword -----------

    @Test
    @DisplayName("changeMyPassword: 현재 비밀번호가 일치하면 새 비밀번호를 인코딩해 갱신하고 기존 세션/토큰을 무효화한다")
    void changeMyPassword_success() {
        // given
        String publicId = "uuid-applicant-9";
        Applicant applicant = Applicant.builder()
                .email("me@test.com")
                .password("encoded-old")
                .role(UserRole.APPLICANT)
                .publicId(publicId)
                .status(ApplicantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 9L);

        ApplicantPasswordUpdateRequestDTO request = new ApplicantPasswordUpdateRequestDTO(
                "OldPass1234!", "Pass1234!", "Pass1234!"
        );
        given(applicantProvider.findByPublicId(publicId)).willReturn(applicant);
        given(passwordEncoder.matches("OldPass1234!", "encoded-old")).willReturn(true);
        given(passwordEncoder.encode("Pass1234!")).willReturn("encoded-new");

        // when
        applicantAuthService.changeMyPassword(publicId, request);

        // then
        verify(passwordEncoder).matches("OldPass1234!", "encoded-old");
        verify(passwordEncoder).encode("Pass1234!");
        assertThat(applicant.getPassword()).isEqualTo("encoded-new");
        verify(tokenVersionRepository).increaseVersion(publicId, UserRole.APPLICANT);
        verify(refreshTokenRepository).deleteByPublicId(publicId, UserRole.APPLICANT);
    }

    @Test
    @DisplayName("changeMyPassword: 현재 비밀번호가 일치하지 않으면 INVALID_CURRENT_PASSWORD 예외 (인코딩/갱신 없음)")
    void changeMyPassword_wrongCurrentPassword_throwsInvalidCurrentPassword() {
        // given
        String publicId = "uuid-applicant-9";
        Applicant applicant = Applicant.builder()
                .email("me@test.com")
                .password("encoded-old")
                .role(UserRole.APPLICANT)
                .publicId(publicId)
                .status(ApplicantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 9L);

        ApplicantPasswordUpdateRequestDTO request = new ApplicantPasswordUpdateRequestDTO(
                "WrongOld1!", "Pass1234!", "Pass1234!"
        );
        given(applicantProvider.findByPublicId(publicId)).willReturn(applicant);
        given(passwordEncoder.matches("WrongOld1!", "encoded-old")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changeMyPassword(publicId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);

        verify(passwordEncoder, never()).encode(anyString());
        assertThat(applicant.getPassword()).isEqualTo("encoded-old");
        verify(tokenVersionRepository, never()).increaseVersion(anyString(), any(UserRole.class));
        verify(refreshTokenRepository, never()).deleteByPublicId(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("changeMyPassword: 새 비밀번호와 확인값이 다르면 PASSWORD_CONFIRM_NOT_MATCH 예외 (대상 조회 이전 차단)")
    void changeMyPassword_confirmMismatch_throwsPasswordConfirmNotMatch() {
        // given
        ApplicantPasswordUpdateRequestDTO request = new ApplicantPasswordUpdateRequestDTO(
                "OldPass1234!", "Pass1234!", "Different1!"
        );

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changeMyPassword("uuid-applicant-9", request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);

        verify(applicantProvider, never()).findByPublicId(anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    private static ApplicantAgreementRequestDTO allTrueAgreements() {
        return new ApplicantAgreementRequestDTO(true, true, true, true, true, true);
    }

    private static Applicant activeApplicant(String email) {
        Applicant applicant = Applicant.builder()
                .email(email)
                .password("encoded")
                .role(UserRole.APPLICANT)
                .status(ApplicantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 10L);
        return applicant;
    }

    private static Applicant pendingApplicant(String email) {
        Applicant applicant = Applicant.builder()
                .email(email)
                .password("encoded-old")
                .role(UserRole.APPLICANT)
                .status(ApplicantStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 20L);
        return applicant;
    }
}
