package com.weiver.global.exception.handler;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.exception.ErrorDetail;
import com.weiver.global.exception.ErrorResponse;
import com.weiver.global.security.cookie.CookieProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final CookieProvider cookieProvider;

    // ===================== 우리가 만든 예외 =====================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Sentry 전송 제외: 의도된 비즈니스 예외는 WARN 레벨로 기록하고 기존 에러 응답을 유지한다.
        log.warn("[BusinessException] errorCode={}, message={}, path={}",
                ex.getCode().code, ex.getMessage(), request.getRequestURI());

        // 토큰 재사용 감지 시 리프레시 토큰 쿠키 만료
        if (ex.getCode() == ErrorCode.TOKEN_REUSE_DETECTED) {
            ResponseCookie expiredRefreshTokenCookie = cookieProvider.createExpiredRefreshTokenCookie();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie.toString());
        }

        return ResponseEntity
                .status(ex.getCode().httpStatus)
                .body(ErrorResponse.of(
                        ex.getCode(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        // Sentry 전송 대상: 예측하지 못한 시스템 예외는 ERROR 레벨과 예외 객체를 함께 기록한다.
        log.error("[UnhandledException] message={}, path={}",
                ex.getMessage(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.httpStatus)
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "서버 내부 오류가 발생했습니다.",
                        request.getRequestURI(),
                        List.of()
                ));
    }

    // ===================== Spring 예외 — @ExceptionHandler =====================

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingCookie(
            MissingRequestCookieException ex,
            HttpServletRequest request) {

        // Sentry 전송 제외: 필수 쿠키 누락은 클라이언트 요청 문제이므로 WARN 레벨로 기록한다.
        log.warn("[MissingCookie] cookieName={}, path={}",
                ex.getCookieName(), request.getRequestURI());

        return toResponse(ErrorCode.MISSING_COOKIE, request, List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handlePathAndConstraint(
            Exception ex,
            HttpServletRequest request) {

        List<ErrorDetail> details = extractDetails(ex);

        // Sentry 전송 제외: 경로 변수/제약 조건 검증 실패는 클라이언트 요청 문제이므로 WARN 레벨로 기록한다.
        log.warn("[BindException] type={}, message={}, path={}",
                ex.getClass().getSimpleName(), ex.getMessage(), request.getRequestURI());

        return toResponse(ErrorCode.BIND_FAILED, request, details);
    }

    // ===================== Spring 예외 — override =====================

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("[NoResourceFound] path={}", ex.getMessage());
        return toResponseObject(status, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return toResponseObject(ErrorCode.VALIDATION_FAILED, getPath(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ErrorDetail> details = List.of(
                new ErrorDetail(ex.getParameterName(), "필수 요청 파라미터가 누락되었습니다.")
        );
        return toResponseObject(ErrorCode.BIND_FAILED, getPath(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return toResponseObject(ErrorCode.MALFORMED_JSON, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return toResponseObject(ErrorCode.METHOD_NOT_ALLOWED, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return toResponseObject(ErrorCode.UNSUPPORTED_MEDIA_TYPE, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        String path = getPath(request);

        if (statusCode.is5xxServerError()) {
            // Sentry 전송 대상: Spring 내부 처리 중 발생한 5xx 예외는 ERROR 레벨로 스택 트레이스를 남긴다.
            log.error("[UnhandledException] message={}, path={}", ex.getMessage(), path, ex);
            return toResponseObject(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "서버 내부 오류가 발생했습니다.",
                    path,
                    List.of()
            );
        }

        // Sentry 전송 제외: Spring MVC 4xx 계열 예외는 클라이언트 요청 문제로 보고 WARN 레벨로 기록한다.
        log.warn("[SpringClientException] status={}, message={}, path={}",
                statusCode.value(), ex.getMessage(), path);
        return toResponseObject(statusCode, path, List.of());
    }

    // ===================== private 헬퍼 =====================

    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode,
            HttpServletRequest request,
            List<ErrorDetail> details) {

        return ResponseEntity
                .status(errorCode.httpStatus)
                .body(ErrorResponse.of(errorCode, request.getRequestURI(), details));
    }

    private ResponseEntity<Object> toResponseObject(
            ErrorCode errorCode,
            String path,
            List<ErrorDetail> details) {

        return ResponseEntity
                .status(errorCode.httpStatus)
                .body(ErrorResponse.of(errorCode, path, details));
    }

    private ResponseEntity<Object> toResponseObject(
            ErrorCode errorCode,
            String message,
            String path,
            List<ErrorDetail> details) {

        return ResponseEntity
                .status(errorCode.httpStatus)
                .body(ErrorResponse.of(errorCode, message, path, details));
    }

    private ResponseEntity<Object> toResponseObject(
            HttpStatusCode statusCode,
            String path,
            List<ErrorDetail> details) {

        return ResponseEntity
                .status(statusCode)
                .body(ErrorResponse.of(statusCode, path, details));
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private List<ErrorDetail> extractDetails(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException e) {
            String requiredType = e.getRequiredType() != null
                    ? e.getRequiredType().getSimpleName()
                    : "알 수 없음";
            return List.of(new ErrorDetail(
                    e.getName(),
                    "'" + e.getValue() + "' 값은 '" + requiredType + "' 타입으로 변환할 수 없습니다."
            ));
        }
        if (ex instanceof ConstraintViolationException e) {
            return e.getConstraintViolations().stream()
                    .map(v -> new ErrorDetail(
                            v.getPropertyPath().toString(),
                            v.getMessage()))
                    .toList();
        }
        return List.of();
    }

}
