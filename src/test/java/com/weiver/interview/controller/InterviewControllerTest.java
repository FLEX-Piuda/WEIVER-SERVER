package com.weiver.interview.controller;

import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.interview.service.InterviewFlowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewFlowService interviewFlowService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private CookieProvider cookieProvider;

    private RequestPostProcessor customAuth(String publicId) {
        return request -> {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, UserRole.APPLICANT);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));

            SecurityContextHolder.getContext().setAuthentication(auth);
            return request;
        };
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("면접 결과 제출 성공 시 200과 성공 메시지를 반환한다")
    void submitInterview_Success() throws Exception {
        // given
        String publicId = "applicant-public-id";
        UUID sessionId = UUID.randomUUID();
        willDoNothing().given(interviewFlowService).submitInterview(eq(sessionId), eq(publicId));

        // when, then
        mockMvc.perform(post("/api/interviews/{interviewSessionId}/submit", sessionId)
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("면접 결과 제출에 성공했습니다."));

        verify(interviewFlowService).submitInterview(eq(sessionId), eq(publicId));
    }

    @Test
    @DisplayName("엣지 케이스: Principal이 없으면 UNAUTHORIZED 에러가 발생한다")
    void submitInterview_WithoutPrincipal_ThrowsUnauthorized() throws Exception {
        // when, then
        mockMvc.perform(post("/api/interviews/{interviewSessionId}/submit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("엣지 케이스: 이미 종료된 면접 세션 재제출 시 409와 INTERVIEW_ALREADY_COMPLETED 에러가 발생한다")
    void submitInterview_AlreadyCompleted_ThrowsConflict() throws Exception {
        // given
        String publicId = "applicant-public-id";
        UUID sessionId = UUID.randomUUID();
        doThrow(new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED))
                .when(interviewFlowService).submitInterview(eq(sessionId), eq(publicId));

        // when, then
        mockMvc.perform(post("/api/interviews/{interviewSessionId}/submit", sessionId)
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INTERVIEW_ALREADY_COMPLETED"));
    }
}
