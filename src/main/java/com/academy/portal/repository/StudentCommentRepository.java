package com.academy.portal.repository;

import com.academy.portal.domain.StudentComment;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCommentRepository extends JpaRepository<StudentComment, Long> {

    List<StudentComment> findByStudentIdOrderByCreatedAtDescIdDesc(Long studentId);

    List<StudentComment> findByStudentIdAndCreatedAtAfterOrderByCreatedAtDesc(Long studentId, LocalDateTime after);
}
