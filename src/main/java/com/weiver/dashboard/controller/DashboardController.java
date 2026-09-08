package com.weiver.dashboard.controller;

import com.weiver.dashboard.dto.response.CompanyDashboardResponseDTO;
import com.weiver.dashboard.dto.response.DashboardNotificationListResponseDTO;
import com.weiver.dashboard.service.DashboardService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.type.JobPostingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "대시보드 API", description = "기업 대시보드 관련 API")
@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;


    @Operation(summary = "기업 정보 카드 조회", description = "대시보드 상단에 표시되는 기업의 기본 정보를 조회합니다.")
    @GetMapping("/company")
    public ResponseEntity<ApiResponse<CompanyDashboardResponseDTO>> getCompanyInfo(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        CompanyDashboardResponseDTO responseDTO = dashboardService.getCompanyInfo(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "공고 리스트 조회 (Slice)",
            description = "기업이 작성한 채용 공고를 최신순으로 Slice 조회합니다. 전체 건수 대신 pageable.hasNext를 반환합니다.<br>" +
                    "각 공고별 '새로운 지원자 수(newApplicantCount)'가 함께 반환됩니다."
    )
    @GetMapping("/job-postings")
    public ResponseEntity<ApiResponse<JobPostingPageResponseDTO>> getJobPostingsList(
            @Parameter(description = "공고 상태 필터링 (DRAFT, ACTIVE, CLOSED, ON_HOLD). 미입력 시 전체 조회", example = "ACTIVE")
            @RequestParam(required = false) JobPostingStatus status,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지당 데이터 개수, 1 이상 100 이하", example = "3")
            @RequestParam(defaultValue = "3") int size,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        JobPostingPageResponseDTO responseDTO = dashboardService.getJobPostingsList(principal.publicId(), status, page, size);
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "알림 목록 조회",
            description = "현재 로그인한 기업의 알림을 최신순으로 Slice 조회합니다."
    )
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<DashboardNotificationListResponseDTO>> getNotifications(
            @Parameter(description = "페이지 번호, 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "조회할 알림 수, 1 이상 100 이하", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @AuthenticationPrincipal
            @Parameter(hidden = true)
            AuthenticatedPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        DashboardNotificationListResponseDTO responseDTO =
                dashboardService.getNotifications(
                        principal.publicId(),
                        page,
                        size
                );

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(summary = "알림 읽음 처리", description = "해당 공고 매칭 지원자 리스트 페이지로 이동 함과 동시에" +
            " 해당 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> readNotification(
            @Parameter(description = "알림 고유 ID") @PathVariable Long notificationId,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        dashboardService.readNotification(notificationId, principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "현재 사용자의 모든 미읽음 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> readAllNotifications(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        dashboardService.readAllNotifications(principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
