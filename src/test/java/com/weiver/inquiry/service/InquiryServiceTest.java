package com.weiver.inquiry.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.inquiry.domain.Inquiry;
import com.weiver.inquiry.dto.request.InquiryCreateRequestDTO;
import com.weiver.inquiry.repository.InquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private ApplicantService applicantService;

    @Mock
    private InquiryRepository inquiryRepository;

    @InjectMocks
    private InquiryService inquiryService;

    @Captor
    private ArgumentCaptor<Inquiry> inquiryCaptor;

    @Test
    @DisplayName("문의를 제출하면 지원자를 조회하고 제목/내용/지원자가 담긴 Inquiry를 저장한다")
    void createInquiry_Success() {
        // given
        String publicId = "pub-1";
        Applicant applicant = Applicant.builder()
                .email("applicant@example.com")
                .password("encoded")
                .build();
        InquiryCreateRequestDTO request = new InquiryCreateRequestDTO("면접 일정 문의", "일정을 변경하고 싶습니다.");

        given(applicantService.getApplicant(publicId)).willReturn(applicant);

        // when
        inquiryService.createInquiry(publicId, request);

        // then
        then(applicantService).should().getApplicant(publicId);
        then(inquiryRepository).should().save(inquiryCaptor.capture());

        Inquiry saved = inquiryCaptor.getValue();
        assertThat(saved.getTitle()).isEqualTo("면접 일정 문의");
        assertThat(saved.getContent()).isEqualTo("일정을 변경하고 싶습니다.");
        assertThat(saved.getApplicant()).isSameAs(applicant);
    }
}
