package com.academy.portal.repository;

import com.academy.portal.domain.Subject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByCodeAsc();
}
