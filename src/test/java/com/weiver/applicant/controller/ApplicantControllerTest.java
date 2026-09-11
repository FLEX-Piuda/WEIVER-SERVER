package com.weiver.applicant.controller;

import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.dto.response.*;
import com.weiver.applicant.service.*;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicantController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicantService applicantService;
    @MockitoBean
    private CertificateService certificateService;
    @MockitoBean
    private AwardService awardService;
    @MockitoBean
    private WorkExperienceService workExperienceService;
    @MockitoBean
    private EducationService educationService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CookieProvider cookieProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
    @DisplayName("구직자 전체 정보 조회 성공")
    void searchApplicant_Success() throws Exception{
        // given
        String publicId = "2222";

        ApplicantDetailResponseDTO mockApplicantDetail = new ApplicantDetailResponseDTO(
                "https://weiver-public-bucket.s3.ap-northeast-2.amazonaws.com",
                "이현우",
                "경기도 안산시 일동",
                "2002-02-08",
                "010-5622-5555",
                "asdglk@gmail.com"
        );

        AwardDetailResponseDTO mockAwardDetail = new AwardDetailResponseDTO(
                1L, "한국인터넷진흥원장상", "2025-11-02", "한국인터넷진흥원(KISA)"
        );

        CertificateDetailResponseDTO mockCertificateDetail = new CertificateDetailResponseDTO(
                1L, "정보처리기사", "2025-03-25", "한국산업인력공단"
        );

        EducationDetailResponseDTO mockEducationDetail = new EducationDetailResponseDTO(
                1L, "한양대학교 에리카", "MASTER", "컴퓨터학부", null, "2021-03", null, "ACTIVE"
        );

        WorkExperienceDetailResponseDTO mockWorkExperienceDetail = new WorkExperienceDetailResponseDTO(
                1L, "SK하이닉스", "인턴", "2026-02-02", null, "백엔드개발해요", "INTERN"
        );

        ApplicantInfoResponseDTO mockResponseDTO = new ApplicantInfoResponseDTO(
                mockApplicantDetail,
                Collections.singletonList(mockEducationDetail),
                Collections.singletonList(mockAwardDetail),
                Collections.singletonList(mockWorkExperienceDetail),
                Collections.singletonList(mockCertificateDetail)
        );

        given(applicantService.searchApplicant(publicId)).willReturn(mockResponseDTO);

        // when, then
        mockMvc.perform(get("/api/applicants")
                        .with(customAuth("2222"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect((jsonPath("$.status").value("success")));
    }

    @Test
    @DisplayName("구직자 개인정보 및 이미지 수정 성공 (Multipart PUT)")
    void updateApplicantInfo_Success() throws Exception{
        // given
        ApplicantInfoRequestDTO requestDTO = new ApplicantInfoRequestDTO(
                "이현우", "test@example.com", "010-1234-5678", "안산시", LocalDate.of(2000, 1, 1)
        );
        String requestDtoJson = objectMapper.writeValueAsString(requestDTO);

        MockMultipartFile requestDtoPart = new MockMultipartFile(
                "requestDTO", "", "application/json", requestDtoJson.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile profileImagePart = new MockMultipartFile(
                "profileImage", "profile.jpg", "image/jpeg", "image_data".getBytes()
        );

        // when, then
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/applicants/info")
                        .file(requestDtoPart)
                        .file(profileImagePart)
                        .with(request -> {
                            request.setMethod(HttpMethod.PUT.name());
                            return request;
                        })
                        .with(customAuth("2222"))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("개인정보 저장에 성공했습니다."));
    }

    @Test
    @DisplayName("엣지 케이스 : Principal이 없을 대 UNAUTHORIZED 에러 발생")
    void searchApplicant_WithoutPrincipal_ThrowsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/applicants")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 성공")
    void getSubmissionStatus_Success() throws Exception {
        // given
        String publicId = "2222";
        ApplicantSubmissionStatusResponseDTO responseDTO = new ApplicantSubmissionStatusResponseDTO(
                true,
                true,
                false,
                true
        );

        given(applicantService.getSubmissionStatus(publicId)).willReturn(responseDTO);

        // when, then
        mockMvc.perform(get("/api/applicants/submission-status")
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.submitted").value(true))
                .andExpect(jsonPath("$.data.resumeCompleted").value(true))
                .andExpect(jsonPath("$.data.essayCompleted").value(false))
                .andExpect(jsonPath("$.data.portfolioCompleted").value(true));
    }

    @Test
    @DisplayName("엣지 케이스 : 프로필 제출 여부 조회 시 Principal이 없으면 UNAUTHORIZED 에러 발생")
    void getSubmissionStatus_WithoutPrincipal_ThrowsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/applicants/submission-status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("엣지 케이스 : 남의 수상 이력 ID 조작 시도 -> 404 (또는 400) 에러 발생")
    void updateAwardInfo_WithOthersAwardId_ThrowsException() throws Exception {
        // given
        String publicId = "1";

        String requestDtoJson = "{\"AwardUpdateDTO\":[{\"awardId\":999, \"awardDate\":\"2025-11-25\", \"awardName\":\"해킹상\", \"issuer\":\"KISA\"}]}";

        doThrow(new BusinessException(ErrorCode.AWARD_NOT_FOUND))
                .when(awardService).updateAwardInfo(eq(publicId), any());

        // when, then
        mockMvc.perform(put("/api/applicants/award")
                        .with(customAuth("1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AWARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("엣지 케이스 : 프로필 사진 없이 텍스트만 수정 요청 -> 성공")
    void updateApplicantInfo_WithoutProfileImage_Success() throws Exception {
        // given
        String publicId = "2222";

        ApplicantInfoRequestDTO requestDTO = new ApplicantInfoRequestDTO(
                "이현우", "test@example.com", "010-1234-5678", "안산시", LocalDate.of(2000, 1, 1)
        );
        String requestDtoJson = objectMapper.writeValueAsString(requestDTO);

        MockMultipartFile requestDtoPart = new MockMultipartFile(
                "requestDTO", "", "application/json", requestDtoJson.getBytes(StandardCharsets.UTF_8)
        );

        doNothing().when(applicantService).updateApplicantInfo(eq(publicId), any(), any());

        // when, then
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/applicants/info")
                        .file(requestDtoPart)
                        .with(request -> {
                            request.setMethod(HttpMethod.PUT.name());
                            return request;
                        })
                        .with(customAuth("2222"))) // ⬅️ 변경!
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("엣지 케이스 3 : 이메일 양식 오류 및 빈 이름 전송 -> 400 Bad Request")
    void updateApplicantInfo_InvalidValidation_ThrowsBadRequest() throws Exception {
        // given
        ApplicantInfoRequestDTO invalidRequestDTO = new ApplicantInfoRequestDTO(
                "", "wrong-email-format", "010-1234-5678", "안산시", LocalDate.of(2000, 1, 1)
        );
        String requestDtoJson = objectMapper.writeValueAsString(invalidRequestDTO);

        MockMultipartFile requestDtoPart = new MockMultipartFile(
                "requestDTO", "", "application/json", requestDtoJson.getBytes(StandardCharsets.UTF_8)
        );

        // when, then
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/applicants/info")
                        .file(requestDtoPart)
                        .with(request -> {
                            request.setMethod(HttpMethod.PUT.name());
                            return request;
                        })
                        .with(customAuth("1")))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("엣지 케이스 : 악성 스크립트 파일(.sh) 업로드 시도 -> 400 에러 발생")
    void updateApplicantInfo_WithInvalidFileExtension_ThrowsBadRequest() throws Exception {
        // given
        String publicId = "2222";

        ApplicantInfoRequestDTO requestDTO = new ApplicantInfoRequestDTO(
                "이현우", "test@example.com", "010-1234-5678", "안산시", LocalDate.of(2000, 1, 1)
        );
        String requestDtoJson = objectMapper.writeValueAsString(requestDTO);

        MockMultipartFile requestDtoPart = new MockMultipartFile(
                "requestDTO", "", "application/json", requestDtoJson.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile maliciousFilePart = new MockMultipartFile(
                "profileImage", "hack.sh", "application/x-sh", "rm -rf /".getBytes()
        );

        doThrow(new BusinessException(ErrorCode.BAD_REQUEST, "지원하지 않는 파일 형식입니다."))
                .when(applicantService).updateApplicantInfo(eq(publicId), any(), any());

        // when, then
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/applicants/info")
                        .file(requestDtoPart)
                        .file(maliciousFilePart)
                        .with(request -> {
                            request.setMethod(HttpMethod.PUT.name());
                            return request;
                        })
                        .with(customAuth("2222")))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다."));
    }
}
