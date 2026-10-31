package com.academy.portal.repository;

import com.academy.portal.domain.Grade;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    List<Grade> findByAssessmentId(Long assessmentId);

    List<Grade> findByAssessmentIdIn(Collection<Long> assessmentIds);

    List<Grade> findByStudentId(Long studentId);
}
