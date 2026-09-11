package com.weiver.dashboard.controller;

import com.weiver.dashboard.dto.response.DashboardNotificationListResponseDTO;
import com.weiver.dashboard.service.DashboardService;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.dto.response.JobPostingsDetails;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CookieProvider cookieProvider;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("알림 조회 페이지 파라미터와 Slice 응답을 정확히 전달한다.")
    void getNotifications_ReturnsSliceResponse() throws Exception {
        NotificationResponseDTO notification = new NotificationResponseDTO(
                10L,
                "인재 매칭",
                "새로운 매칭이 있습니다.",
                false,
                100L,
                LocalDateTime.of(2026, 1, 2, 12, 0)
        );
        DashboardNotificationListResponseDTO response =
                DashboardNotificationListResponseDTO.from(
                        new SliceImpl<>(
                                List.of(notification),
                                PageRequest.of(1, 5),
                                true
                        )
                );

        given(dashboardService.getNotifications("company-1", 1, 5))
                .willReturn(response);

        mockMvc.perform(get("/api/dashboards/notifications")
                        .param("page", "1")
                        .param("size", "5")
                        .with(companyAuth("company-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.NotificationDTO[0].notificationId").value(10L))
                .andExpect(jsonPath("$.data.NotificationDTO[0].jdId").value(100L))
                .andExpect(jsonPath("$.data.pageable.pageNumber").value(1))
                .andExpect(jsonPath("$.data.pageable.pageSize").value(5))
                .andExpect(jsonPath("$.data.pageable.hasNext").value(true))
                .andExpect(jsonPath("$.data.pageable.isLast").value(false));

        verify(dashboardService).getNotifications("company-1", 1, 5);
    }

    @Test
    @DisplayName("페이지 파라미터를 생략하면 page 0, size 20을 사용한다.")
    void getNotifications_UsesDefaultPageRequest() throws Exception {
        DashboardNotificationListResponseDTO response =
                DashboardNotificationListResponseDTO.from(
                        new SliceImpl<>(List.of(), PageRequest.of(0, 20), false)
                );
        given(dashboardService.getNotifications("company-1", 0, 20))
                .willReturn(response);

        mockMvc.perform(get("/api/dashboards/notifications")
                        .with(companyAuth("company-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.NotificationDTO").isEmpty())
                .andExpect(jsonPath("$.data.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageable.pageSize").value(20))
                .andExpect(jsonPath("$.data.pageable.hasNext").value(false))
                .andExpect(jsonPath("$.data.pageable.isLast").value(true));

        verify(dashboardService).getNotifications("company-1", 0, 20);
    }

    @Test
    void getJobPostings_UsesDefaultSizeAndSerializesSliceContract() throws Exception {
        var detail = new JobPostingsDetails(
                10L, "백엔드", "공개중", "IT", "backend", 2L);
        var response = JobPostingPageResponseDTO.of(
                new SliceImpl<>(List.of(detail), PageRequest.of(0, 3), true), List.of(detail));
        given(dashboardService.getJobPostingsList("company-1", null, 0, 3)).willReturn(response);
        mockMvc.perform(get("/api/dashboards/job-postings").with(companyAuth("company-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].jdId").value(10))
                .andExpect(jsonPath("$.data.content[0].newApplicantCount").value(2))
                .andExpect(jsonPath("$.data.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageable.pageSize").value(3))
                .andExpect(jsonPath("$.data.pageable.hasNext").value(true))
                .andExpect(jsonPath("$.data.pageable.isLast").value(false))
                .andExpect(jsonPath("$.data.pageable.totalElements").doesNotExist())
                .andExpect(jsonPath("$.data.pageable.totalPages").doesNotExist());
        verify(dashboardService).getJobPostingsList("company-1", null, 0, 3);
    }

    @Test
    void getJobPostings_InvalidPageReturnsBadRequest() throws Exception {
        given(dashboardService.getJobPostingsList("company-1", null, -1, 3))
                .willThrow(new BusinessException(
                        ErrorCode.BAD_REQUEST));
        mockMvc.perform(get("/api/dashboards/job-postings").param("page", "-1")
                        .with(companyAuth("company-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobPostings_RequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboards/job-postings"))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor companyAuth(String publicId) {
        return request -> {
            AuthenticatedPrincipal principal =
                    new AuthenticatedPrincipal(publicId, UserRole.COMPANY);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return request;
        };
    }
}
