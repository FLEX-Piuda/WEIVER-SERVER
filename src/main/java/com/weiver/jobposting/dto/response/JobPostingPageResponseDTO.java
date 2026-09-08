package com.weiver.jobposting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Slice;

import java.util.List;

@Schema(description = "채용 공고 리스트 Slice 응답 DTO")
public record JobPostingPageResponseDTO(
        @Schema(description = "현재 페이지의 채용 공고 데이터 목록")
        List<JobPostingsDetails> content,

        @Schema(description = "전체 건수 계산 없는 Slice 메타 정보")
        JobPostingSliceInfoDTO pageable
) {
    public static JobPostingPageResponseDTO of(Slice<?> slice, List<JobPostingsDetails> content) {
        return new JobPostingPageResponseDTO(content, JobPostingSliceInfoDTO.from(slice));
    }
}
