package com.academy.portal.repository;

import com.academy.portal.domain.Enrollment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudentIdOrderByIdAsc(Long studentId);

    boolean existsByStudentIdAndSubjectId(Long studentId, Long subjectId);

    long countBySubjectId(Long subjectId);

    void deleteByStudentIdAndSubjectId(Long studentId, Long subjectId);
}
