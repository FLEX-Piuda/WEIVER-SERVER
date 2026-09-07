package com.weiver.inquiry.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.inquiry.dto.request.InquiryCreateRequestDTO;
import com.weiver.inquiry.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiry", description = "문의사항 API")
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "문의사항 제출", description = "로그인한 구직자가 제목과 내용으로 문의를 제출합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createInquiry(
            @RequestBody @Valid InquiryCreateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        inquiryService.createInquiry(principal.publicId(), requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>created(null, "문의가 접수되었습니다."));
    }
}
