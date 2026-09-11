package com.weiver.inquiry.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.inquiry.dto.request.InquiryCreateRequestDTO;
import com.weiver.inquiry.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InquiryService {

    private final ApplicantService applicantService;
    private final InquiryRepository inquiryRepository;

    public void createInquiry(String applicantPublicId, InquiryCreateRequestDTO request) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);
        inquiryRepository.save(request.toEntity(applicant));
    }
}
