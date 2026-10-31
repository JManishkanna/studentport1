package com.academy.portal.repository;

import com.academy.portal.domain.Assessment;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findAllByOrderByDueDateAscIdAsc();

    List<Assessment> findByClassLevelAndSectionOrderByDueDateAscIdAsc(String classLevel, String section);

    List<Assessment> findByDueDateBetweenOrderByDueDateAscIdAsc(LocalDate from, LocalDate to);

    boolean existsBySubjectIdAndClassLevelAndSectionAndTitleIgnoreCaseAndIdNot(
            Long subjectId, String classLevel, String section, String title, Long id);
}
