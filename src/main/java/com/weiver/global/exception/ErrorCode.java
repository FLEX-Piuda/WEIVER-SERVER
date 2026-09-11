package com.weiver.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ===================== AUTH =====================
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    MISSING_COOKIE("MISSING_COOKIE", HttpStatus.BAD_REQUEST, "필수 쿠키가 누락되었습니다."),
    USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    // ===================== TOKEN =====================
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    INVALID_TOKEN("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    BLACKLISTED_TOKEN("BLACKLISTED_TOKEN", HttpStatus.UNAUTHORIZED, "로그아웃된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND("REFRESH_TOKEN_NOT_FOUND", HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다."),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요."),
    TOKEN_REUSE_DETECTED("TOKEN_REUSE_DETECTED", HttpStatus.UNAUTHORIZED, "리프레시 토큰 재사용이 감지되었습니다. 다시 로그인해 주세요."),

    // ===================== EMAIL =====================
    EMAIL_NOT_FOUND("EMAIL_NOT_FOUND", HttpStatus.NOT_FOUND, "이메일 템플릿이 없습니다."),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    EMAIL_SEND_FAILED("EMAIL_SEND_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "이메일 인증번호 전송에 실패했습니다."),
    INVALID_VERIFICATION_CODE("INVALID_VERIFICATION_CODE", HttpStatus.BAD_REQUEST, "이메일 인증번호가 올바르지 않습니다."),
    VERIFICATION_CODE_EXPIRED("VERIFICATION_CODE_EXPIRED", HttpStatus.BAD_REQUEST, "이메일 인증번호가 만료되었습니다. 다시 요청해 주세요."),
    TOO_MANY_VERIFICATION_ATTEMPTS("TOO_MANY_VERIFICATION_ATTEMPTS", HttpStatus.BAD_REQUEST, "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."),
    EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN, "이메일 인증이 완료되지 않았습니다."),

    // ===================== VALIDATION =====================
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    BIND_FAILED("BIND_FAILED", HttpStatus.BAD_REQUEST, "요청 파라미터 처리에 실패했습니다."),
    MALFORMED_JSON("MALFORMED_JSON", HttpStatus.BAD_REQUEST, "요청 본문의 형식이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),

    // ===================== APPLICANT =====================
    APPLICANT_NOT_FOUND("APPLICANT_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    APPLICANT_ALREADY_EXISTS("APPLICANT_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
    INVALID_PASSWORD("INVALID_PASSWORD", HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    PASSWORD_CONFIRM_NOT_MATCH("PASSWORD_CONFIRM_NOT_MATCH", HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    REQUIRED_AGREEMENT_NOT_ACCEPTED("REQUIRED_AGREEMENT_NOT_ACCEPTED", HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 합니다."),
    INVALID_SIGNUP_TOKEN("INVALID_SIGNUP_TOKEN", HttpStatus.BAD_REQUEST, "회원가입 토큰이 유효하지 않거나 만료되었습니다."),
    SIGNUP_NOT_COMPLETED("SIGNUP_NOT_COMPLETED", HttpStatus.FORBIDDEN, "회원가입이 완료되지 않은 계정입니다."),

    // ===================== COMPANY =====================
    COMPANY_NOT_FOUND("COMPANY_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 기업입니다."),

    // ===================== RESUME =====================
    RESUME_NOT_FOUND("RESUME_NOT_FOUND", HttpStatus.NOT_FOUND, "이력서를 찾을 수 없습니다."),
    EDUCATION_NOT_FOUND("EDUCATION_NOT_FOUND", HttpStatus.NOT_FOUND, "학력 정보를 찾을 수 없습니다."),
    CERTIFICATION_NOT_FOUND("CERTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND, "자격증 정보를 찾을 수 없습니다."),
    AWARD_NOT_FOUND("AWARD_NOT_FOUND", HttpStatus.NOT_FOUND, "수상 이력을 찾을 수 없습니다."),
    EXPERIENCE_NOT_FOUND("EXPERIENCE_NOT_FOUND", HttpStatus.NOT_FOUND, "경력 정보를 찾을 수 없습니다."),
    PORTFOLIO_NOT_FOUND("PORTFOLIO_NOT_FOUND", HttpStatus.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."),
    PORTFOLIO_ALREADY_EXISTS("PORTFOLIO_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 포트폴리오가 존재합니다."),

    // ===================== REPORT =====================
    REPORT_NOT_FOUND("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다."),


    // ===================== ESSAY-ANSWER =====================
    ESSAY_ANSWER_NOT_FOUND("ESSAY_ANSWER_NOT_FOUND", HttpStatus.NOT_FOUND, "자기소개서를 찾을 수 없습니다."),
    ESSAY_QUESTION_NOT_FOUND("ESSAY_QUESTION_NOT_FOUND", HttpStatus.NOT_FOUND, "자기소개서 문항을 찾을 수 없습니다."),
    ESSAY_ANSWER_ALREADY_EXISTS("ESSAY_ANSWER_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 자기소개서가 존재합니다."),
    ESSAY_ANSWER_COUNT_MISMATCH("ESSAY_ANSWER_COUNT_MISMATCH", HttpStatus.BAD_REQUEST, "자기소개서 답변 개수가 올바르지 않습니다."),
    ESSAY_ANSWER_QUESTION_MISMATCH("ESSAY_ANSWER_QUESTION_MISMATCH", HttpStatus.BAD_REQUEST, "자기소개서 답변 문항이 요청과 일치하지 않습니다."),
    ESSAY_ANSWER_TOO_LONG("ESSAY_ANSWER_TOO_LONG", HttpStatus.BAD_REQUEST, "자기소개서 답변이 허용된 최대 글자 수를 초과했습니다."),

    // ===================== NOTIFICATION =====================
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NOTIFICATION_FORBIDDEN("NOTIFICATION_FORBIDDEN", HttpStatus.FORBIDDEN, "해당 알림에 대한 접근 권한이 없습니다."),

    // ===================== JOB_POSTING =====================
    JOB_POSTING_NOT_FOUND("JOB_POSTING_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 공고입니다."),

    // ===================== INTERVIEW =====================
    INTERVIEW_SESSION_NOT_FOUND("INTERVIEW_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "면접 세션을 찾을 수 없습니다."),
    INTERVIEW_ALREADY_COMPLETED("INTERVIEW_ALREADY_COMPLETED", HttpStatus.CONFLICT, "이미 완료된 면접입니다."),
    APPLICANT_ANALYSIS_NOT_COMPLETED("APPLICANT_ANALYSIS_NOT_COMPLETED", HttpStatus.CONFLICT, "지원자 분석이 완료되지 않았습니다."),
    INVALID_INTERVIEW_TYPE("INVALID_INTERVIEW_TYPE", HttpStatus.BAD_REQUEST, "잘못된 인터뷰 타입입니다."),

    // ===================== MATCHING =====================
    MATCH_NOT_FOUND("MATCH_NOT_FOUND", HttpStatus.NOT_FOUND, "매칭 결과를 찾을 수 없습니다."),

    // ===================== ANALYSIS =====================
    DETAIL_ANALYSIS_REPORT_NOT_FOUND("DETAIL_ANALYSIS_REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "상세 분석 리포트를 찾을 수 없습니다."),

    // ===================== SERVER =====================
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    FAIL_DELETE_FILE("FAIL_DELETE_FILE", HttpStatus.INTERNAL_SERVER_ERROR, "S3 파일 삭제 실패");
    public final String code;
    public final HttpStatus httpStatus;
    public final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
