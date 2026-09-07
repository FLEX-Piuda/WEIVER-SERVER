package com.weiver.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.interview.dto.request.InterviewErrorReportRequestDTO;
import com.weiver.interview.service.InterviewErrorReportService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewErrorReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class InterviewErrorReportControllerTest {

    private static final String BASE_URL = "/api/interviews/{interviewSessionId}/error-report";
    private static final String APPLICANT_PUBLIC_ID = "applicant-pub-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterviewErrorReportService interviewErrorReportService;

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
    @DisplayName("오류 리포트 접수 성공 시 201 CREATED를 반환한다")
    void createErrorReport_Success() throws Exception {
        // given
        UUID sessionId = UUID.randomUUID();
        InterviewErrorReportRequestDTO request =
                new InterviewErrorReportRequestDTO("질문 음성이 재생되지 않았습니다.");

        // when, then
        mockMvc.perform(post(BASE_URL, sessionId)
                        .with(customAuth(APPLICANT_PUBLIC_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("오류 리포트가 접수되었습니다."));

        then(interviewErrorReportService).should()
                .createErrorReport(eq(sessionId), eq(APPLICANT_PUBLIC_ID), any());
    }

    @Test
    @DisplayName("content가 비어있으면 400 VALIDATION_FAILED를 반환한다")
    void createErrorReport_BlankContent() throws Exception {
        // given
        UUID sessionId = UUID.randomUUID();
        InterviewErrorReportRequestDTO request = new InterviewErrorReportRequestDTO("");

        // when, then
        mockMvc.perform(post(BASE_URL, sessionId)
                        .with(customAuth(APPLICANT_PUBLIC_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(interviewErrorReportService).should(never())
                .createErrorReport(any(), any(), any());
    }

    @Test
    @DisplayName("미인증 사용자가 요청하면 401 UNAUTHORIZED를 반환한다")
    void createErrorReport_Unauthorized() throws Exception {
        // given
        UUID sessionId = UUID.randomUUID();
        InterviewErrorReportRequestDTO request =
                new InterviewErrorReportRequestDTO("오류 내용");

        // when, then
        mockMvc.perform(post(BASE_URL, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(interviewErrorReportService).should(never())
                .createErrorReport(any(), any(), any());
    }
}
