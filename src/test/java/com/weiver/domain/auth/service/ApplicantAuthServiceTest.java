package com.weiver.domain.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantAgreementRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.service.ApplicantProvider;
import com.weiver.applicant.type.ApplicantStatus;
import com.weiver.auth.dto.request.ApplicantEmailSendRequestDTO;
import com.weiver.auth.dto.request.ApplicantPasswordChangeRequestDTO;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.repository.ApplicantSignupTokenRepository;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.auth.service.ApplicantVerificationCodeGenerator;
import com.weiver.auth.service.EmailVerificationService;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicantAuthService 비밀번호 재설정/변경 단위 테스트")
class ApplicantAuthServiceTest {

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAgreementRepository applicantAgreementRepository;
    @Mock private ApplicantEmailVerificationRepository emailVerificationRepository;
    @Mock private ApplicantSignupTokenRepository signupTokenRepository;
    @Mock private TokenVersionRepository tokenVersionRepository;
    @Mock private ApplicantVerificationCodeGenerator codeGenerator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ApplicantProvider applicantProvider;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks private ApplicantAuthService applicantAuthService;

    private static final String EMAIL = "user@example.com";

    private Applicant activeApplicant() {
        return Applicant.builder()
                .email(EMAIL)
                .password("encoded-old")
                .role(UserRole.APPLICANT)
                .status(ApplicantStatus.ACTIVE)
                .build();
    }

    // ----------- sendPasswordResetCode -----------

    @Test
    @DisplayName("sendPasswordResetCode: ACTIVE 계정이 존재하면 코드 저장 및 인증번호 발송을 수행한다")
    void sendPasswordResetCode_activeAccount_sendsCode() {
        // given
        given(applicantRepository.findByEmailAndDeletedFalse(EMAIL))
                .willReturn(Optional.of(activeApplicant()));
        given(codeGenerator.generateCode()).willReturn("123456");

        // when
        applicantAuthService.sendPasswordResetCode(new ApplicantEmailSendRequestDTO(EMAIL));

        // then
        verify(emailVerificationRepository).deleteAttemptCount(EMAIL);
        verify(emailVerificationRepository).saveCode(eq(EMAIL), eq("123456"), any(Duration.class));
        verify(emailVerificationService).sendVerificationCode(EMAIL, "123456");
    }

    @Test
    @DisplayName("sendPasswordResetCode: 가입되지 않은 이메일이면 이메일 열거 방지를 위해 발송 없이 성공 반환한다")
    void sendPasswordResetCode_unregisteredEmail_doesNotSend() {
        // given
        given(applicantRepository.findByEmailAndDeletedFalse(EMAIL))
                .willReturn(Optional.empty());

        // when
        applicantAuthService.sendPasswordResetCode(new ApplicantEmailSendRequestDTO(EMAIL));

        // then
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
        verify(emailVerificationRepository, never()).saveCode(anyString(), anyString(), any(Duration.class));
        verify(codeGenerator, never()).generateCode();
    }

    @Test
    @DisplayName("sendPasswordResetCode: PENDING 계정만 있으면 ACTIVE가 아니므로 발송하지 않는다")
    void sendPasswordResetCode_pendingAccount_doesNotSend() {
        // given
        Applicant pending = Applicant.builder()
                .email(EMAIL)
                .password("encoded-old")
                .role(UserRole.APPLICANT)
                .status(ApplicantStatus.PENDING)
                .build();
        given(applicantRepository.findByEmailAndDeletedFalse(EMAIL))
                .willReturn(Optional.of(pending));

        // when
        applicantAuthService.sendPasswordResetCode(new ApplicantEmailSendRequestDTO(EMAIL));

        // then
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
        verify(codeGenerator, never()).generateCode();
    }

    // ----------- changePassword -----------

    @Test
    @DisplayName("changePassword: 정상 요청이면 토큰을 소비하고 새 비밀번호를 인코딩해 갱신한다")
    void changePassword_success() {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                EMAIL, "verification-token", "Pass1234!", "Pass1234!"
        );
        given(emailVerificationRepository.findAndDeleteVerifiedToken("verification-token"))
                .willReturn(Optional.of(EMAIL));
        Applicant applicant = activeApplicant();
        given(applicantRepository.findByEmailAndDeletedFalse(EMAIL))
                .willReturn(Optional.of(applicant));
        given(passwordEncoder.encode("Pass1234!")).willReturn("encoded-new");

        // when
        applicantAuthService.changePassword(request);

        // then
        verify(emailVerificationRepository, times(1)).findAndDeleteVerifiedToken("verification-token");
        verify(passwordEncoder).encode("Pass1234!");
        assertThat(applicant.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    @DisplayName("changePassword: 인증 토큰이 존재하지 않으면 EMAIL_NOT_VERIFIED 예외가 발생한다")
    void changePassword_tokenNotFound_throwsEmailNotVerified() {
        // given
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                EMAIL, "invalid-token", "Pass1234!", "Pass1234!"
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
                EMAIL, "verification-token", "Pass1234!", "Pass1234!"
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
                EMAIL, "verification-token", "Pass1234!", "Different1!"
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
        ApplicantPasswordChangeRequestDTO request = new ApplicantPasswordChangeRequestDTO(
                EMAIL, "verification-token", "Pass1234!", "Pass1234!"
        );
        given(emailVerificationRepository.findAndDeleteVerifiedToken("verification-token"))
                .willReturn(Optional.of(EMAIL));
        given(applicantRepository.findByEmailAndDeletedFalse(EMAIL))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.changePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);

        verify(passwordEncoder, never()).encode(anyString());
    }
}
