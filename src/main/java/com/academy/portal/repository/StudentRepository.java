package com.academy.portal.repository;

import com.academy.portal.domain.Student;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRollNumberIgnoreCase(String rollNumber);

    boolean existsByRollNumberIgnoreCaseAndIdNot(String rollNumber, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("""
            select s from Student s
            where (:pattern is null
                   or lower(concat(s.firstName, ' ', s.lastName)) like :pattern
                   or lower(s.rollNumber) like :pattern
                   or lower(s.email) like :pattern)
              and (:classLevel is null or s.classLevel = :classLevel)
              and (:status is null or s.status = :status)
            order by s.classLevel, s.section, s.rollNumber
            """)
    List<Student> search(@Param("pattern") String pattern,
                         @Param("classLevel") String classLevel,
                         @Param("status") String status);

    @Query("select distinct s.classLevel from Student s order by s.classLevel")
    List<String> findDistinctClassLevels();

    long countByStatus(String status);
}
