package com.weiver.applicant.service;

import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.request.post.AwardDetailDTO;
import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.EducationDetailDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.applicant.dto.response.ApplicantInfoResponseDTO;
import com.weiver.applicant.dto.response.ApplicantSubmissionStatusResponseDTO;
import com.weiver.applicant.repository.*;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.EmploymentType;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.applicant.type.Status;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceTest {

    @Mock private ApplicantRepository applicantRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private AwardRepository awardRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private WorkExperienceRepository workExperienceRepository;
    @Mock private EssayAnswerRepository essayAnswerRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private S3Service s3Service;

    @InjectMocks
    private ApplicantService applicantService;

    @InjectMocks
    private AwardService awardService;

    @InjectMocks
    private CertificateService certificateService;

    @InjectMocks
    private EducationService educationService;

    @InjectMocks
    private WorkExperienceService workExperienceService;

    // 레포지토리에 전달된 '진짜 리스트 데이터'를 낚아채서 검사하기 위한 도구
    @Captor
    private ArgumentCaptor<List<Education>> educationListCaptor;

    @Captor
    private ArgumentCaptor<List<Award>> awardListCaptor;

    @Test
    @DisplayName("프로필 사진 없이 텍스트 정보만 업데이트 시 정상 수행되며 S3 통신은 발생 X.")
    void updateApplicantInfo_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "2222";

        // Mock이 아닌 실제 엔티티 생성 
        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .name("홍길동")
                .phoneNumber("010-1234-1234")
                .photoUrl("https://old-photo.url")
                .build();

        // 실제 DTO 사용
        ApplicantInfoRequestDTO realRequestDTO = new ApplicantInfoRequestDTO(
                "김철수", "new@email.com", "010-5678-1234", "서울시", LocalDate.of(1999, 1, 1)
        );

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(realApplicant));

        // When
        applicantService.updateApplicantInfo(publicId, realRequestDTO, null);

        // Then
        assertThat(realApplicant.getName()).isEqualTo("김철수");
        assertThat(realApplicant.getPhoneNumber()).isEqualTo("010-5678-1234");
        // 사진을 안 보냈으니 기존 사진 URL이 그대로 유지되었는지 확인
        assertThat(realApplicant.getPhotoUrl()).isEqualTo("https://old-photo.url");

        // 사진이 없으므로 S3 관련 메서드가 "절대 호출되지 않았음(never)"을 검증
        verify(s3Service, never()).deleteFile(anyString());
        verify(s3Service, never()).publicUpload(any(), anyString());
    }
    
    @Test
    @DisplayName("수상 정보 업데이트 정상 수행")
    void saveAward_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "2222";

        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .build();

        AwardDetailDTO awardDetailDTO = new AwardDetailDTO(LocalDate.of(2021, 2, 1), "대상", "한양대학교");
        AwardRequestDTO awardRequestDTO = new AwardRequestDTO(List.of(awardDetailDTO));

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(realApplicant));

        // When
        awardService.saveAwardInfo(publicId, awardRequestDTO);

        // Then

        verify(awardRepository).saveAll(awardListCaptor.capture());
        List<Award> savedAwards = awardListCaptor.getValue();


        assertThat(savedAwards).hasSize(1);
        assertThat(savedAwards.get(0).getAwardName()).isEqualTo("대상");
        assertThat(savedAwards.get(0).getApplicant().getPublicId()).isEqualTo(publicId);
    }
    

    @Test
    @DisplayName("학력 정보 단건 저장 정상적으로 수행")
    void saveEducationInfo_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "2222";
        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .build();

        EducationDetailDTO detailDTO = new EducationDetailDTO(
                Degree.BACHELOR, "한양대학교", "컴퓨터공학", 4.0, YearMonth.of(2020, 3), YearMonth.of(2024, 2), Status.GRADUATED
        );
        EducationRequestDTO realRequestDTO = new EducationRequestDTO(List.of(detailDTO));

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(realApplicant));

        // When
        educationService.saveEducationInfo(publicId, realRequestDTO);

        // Then
        // saveAll이 호출될 때 넘어간 진짜 파라미터(List)를 캡처(Capture)
        verify(educationRepository).saveAll(educationListCaptor.capture());

        // 캡처한 리스트를 꺼내서, DTO의 값이 Entity로 잘 변환되어 들어갔는지 확인
        List<Education> savedEducations = educationListCaptor.getValue();

        assertThat(savedEducations).hasSize(1);
        assertThat(savedEducations.get(0).getSchoolName()).isEqualTo("한양대학교");
        assertThat(savedEducations.get(0).getApplicant().getPublicId()).isEqualTo(publicId);
    }

    @Test
    @DisplayName("학력 정보 여러건 저장 정상적으로 수행")
    void saveEducationList_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "2222";
        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .build();

        EducationDetailDTO highSchoolDTO = new EducationDetailDTO(
                Degree.HIGH_SCHOOL, "테스트고등학교", "이과", 0.0, YearMonth.of(2017, 3), YearMonth.of(2020, 2), Status.GRADUATED
        );

        EducationDetailDTO universityDTO = new EducationDetailDTO(
                Degree.BACHELOR, "한양대학교 에리카", "ICT융합학부", 4.0, YearMonth.of(2020, 3), YearMonth.of(2026, 2), Status.ACTIVE
        );

        EducationRequestDTO realRequestDTO = new EducationRequestDTO(List.of(highSchoolDTO, universityDTO));

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(realApplicant));

        // When
        educationService.saveEducationInfo(publicId, realRequestDTO);

        // Then
        // saveAll이 호출될 때 넘어간 진짜 파라미터(List)를 캡처(Capture)
        verify(educationRepository).saveAll(educationListCaptor.capture());

        // 캡처한 리스트를 꺼내서, DTO의 값이 Entity로 잘 변환되어 들어갔는지 확인
        List<Education> savedEducations = educationListCaptor.getValue();

        assertThat(savedEducations.get(0).getApplicant().getPublicId()).isEqualTo(publicId);

        assertThat(savedEducations).hasSize(2);
        assertThat(savedEducations.get(1).getSchoolName()).isEqualTo("한양대학교 에리카");
        assertThat(savedEducations.get(1).getApplicant().getPublicId()).isEqualTo(publicId);
    }

    @Test
    @DisplayName("존재하지 않는 구직자 ID로 요청 시 예외 발생")
    void getApplicant_NotFound_ThrowsException() {
        // Given
        String invalidPublicId = "3333";

        ApplicantInfoRequestDTO emptyRequestDTO = new ApplicantInfoRequestDTO(null, null, null, null, null);

        given(applicantRepository.findByPublicId(invalidPublicId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> applicantService.updateApplicantInfo(invalidPublicId, emptyRequestDTO,null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }

    @Test
    @DisplayName("새로운 사진 업로드 시 기존 S3 파일 삭제 및 새 파일 업로드가 정상 수행된다.")
    void updateApplicantInfo_WithNewImage_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "2222";
        String oldPhotoUrl = "https://weiver-public-bucket/old-profile.jpg";
        String newPhotoUrl = "https://weiver-public-bucket/new-profile.jpg";

        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .name("홍길동")
                .photoUrl(oldPhotoUrl)
                .build();

        ApplicantInfoRequestDTO realRequestDTO = new ApplicantInfoRequestDTO(
                "김철수", "new@email.com", "010-5678-1234", "서울시", LocalDate.of(1999, 1, 1)
        );

        // 가짜(Mock) 첨부파일 생성
        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.isEmpty()).willReturn(false); // 파일이 비어있지 않음

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(realApplicant));

        // s3Service.publicUpload 호출되면 가짜 URL을 반환
        given(s3Service.publicUpload(mockFile, "profiles")).willReturn(newPhotoUrl);

        // When
        applicantService.updateApplicantInfo(publicId, realRequestDTO, mockFile);

        // Then
        verify(s3Service, times(1)).deleteFile(oldPhotoUrl);
        verify(s3Service, times(1)).publicUpload(mockFile, "profiles");

        assertThat(realApplicant.getPhotoUrl()).isEqualTo(newPhotoUrl);
        assertThat(realApplicant.getName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("구직자 정보 전체 조회 시, 연관된 모든 레포지토리를 호출하여 DTO로 변환해 반환한다.")
    void searchApplicant_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "2222";

        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .name("이현우")
                .email("test@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .photoUrl("https://s3/profile.jpg")
                .build();

        Education education = Education.builder()
                .schoolName("한양대학교 에리카")
                .degree(Degree.valueOf("BACHELOR"))
                .major("ICT융합학부")
                .startDate(YearMonth.of(2021, 3))
                .status(Status.ACTIVE)
                .applicant(realApplicant)
                .build();

        Award award = Award.builder()
                .awardName("한국인터넷진흥원장상")
                .awardDate(LocalDate.of(2025, 11, 1))
                .applicant(realApplicant)
                .build();

        WorkExperience workExperience = WorkExperience.builder()
                .companyName("에이블리")
                .startDate(LocalDate.of(2026, 2, 1))
                .employmentType(EmploymentType.INTERN)
                .position("부장")
                .isRecognized(true)
                .applicant(realApplicant)
                .build();

        Certificate certificate = Certificate.builder()
                .certificateName("정보처리기사")
                .issuer("대한민국")
                .acquisitionDate(LocalDate.of(2025, 3, 1))
                .applicant(realApplicant)
                .build();

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(realApplicant));

        // 부모 엔티티를 넘겨줬을 때, 미리 만들어둔 자식 리스트를 반환하도록 세팅
        given(educationRepository.findAllByApplicant(realApplicant))
                .willReturn(List.of(education));
        given(awardRepository.findAllByApplicant(realApplicant))
                .willReturn(List.of(award));
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(realApplicant))
                .willReturn(List.of(workExperience));
        given(certificateRepository.findAllByApplicant(realApplicant))
                .willReturn(List.of(certificate));

        // When
        ApplicantInfoResponseDTO responseDTO = applicantService.searchApplicant(publicId);

        // Then
        
        // N+1 발생 여부 확인
        verify(educationRepository, times(1)).findAllByApplicant(realApplicant);
        verify(awardRepository, times(1)).findAllByApplicant(realApplicant);
        verify(workExperienceRepository, times(1)).findAllByApplicantOrderByStartDateDesc(realApplicant);
        verify(certificateRepository, times(1)).findAllByApplicant(realApplicant);

        // 부모 DTO 변환 검증
        assertThat(responseDTO.applicant().name()).isEqualTo("이현우");
        assertThat(responseDTO.applicant().email()).isEqualTo("test@example.com");

        // 3. 자식 리스트 DTO 변환 검증
        assertThat(responseDTO.education()).hasSize(1);
         assertThat(responseDTO.education().getFirst().schoolName()).isEqualTo("한양대학교 에리카");

        assertThat(responseDTO.award()).hasSize(1);
        assertThat(responseDTO.workExperience()).hasSize(1);
        assertThat(responseDTO.certificate()).hasSize(1);
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 시 동기화 상태가 COMPLETED이고 서류가 모두 작성된 경우 submitted와 3종 서류가 모두 true를 반환한다.")
    void getSubmissionStatus_CompletedAndAllDocuments_ReturnsAllTrue() {
        // Given
        String publicId = "2222";
        Applicant applicant = completedApplicantWithSyncStatus(publicId, ProfileSyncStatus.COMPLETED);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(educationRepository.existsByApplicant(applicant)).willReturn(true);
        given(essayAnswerRepository.existsByApplicant(applicant)).willReturn(true);
        given(portfolioRepository.existsByApplicant(applicant)).willReturn(true);

        // When
        ApplicantSubmissionStatusResponseDTO responseDTO = applicantService.getSubmissionStatus(publicId);

        // Then
        assertThat(responseDTO.submitted()).isTrue();
        assertThat(responseDTO.resumeCompleted()).isTrue();
        assertThat(responseDTO.essayCompleted()).isTrue();
        assertThat(responseDTO.portfolioCompleted()).isTrue();
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 시 동기화 상태가 REQUESTED이면 submitted는 true를 반환한다.")
    void getSubmissionStatus_Requested_ReturnsSubmittedTrue() {
        // Given
        String publicId = "2222";
        Applicant applicant = completedApplicantWithSyncStatus(publicId, ProfileSyncStatus.REQUESTED);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(educationRepository.existsByApplicant(applicant)).willReturn(true);
        given(essayAnswerRepository.existsByApplicant(applicant)).willReturn(true);
        given(portfolioRepository.existsByApplicant(applicant)).willReturn(true);

        // When
        ApplicantSubmissionStatusResponseDTO responseDTO = applicantService.getSubmissionStatus(publicId);

        // Then
        assertThat(responseDTO.submitted()).isTrue();
        assertThat(responseDTO.resumeCompleted()).isTrue();
        assertThat(responseDTO.essayCompleted()).isTrue();
        assertThat(responseDTO.portfolioCompleted()).isTrue();
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 시 동기화 상태가 PENDING이면 submitted는 false를 반환한다.")
    void getSubmissionStatus_Pending_ReturnsSubmittedFalse() {
        // Given
        String publicId = "2222";
        Applicant applicant = completedApplicantWithSyncStatus(publicId, ProfileSyncStatus.PENDING);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(educationRepository.existsByApplicant(applicant)).willReturn(true);
        given(essayAnswerRepository.existsByApplicant(applicant)).willReturn(true);
        given(portfolioRepository.existsByApplicant(applicant)).willReturn(true);

        // When
        ApplicantSubmissionStatusResponseDTO responseDTO = applicantService.getSubmissionStatus(publicId);

        // Then
        assertThat(responseDTO.submitted()).isFalse();
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 시 동기화 상태가 FAILED여도 submitted는 true를 반환한다.")
    void getSubmissionStatus_Failed_ReturnsSubmittedTrue() {
        // Given
        String publicId = "2222";
        Applicant applicant = completedApplicantWithSyncStatus(publicId, ProfileSyncStatus.FAILED);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(educationRepository.existsByApplicant(applicant)).willReturn(true);
        given(essayAnswerRepository.existsByApplicant(applicant)).willReturn(true);
        given(portfolioRepository.existsByApplicant(applicant)).willReturn(true);

        // When
        ApplicantSubmissionStatusResponseDTO responseDTO = applicantService.getSubmissionStatus(publicId);

        // Then
        assertThat(responseDTO.submitted()).isTrue();
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 시 이력서 상세 항목이 없으면 submitted가 true여도 resumeCompleted는 false를 반환한다.")
    void getSubmissionStatus_NoResumeDetail_ReturnsResumeFalse() {
        // Given
        String publicId = "2222";
        Applicant applicant = completedApplicantWithSyncStatus(publicId, ProfileSyncStatus.COMPLETED);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(educationRepository.existsByApplicant(applicant)).willReturn(false);
        given(workExperienceRepository.existsByApplicant(applicant)).willReturn(false);
        given(certificateRepository.existsByApplicant(applicant)).willReturn(false);
        given(awardRepository.existsByApplicant(applicant)).willReturn(false);
        given(essayAnswerRepository.existsByApplicant(applicant)).willReturn(true);
        given(portfolioRepository.existsByApplicant(applicant)).willReturn(true);

        // When
        ApplicantSubmissionStatusResponseDTO responseDTO = applicantService.getSubmissionStatus(publicId);

        // Then
        assertThat(responseDTO.submitted()).isTrue();
        assertThat(responseDTO.resumeCompleted()).isFalse();
        assertThat(responseDTO.essayCompleted()).isTrue();
        assertThat(responseDTO.portfolioCompleted()).isTrue();
    }

    @Test
    @DisplayName("프로필 제출 여부 조회 시 지원자가 존재하지 않으면 APPLICANT_NOT_FOUND 예외가 발생하고 서류 조회는 수행하지 않는다.")
    void getSubmissionStatus_ApplicantNotFound_ThrowsException() {
        // Given
        String publicId = "not-found";
        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> applicantService.getSubmissionStatus(publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);

        verifyNoInteractions(educationRepository, workExperienceRepository, certificateRepository,
                awardRepository, essayAnswerRepository, portfolioRepository);
    }

    private Applicant completedBasicInfoApplicant(String publicId) {
        return Applicant.builder()
                .publicId(publicId)
                .name("이현우")
                .email("test@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build();
    }

    private Applicant completedApplicantWithSyncStatus(String publicId, ProfileSyncStatus profileSyncStatus) {
        return Applicant.builder()
                .publicId(publicId)
                .name("이현우")
                .email("test@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .profileSyncStatus(profileSyncStatus)
                .build();
    }
}
