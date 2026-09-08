package com.weiver.jobposting.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.applicant.domain.Applicant;
import com.weiver.company.domain.Company;
import com.weiver.company.type.CompanyType;
import com.weiver.company.type.DecisionMaking;
import com.weiver.company.type.OperationStyle;
import com.weiver.company.type.RoleDefinition;
import com.weiver.company.type.WorkPace;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.service.JobPostingService;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.matching.domain.MatchResult;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.repository.NotificationRepository;
import com.weiver.notification.type.NotificationType;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JobPostingSliceIntegrationTest.JpaAuditingTestConfig.class)
class JobPostingSliceIntegrationTest {
    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditingTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) { return new JPAQueryFactory(em); }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void slicePreservesIsolationOrderAndCountsWithoutCountQueryOrNPlusOne(boolean filtered) {
        Company target = persistCompany("target", "target-login");
        Company other = persistCompany("other", "other-login");
        JobPosting oldest = persistJob(target, JobPostingStatus.ACTIVE);
        JobPosting lower = persistJob(target, JobPostingStatus.ACTIVE);
        JobPosting middle = persistJob(target, JobPostingStatus.ACTIVE);
        JobPosting latest = persistJob(target, JobPostingStatus.ACTIVE);
        JobPosting draft = persistJob(target, JobPostingStatus.DRAFT);
        persistJob(other, JobPostingStatus.ACTIVE);
        MatchResult match = persistMatch(latest, "first");
        persistNotification(target, match, "unread");
        persistNotification(target, match, "duplicate unread");
        persistNotification(target, persistMatch(latest, "second"), "second applicant");
        persistNotification(target, persistMatch(latest, "read"), "already read").markAsRead();
        persistNotification(target, persistMatch(oldest, "off-page"), "off page");
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE job_postings SET create_time = :time")
                .setParameter("time", LocalDateTime.of(2026, 1, 2, 12, 0)).executeUpdate();
        // Keep the draft older so both query paths exercise a full first slice.
        entityManager.createNativeQuery("UPDATE job_postings SET create_time = :time WHERE jd_id = :id")
                .setParameter("time", LocalDateTime.of(2026, 1, 1, 12, 0))
                .setParameter("id", draft.getJdId()).executeUpdate();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var service = new JobPostingService(null, notificationRepository, jobPostingRepository, null, null, null);
        JobPostingStatus status = filtered ? JobPostingStatus.ACTIVE : null;
        var first = service.searchJobPostingsList("target", status, 0, 3);
        assertThat(first.content()).extracting(d -> d.jdId())
                .containsExactly(latest.getJdId(), middle.getJdId(), lower.getJdId());
        assertThat(first.content()).extracting(d -> d.newApplicantCount()).containsExactly(2L, 0L, 0L);
        assertThat(first.pageable().hasNext()).isTrue();
        // One bounded content query plus one grouped applicant query, no total count or lazy loads.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(4);

        entityManager.clear();
        statistics.clear();
        var second = service.searchJobPostingsList("target", status, 1, 3);
        assertThat(second.content()).extracting(d -> d.jdId())
                .containsExactlyElementsOf(filtered ? List.of(oldest.getJdId()) : List.of(oldest.getJdId(), draft.getJdId()));
        assertThat(second.content().getFirst().newApplicantCount()).isEqualTo(1L);
        assertThat(second.pageable().hasNext()).isFalse();
        assertThat(second.pageable().isLast()).isTrue();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        statistics.clear();
        var empty = service.searchJobPostingsList("target", status, 2, 3);
        assertThat(empty.content()).isEmpty();
        assertThat(empty.pageable().isLast()).isTrue();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void exactlyFullSliceHasNoNextPage() {
        Company company = persistCompany("exact", "exact-login");
        for (int i = 0; i < 3; i++) persistJob(company, JobPostingStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();
        var slice = jobPostingRepository.findByCompany_PublicId("exact",
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "createTime", "jdId")));
        assertThat(slice.getContent()).hasSize(3);
        assertThat(slice.hasNext()).isFalse();
    }

    private JobPosting persistJob(Company company, JobPostingStatus status) {
        JobPosting job = JobPosting.builder().company(company).title("backend")
                .jobCategory("IT").detailedJob("backend").deadline(LocalDate.of(2027, 1, 1))
                .status(status).build();
        entityManager.persist(job);
        return job;
    }

    private MatchResult persistMatch(JobPosting job, String suffix) {
        Applicant applicant = Applicant.builder().publicId(suffix).email(suffix + "@test.com")
                .password("encoded-password").build();
        entityManager.persist(applicant);
        MatchResult match = MatchResult.builder().jobPosting(job).applicant(applicant).build();
        entityManager.persist(match);
        return match;
    }

    private Company persistCompany(String publicId, String loginId) {
        Company company = Company.builder()
                .publicId(publicId)
                .loginId(loginId)
                .password("encoded-password")
                .companyType(CompanyType.STARTUP)
                .employeeNum(10)
                .companyCeoName("홍길동")
                .companyName(loginId + " company")
                .foundedYear(LocalDate.of(2020, 1, 1))
                .avgSale(100)
                .address("서울시")
                .cultureDescription("수평적인 문화")
                .directionDescription("성장")
                .workPace(WorkPace.FAST_EXECUTION)
                .decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                .operationStyle(OperationStyle.STABILITY_ORIENTED)
                .build();
        entityManager.persist(company);
        return company;
    }

    private Notification persistNotification(
            Company company,
            MatchResult matchResult,
            String message
    ) {
        Notification notification = Notification.builder()
                .company(company)
                .matchResult(matchResult)
                .type(NotificationType.RESUME_MATCH_TALENT)
                .message(message)
                .build();
        entityManager.persist(notification);
        return notification;
    }

}
