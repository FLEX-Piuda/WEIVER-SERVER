package com.weiver.global.exception.handler;

import com.weiver.global.exception.ErrorResponse;
import com.weiver.global.security.cookie.CookieProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private CookieProvider cookieProvider;

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("낙관적 락 충돌 예외를 수신하면 409 Conflict와 CONCURRENT_REQUEST_CONFLICT 코드를 반환한다")
    void handleOptimisticLock_returnsConflict() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler(cookieProvider);
        given(request.getRequestURI()).willReturn("/api/interviews/1/results");
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException(Object.class, 1L);

        // when
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("CONCURRENT_REQUEST_CONFLICT");
        assertThat(response.getBody().getPath()).isEqualTo("/api/interviews/1/results");
    }
}
