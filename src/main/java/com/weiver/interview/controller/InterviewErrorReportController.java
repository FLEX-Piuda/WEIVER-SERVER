package com.weiver.interview.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.interview.dto.request.InterviewErrorReportRequestDTO;
import com.weiver.interview.service.InterviewErrorReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "AI 면접 오류 리포트 API", description = "AI 면접 진행 중 발생한 오류를 구직자가 신고하는 API입니다.")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewErrorReportController {

    private final InterviewErrorReportService interviewErrorReportService;

    @Operation(
            summary = "AI 면접 오류 리포트 접수",
            description = "면접 화면의 '오류가 있어요' 모달에서 로그인한 구직자가 자유 텍스트로 오류를 신고합니다."
    )
    @PostMapping("/{interviewSessionId}/error-report")
    public ResponseEntity<ApiResponse<Void>> createErrorReport(
            @PathVariable UUID interviewSessionId,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal,
            @RequestBody @Valid InterviewErrorReportRequestDTO request) {

        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        interviewErrorReportService.createErrorReport(interviewSessionId, principal.publicId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>created(null, "오류 리포트가 접수되었습니다."));
    }
}
