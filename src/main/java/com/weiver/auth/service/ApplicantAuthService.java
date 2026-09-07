package com.weiver.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAgreement;
import com.weiver.applicant.repository.ApplicantAgreementRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ApplicantStatus;
import com.weiver.auth.dto.request.*;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupInitResponseDTO;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.repository.ApplicantSignupTokenRepository;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.UserRole;
import com.weiver.applicant.service.ApplicantProvider;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicantAuthService {

    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    // 배포 이후 테스트 우회가 필요 없어지면 TEST_EMAIL_* 상수, isTestEmail(), send/verify 분기, 관련 테스트를 함께 삭제하세요.
    private static final String TEST_EMAIL_DOMAIN = "weiver.test";
    private static final String TEST_EMAIL_VERIFICATION_CODE = "000000";

    private final ApplicantRepository applicantRepository;
    private final ApplicantAgreementRepository applicantAgreementRepository;
    private final ApplicantEmailVerificationRepository emailVerificationRepository;
    private final ApplicantSignupTokenRepository signupTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;
    private final ApplicantVerificationCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicantProvider applicantProvider;
    private final EmailVerificationService emailVerificationService;

    // 비-prod 테스트 편의용 이메일 인증 우회 스위치. prod에서는 반드시 false여야 한다(기본 false).
    @Value("${weiver.auth.test-email-bypass.enabled:false}")
    private boolean testEmailBypassEnabled;

    public void sendEmailCode(ApplicantEmailSendRequestDTO request) {
        String email = request.email();

        applicantRepository.findByEmailAndDeletedFalse(email)
                .filter(existing -> existing.getStatus() == ApplicantStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                });

        if (testEmailBypassEnabled && isTestEmail(email)) {
            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            return;
        }

        String code = codeGenerator.generateCode();

        emailVerificationRepository.deleteAttemptCount(email);
        emailVerificationRepository.saveCode(email, code, EMAIL_CODE_TTL);

        try {
            emailVerificationService.sendVerificationCode(email, code);
        } catch (Exception e) {
            log.warn("[EmailVerification] 인증번호 메일 발송 실패 email={} cause={}", email, e.toString());
            emailVerificationRepository.deleteCode(email);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void sendPasswordResetCode(ApplicantEmailSendRequestDTO request) {
        String email = request.email();

        boolean activeApplicantExists = applicantRepository.findByEmailAndDeletedFalse(email)
                .filter(existing -> existing.getStatus() == ApplicantStatus.ACTIVE)
                .isPresent();

        // 이메일 열거(enumeration) 방지: 가입되지 않은 이메일도 성공 응답을 반환하되 실제 코드는 발송하지 않는다.
        if (!activeApplicantExists) {
            return;
        }

        if (testEmailBypassEnabled && isTestEmail(email)) {
            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            return;
        }

        String code = codeGenerator.generateCode();

        emailVerificationRepository.deleteAttemptCount(email);
        emailVerificationRepository.saveCode(email, code, EMAIL_CODE_TTL);

        try {
            emailVerificationService.sendVerificationCode(email, code);
        } catch (Exception e) {
            log.warn("[PasswordReset] 인증번호 메일 발송 실패 email={} cause={}", email, e.toString());
            emailVerificationRepository.deleteCode(email);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public ApplicantEmailVerifyResponseDTO verifyEmailCode(ApplicantEmailVerifyRequestDTO request) {
        String email = request.email();

        if (testEmailBypassEnabled && isTestEmail(email)) {
            if (!TEST_EMAIL_VERIFICATION_CODE.equals(request.code())) {
                throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
            }

            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            return issueVerificationToken(email);
        }

        // 1) 코드 존재 여부만 확인 (소비하지 않음 - 시도 횟수 한도 내에선 재시도 허용)
        String savedCode = emailVerificationRepository.findCodeByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        // 2) 시도 횟수 증가 (TTL은 코드와 동일하게 부여)
        long attemptCount = emailVerificationRepository.incrementAttemptCount(email, EMAIL_CODE_TTL);

        // 3) 한도 초과 시 코드/카운터 모두 소각하고 차단
        if (attemptCount > MAX_VERIFICATION_ATTEMPTS) {
            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            throw new BusinessException(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS);
        }

        // 4) 코드 불일치 - 카운터는 살린 채 거절 (사용자가 한도 내에서 재시도 가능)
        if (!savedCode.equals(request.code())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 5) 일치 - 코드/카운터 정리 + verificationToken 발급
        emailVerificationRepository.deleteCode(email);
        emailVerificationRepository.deleteAttemptCount(email);

        return issueVerificationToken(email);
    }

    private boolean isTestEmail(String email) {
        int atIndex = email.lastIndexOf("@");
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return false;
        }

        String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return TEST_EMAIL_DOMAIN.equals(domain);
    }

    private ApplicantEmailVerifyResponseDTO issueVerificationToken(String email) {
        String verificationToken = UUID.randomUUID().toString();
        emailVerificationRepository.saveVerifiedToken(verificationToken, email, VERIFICATION_TOKEN_TTL);
        return new ApplicantEmailVerifyResponseDTO(verificationToken);
    }

    @Transactional
    public void changePassword(ApplicantPasswordChangeRequestDTO request) {
        validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());

        // verification 토큰을 atomic하게 소비 (한 번 시도하면 재사용 불가)
        String verifiedEmail = emailVerificationRepository.findAndDeleteVerifiedToken(request.verificationToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!verifiedEmail.equals(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(request.email())
                .filter(existing -> existing.getStatus() == ApplicantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        String encodedPassword = passwordEncoder.encode(request.newPassword());
        applicant.updatePassword(encodedPassword);

        // 비밀번호 변경 후 기존 세션/토큰 무효화 (액세스 토큰 버전 증가 + 리프레시 토큰 폐기)
        tokenVersionRepository.increaseVersion(applicant.getPublicId(), applicant.getRole());
        refreshTokenRepository.deleteByPublicId(applicant.getPublicId(), applicant.getRole());
    }

    @Transactional
    public ApplicantSignupInitResponseDTO initSignup(ApplicantSignupInitRequestDTO request) {
        validatePasswordConfirm(request.password(), request.passwordConfirm());

        // verification 토큰을 atomic하게 소비 (한 번 시도하면 재사용 불가)
        String verifiedEmail = emailVerificationRepository.findAndDeleteVerifiedToken(request.verificationToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if(!verifiedEmail.equals(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(request.email())
                .map(existing -> {
                    if (existing.getStatus() == ApplicantStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                    }
                    existing.resetPendingPassword(encodedPassword);
                    return existing;
                })
                .orElseGet(() -> {
                    Applicant newApplicant = Applicant.builder()
                            .email(request.email())
                            .password(encodedPassword)
                            .role(UserRole.APPLICANT)
                            .build();
                    try {
                        Applicant saved = applicantRepository.save(newApplicant);
                        applicantRepository.flush();
                        return saved;
                    } catch (DataIntegrityViolationException e) {
                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                    }
                });

        String signupToken = UUID.randomUUID().toString();
        signupTokenRepository.save(signupToken, applicant.getPublicId(), SIGNUP_TOKEN_TTL);

        return new ApplicantSignupInitResponseDTO(signupToken);
    }

    @Transactional
    public ApplicantLoginResult completeSignup(ApplicantSignupCompleteRequestDTO request) {
        validateRequiredAgreements(request.agreements());

        String publicId = signupTokenRepository.findAndDelete(request.signupToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN));

        Applicant applicant = applicantRepository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        if (applicant.getStatus() != ApplicantStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN);
        }

        ApplicantAgreement agreement = ApplicantAgreement.builder()
                .applicant(applicant)
                .termsOfService(request.agreements().termsOfService())
                .privacyPolicy(request.agreements().privacyPolicy())
                .individualMemberTerms(request.agreements().individualMemberTerms())
                .aiAnalysisConsent(request.agreements().aiAnalysisConsent())
                .sensitiveDataConsent(request.agreements().sensitiveDataConsent())
                .marketingConsent(request.agreements().marketingConsent())
                .build();

        applicantAgreementRepository.save(agreement);
        applicant.activate();

        UserRole userRole = applicant.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

        String accessToken = jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion);
        long refreshTokenTtlMillis = jwtTokenProvider.getRefreshTokenExpirationMillis();

        refreshTokenRepository.save(
                publicId,
                userRole,
                refreshToken,
                refreshTokenTtlMillis
        );

        return new ApplicantLoginResult(
                accessToken,
                refreshToken,
                userRole
        );

    }

    @Transactional
    public ApplicantLoginResult login(ApplicantLoginRequestDTO request) {
        // 로그인 경로에서는 계정 미존재와 비밀번호 불일치를 동일한 오류(INVALID_PASSWORD)로 통일해 이메일 열거를 차단한다.
        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD));

        if(!passwordEncoder.matches(request.password(), applicant.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        if (applicant.getStatus() != ApplicantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SIGNUP_NOT_COMPLETED);
        }

        String publicId = applicant.getPublicId();
        UserRole userRole = applicant.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

        String accessToken = jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion);
        long refreshTokenTtlMillis = jwtTokenProvider.getRefreshTokenExpirationMillis();

        refreshTokenRepository.save(
                publicId,
                userRole,
                refreshToken,
                refreshTokenTtlMillis
        );

        return new ApplicantLoginResult(
                accessToken,
                refreshToken,
                userRole
        );
    }

    @Transactional
    public void withdraw(String publicId) {
        Applicant applicant = applicantProvider.findByPublicId(publicId);

        refreshTokenRepository.deleteByPublicId(
                applicant.getPublicId(),
                applicant.getRole()
        );

        applicant.withdraw();
    }

    private void validatePasswordConfirm(String password, String passwordConfirm) {
        if(!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
    }

    private void validateRequiredAgreements(ApplicantAgreementRequestDTO agreements) {
        if(!Boolean.TRUE.equals(agreements.termsOfService())
        || !Boolean.TRUE.equals(agreements.privacyPolicy())
        || !Boolean.TRUE.equals(agreements.individualMemberTerms())
        || !Boolean.TRUE.equals(agreements.aiAnalysisConsent())
        || !Boolean.TRUE.equals(agreements.sensitiveDataConsent())) {
            throw new BusinessException(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED);
        }
    }
}
