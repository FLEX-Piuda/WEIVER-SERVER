package com.weiver.applicant.controller;

import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.CertificateRequestDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.post.WorkExperienceRequestDTO;
import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.dto.response.ApplicantInfoResponseDTO;
import com.weiver.applicant.dto.response.ApplicantSubmissionStatusResponseDTO;
import com.weiver.applicant.service.*;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "구직자(Applicant) 도메인 API", description = "구직자의 프로필, 학력, 수상, 자격증, 경력 정보를 관리하는 API입니다.\n\n" +
        "**[필독: PUT(수정) API 데이터 전송 규칙]**\n" +
        "- 개인 정보, 학력, 자격증, 수상이력, 경력사항 수정 API는 **'전체 덮어쓰기(Snapshot)'** 방식을 사용합니다.\n" +
        "- 사용자의 개별 액션(추가/수정/삭제)을 하나씩 API로 쏘지 마세요.\n" +
        "- 최종적으로 **저장 버튼을 누르는 순간, 화면에 남아있는 전체 데이터 리스트를 한 번에 배열에 담아 전송**해야 합니다.\n" +
        "- **서버 동작 방식:** 프론트에서 보낸 배열에 없는 기존 데이터는 사용자가 화면에서 지운 것으로 간주하고 **서버가 DB에서 자동 삭제 처리**합니다.")
@RestController
@RequestMapping("/api/applicants")
@RequiredArgsConstructor
public class ApplicantController {

    private final ApplicantService applicantService;
    private final AwardService awardService;
    private final CertificateService certificateService;
    private final EducationService educationService;
    private final WorkExperienceService workExperienceService;

    @Operation(
            summary = "구직자 전체 정보 조회 ",
            description = "로그인한 구직자의 기본 프로필, 학력, 수상, 자격증, 경력 등 **모든 정보를 한 번에 응답**합니다.<br>" +
                    "각 도메인별 배열 데이터가 포함되어 반환됩니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<ApplicantInfoResponseDTO>> searchApplicant(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        ApplicantInfoResponseDTO responseDTO = applicantService.searchApplicant(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));

    }

    @Operation(
            summary = "프로필 제출 여부 조회",
            description = "로그인한 구직자의 프로필 제출 여부와 이력서, 자기소개서, 포트폴리오 각 서류의 작성 완료 여부를 함께 boolean 값으로 반환합니다."
    )
    @GetMapping("/submission-status")
    public ResponseEntity<ApiResponse<ApplicantSubmissionStatusResponseDTO>> getSubmissionStatus(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        ApplicantSubmissionStatusResponseDTO responseDTO = applicantService.getSubmissionStatus(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "지원자 개인정보 및 프로필 이미지 수정",
            description = "구직자의 기본 정보(텍스트)와 프로필 사진(파일)을 함께 수정합니다.<br>" +
                    "**[Content-Type: multipart/form-data]** 로 전송해야 합니다.<br>" +
                    "- `requestDTO`: 이름, 생년월일, 연락처 등 (application/json 형식으로 변환하여 전송)<br>" +
                    "- `profileImage`: 프로필 사진 파일 (사진 변경이 없으면 필드 생략 또는 null 전송)"
    )
    @PutMapping(value = "/info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> updateApplicantInfo(
            @Parameter(
                    description = "개인정보 수정 데이터 (JSON)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApplicantInfoRequestDTO.class))
            )
            @RequestPart(value = "requestDTO") @Valid ApplicantInfoRequestDTO requestDTO,
            @Parameter(description = "프로필 이미지 파일 (.jpg, .png 등)") @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        applicantService.updateApplicantInfo(principal.publicId(), requestDTO, profileImage);

        return ResponseEntity.ok(ApiResponse.success("개인정보 저장에 성공했습니다."));
    }

    @Operation(
            summary = "학력 정보 초기 저장",
            description = "회원가입 후 최초로 학력 정보를 등록할 때 사용합니다."
    )
    @PostMapping("/education")
    public ResponseEntity<ApiResponse<Void>> saveEducationInfo(
            @RequestBody @Valid EducationRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        educationService.saveEducationInfo(principal.publicId(), requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>created(null, "학력 저장에 성공했습니다."));
    }

    @Operation(
            summary = "학력 정보 전체 덮어쓰기 (수정/삭제/추가 동시 처리)",
            description = "화면에 존재하는 **최종 학력 리스트 전체를 배열로 묶어서** 보냅니다.<br>" +
                    "새로 추가된 항목은 `id` 없이, 기존 항목은 `id`를 포함하여 전송하세요."
    )
    @PutMapping("/education")
    public ResponseEntity<ApiResponse<Void>> updateEducationInfo(
            @RequestBody @Valid EducationUpdateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        educationService.updateEducationInfo(principal.publicId(), requestDTO);

        return ResponseEntity.ok(ApiResponse.success("학력 업데이트 성공했습니다."));
    }

    @Operation(
            summary = "수상 이력 초기 저장",
            description = "회원가입 후 최초로 수상 이력을 등록할 때 사용합니다."
    )
    @PostMapping("/award")
    public ResponseEntity<ApiResponse<Void>> saveAwardInfo(
            @RequestBody @Valid AwardRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        awardService.saveAwardInfo(principal.publicId(), requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>created(null, "수상이력 저장에 성공했습니다."));
    }

    @Operation(
            summary = "수상 이력 전체 덮어쓰기 (수정/삭제/추가 동시 처리)",
            description = "화면에 존재하는 **최종 수상 이력 리스트 전체를 배열로 묶어서** 보냅니다.<br>" +
                    "새로 추가된 항목은 `id` 없이, 기존 항목은 `id`를 포함하여 전송하세요."
    )
    @PutMapping("/award")
    public ResponseEntity<ApiResponse<Void>> updateAwardInfo(
            @RequestBody @Valid AwardUpdateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        awardService.updateAwardInfo(principal.publicId(), requestDTO);

        return ResponseEntity.ok(ApiResponse.success("수상목록 업데이트 성공했습니다."));
    }

    @Operation(
            summary = "자격증 초기 저장",
            description = "회원가입 후 최초로 자격증을 등록할 때 사용합니다."
    )
    @PostMapping("/certificate")
    public ResponseEntity<ApiResponse<Void>> saveCertificateInfo(
            @RequestBody @Valid CertificateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        certificateService.saveCertificateInfo(principal.publicId(), requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>created(null, "자격증 저장에 성공했습니다."));
    }

    @Operation(
            summary = "자격증 정보 전체 덮어쓰기 (수정/삭제/추가 동시 처리)",
            description = "화면에 존재하는 **최종 자격증 리스트 전체를 배열로 묶어서** 보냅니다.<br>" +
                    "새로 추가된 항목은 `id` 없이, 기존 항목은 `id`를 포함하여 전송하세요."
    )
    @PutMapping("/certificate")
    public ResponseEntity<ApiResponse<Void>> updateCertificateInfo(
            @RequestBody @Valid CertificateUpdateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        certificateService.updateCertificateInfo(principal.publicId(), requestDTO);

        return ResponseEntity.ok(ApiResponse.success("자격증 목록 업데이트 성공했습니다."));
    }

    @Operation(
            summary = "경력 정보 초기 저장",
            description = "회원가입 후 최초로 경력 정보를 등록할 때 사용합니다."
    )
    @PostMapping("/experience")
    public ResponseEntity<ApiResponse<Void>> saveWorkExperienceInfo(
            @RequestBody @Valid WorkExperienceRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        workExperienceService.saveWorkExperienceInfo(principal.publicId(), requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>created(null, "경력 저장에 성공했습니다."));
    }

    @Operation(
            summary = "경력 정보 전체 덮어쓰기 (수정/삭제/추가 동시 처리)",
            description = "화면에 존재하는 **최종 경력 리스트 전체를 배열로 묶어서** 보냅니다.<br>" +
                    "새로 추가된 항목은 `id` 없이, 기존 항목은 `id`를 포함하여 전송하세요."
    )
    @PutMapping("/experience")
    public ResponseEntity<ApiResponse<Void>> updateExperienceInfo(
            @RequestBody @Valid WorkExperienceUpdateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        workExperienceService.updateWorkExperienceInfo(principal.publicId(), requestDTO);

        return ResponseEntity.ok(ApiResponse.success("경력 업데이트 성공했습니다."));
    }
}
