package com.academy.portal.repository;

import com.academy.portal.domain.AttendanceRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findBySessionDate(LocalDate sessionDate);

    List<AttendanceRecord> findByClassLevelAndSectionAndSubjectIdAndSessionDateAndStartTime(
            String classLevel, String section, Long subjectId, LocalDate sessionDate, LocalTime startTime);

    @Query("""
            select a from AttendanceRecord a
            where a.sessionDate between :from and :to
              and (:classLevel is null or a.classLevel = :classLevel)
              and (:section is null or a.section = :section)
            order by a.sessionDate, a.classLevel, a.section, a.startTime, a.studentId
            """)
    List<AttendanceRecord> findInRange(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("classLevel") String classLevel,
                                       @Param("section") String section);

    @Query("""
            select a from AttendanceRecord a
            where a.studentId = :studentId
              and a.sessionDate between :from and :to
            order by a.sessionDate desc, a.startTime desc
            """)
    List<AttendanceRecord> findForStudent(@Param("studentId") Long studentId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
}
