package com.weiver.interview.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.interview.service.InterviewFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Interview", description = "AI 면접 세션 REST API")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewFlowService interviewFlowService;

    @Operation(summary = "면접 결과 제출(수동 종료)", description = "진행 중인 면접 세션을 수동으로 종료하고 transcript 저장 요청을 발행한다. 이미 종료된 세션은 재제출할 수 없다.")
    @PostMapping("/{interviewSessionId}/submit")
    public ResponseEntity<ApiResponse<Void>> submitInterview(
            @Parameter(description = "면접 세션 ID(UUID)") @PathVariable UUID interviewSessionId,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        interviewFlowService.submitInterview(interviewSessionId, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success("면접 결과 제출에 성공했습니다."));
    }
}
