package com.academy.portal.repository;

import com.academy.portal.domain.Teacher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("""
            select t from Teacher t
            where (:pattern is null
                   or lower(concat(t.firstName, ' ', t.lastName)) like :pattern
                   or lower(t.email) like :pattern
                   or lower(t.employeeCode) like :pattern)
              and (:department is null or t.department = :department)
            order by t.department, t.lastName
            """)
    List<Teacher> search(@Param("pattern") String pattern, @Param("department") String department);

    @Query("select distinct t.department from Teacher t order by t.department")
    List<String> findDistinctDepartments();
}
