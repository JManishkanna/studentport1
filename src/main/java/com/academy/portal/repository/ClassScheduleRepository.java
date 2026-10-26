package com.academy.portal.repository;

import com.academy.portal.domain.ClassSchedule;
import com.academy.portal.dto.ScheduleRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, Long> {

    String ROW_PROJECTION = """
            select new com.academy.portal.dto.ScheduleRow(
                cs.id, su.id, su.code, su.name, te.id,
                concat(te.firstName, ' ', te.lastName),
                cs.classLevel, cs.section, cs.room, cs.dayOfWeek, cs.startTime, cs.endTime, cs.term)
            from ClassSchedule cs
            join Subject su on su.id = cs.subjectId
            join Teacher te on te.id = cs.teacherId
            """;

    @Query(ROW_PROJECTION + "order by cs.dayOfWeek, cs.startTime, cs.classLevel, cs.section")
    List<ScheduleRow> allRows();

    @Query(ROW_PROJECTION + """
            where cs.classLevel = :classLevel and cs.section = :section
            order by cs.dayOfWeek, cs.startTime
            """)
    List<ScheduleRow> rowsForClass(@Param("classLevel") String classLevel, @Param("section") String section);

    @Query(ROW_PROJECTION + """
            where cs.teacherId = :teacherId
            order by cs.dayOfWeek, cs.startTime
            """)
    List<ScheduleRow> rowsForTeacher(@Param("teacherId") Long teacherId);

    @Query(ROW_PROJECTION + """
            where cs.subjectId = :subjectId
            order by cs.dayOfWeek, cs.startTime
            """)
    List<ScheduleRow> rowsForSubject(@Param("subjectId") Long subjectId);

    @Query(ROW_PROJECTION + "where cs.dayOfWeek = :dayOfWeek order by cs.startTime, cs.classLevel")
    List<ScheduleRow> rowsForDay(@Param("dayOfWeek") int dayOfWeek);

    Optional<ClassSchedule> findBySubjectIdAndClassLevelAndSectionAndDayOfWeekAndStartTime(
            Long subjectId, String classLevel, String section, Integer dayOfWeek, java.time.LocalTime startTime);

    long countByDayOfWeek(int dayOfWeek);
}
