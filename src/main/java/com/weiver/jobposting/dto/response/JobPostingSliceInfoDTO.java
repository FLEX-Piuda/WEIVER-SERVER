package com.weiver.jobposting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Slice;

@Schema(description = "공고 Slice 정보")
public record JobPostingSliceInfoDTO(
        @Schema(description = "현재 페이지 번호", example = "0")
        int pageNumber,

        @Schema(description = "페이지 요청 크기", example = "3")
        int pageSize,

        @Schema(description = "다음 Slice 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "마지막 Slice 여부", example = "false")
        boolean isLast
) {
    public static JobPostingSliceInfoDTO from(Slice<?> slice) {
        return new JobPostingSliceInfoDTO(
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext(),
                slice.isLast()
        );
    }
}
