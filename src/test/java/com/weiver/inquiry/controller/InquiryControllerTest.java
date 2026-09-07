package com.weiver.inquiry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.inquiry.dto.request.InquiryCreateRequestDTO;
import com.weiver.inquiry.service.InquiryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InquiryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InquiryService inquiryService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CookieProvider cookieProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private RequestPostProcessor customAuth(String publicId) {
        return customAuth(publicId, UserRole.APPLICANT);
    }

    private RequestPostProcessor customAuth(String publicId, UserRole role) {
        return request -> {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, role);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
            SecurityContextHolder.getContext().setAuthentication(auth);
            return request;
        };
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("문의 제출 성공 시 201 Created를 반환한다")
    void createInquiry_Success() throws Exception {
        // given
        String publicId = "pub-1";
        InquiryCreateRequestDTO request = new InquiryCreateRequestDTO("면접 일정 문의", "일정을 변경하고 싶습니다.");

        // when, then
        mockMvc.perform(post("/api/inquiries")
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("문의가 접수되었습니다."));

        then(inquiryService).should().createInquiry(eq(publicId), any());
    }

    @Test
    @DisplayName("제목이 비어 있으면 400 VALIDATION_FAILED를 반환한다")
    void createInquiry_BlankTitle_ThrowsValidationFailed() throws Exception {
        // given
        InquiryCreateRequestDTO request = new InquiryCreateRequestDTO("", "일정을 변경하고 싶습니다.");

        // when, then
        mockMvc.perform(post("/api/inquiries")
                        .with(customAuth("pub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(inquiryService).should(never()).createInquiry(anyString(), any());
    }

    @Test
    @DisplayName("내용이 비어 있으면 400 VALIDATION_FAILED를 반환한다")
    void createInquiry_BlankContent_ThrowsValidationFailed() throws Exception {
        // given
        InquiryCreateRequestDTO request = new InquiryCreateRequestDTO("면접 일정 문의", "");

        // when, then
        mockMvc.perform(post("/api/inquiries")
                        .with(customAuth("pub-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(inquiryService).should(never()).createInquiry(anyString(), any());
    }

    @Test
    @DisplayName("APPLICANT가 아닌(COMPANY) 인증 주체면 403 FORBIDDEN을 반환한다")
    void createInquiry_NotApplicant_ThrowsForbidden() throws Exception {
        // given
        InquiryCreateRequestDTO request = new InquiryCreateRequestDTO("면접 일정 문의", "일정을 변경하고 싶습니다.");

        // when, then
        mockMvc.perform(post("/api/inquiries")
                        .with(customAuth("pub-1", UserRole.COMPANY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        then(inquiryService).should(never()).createInquiry(anyString(), any());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 UNAUTHORIZED를 반환한다")
    void createInquiry_WithoutPrincipal_ThrowsUnauthorized() throws Exception {
        // given
        InquiryCreateRequestDTO request = new InquiryCreateRequestDTO("면접 일정 문의", "일정을 변경하고 싶습니다.");

        // when, then
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        then(inquiryService).should(never()).createInquiry(anyString(), any());
    }
}
