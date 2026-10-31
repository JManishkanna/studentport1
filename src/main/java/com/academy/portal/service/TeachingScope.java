package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Enrollment;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.repository.ClassScheduleRepository;
import com.academy.portal.repository.EnrollmentRepository;
import com.academy.portal.repository.StudentRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Which classes and subjects a signed-in person teaches or studies, read from the timetable. */
@Component
public class TeachingScope {

    private final ClassScheduleRepository scheduleRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public TeachingScope(ClassScheduleRepository scheduleRepository,
                         StudentRepository studentRepository,
                         EnrollmentRepository enrollmentRepository) {
        this.scheduleRepository = scheduleRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** Timetable rows the person can see: everything, their own teaching, or their own class. */
    public List<ScheduleRow> rows(AppUser me) {
        return switch (me.getRole()) {
            case ADMIN -> scheduleRepository.allRows();
            case TEACHER -> scheduleRepository.rowsForTeacher(requireTeacherId(me));
            case STUDENT -> {
                Student student = requireStudent(me);
                yield scheduleRepository.allRows().stream()
                        .filter(row -> Objects.equals(row.classLevel(), student.getClassLevel())
                                && Objects.equals(row.section(), student.getSection()))
                        .toList();
            }
        };
    }

    public List<ScheduleRow> rowsForClass(String classLevel, String section) {
        return scheduleRepository.rowsForClass(classLevel, section);
    }

    /** Subject-in-class keys a teacher teaches. */
    public Set<String> teacherKeys(AppUser me) {
        return scheduleRepository.rowsForTeacher(requireTeacherId(me)).stream()
                .map(row -> key(row.subjectId(), row.classLevel(), row.section()))
                .collect(Collectors.toSet());
    }

    public boolean teaches(AppUser me, Long subjectId, String classLevel, String section) {
        if (me.getRole() == Role.ADMIN) {
            return true;
        }
        return me.getRole() == Role.TEACHER && teacherKeys(me).contains(key(subjectId, classLevel, section));
    }

    public boolean teachesClass(AppUser me, String classLevel, String section) {
        if (me.getRole() == Role.ADMIN) {
            return true;
        }
        if (me.getRole() != Role.TEACHER) {
            return false;
        }
        return scheduleRepository.rowsForTeacher(requireTeacherId(me)).stream()
                .anyMatch(row -> Objects.equals(row.classLevel(), classLevel)
                        && Objects.equals(row.section(), section));
    }

    /** True when some timetable row runs this subject for this class division. */
    public boolean onTimetable(Long subjectId, String classLevel, String section) {
        return scheduleRepository.rowsForSubject(subjectId).stream()
                .anyMatch(row -> Objects.equals(row.classLevel(), classLevel)
                        && Objects.equals(row.section(), section));
    }

    /** Teacher names per subject-in-class key, joined when a subject is shared. */
    public Map<String, String> teacherNames() {
        Map<String, Set<String>> names = new HashMap<>();
        for (ScheduleRow row : scheduleRepository.allRows()) {
            names.computeIfAbsent(key(row.subjectId(), row.classLevel(), row.section()),
                    ignored -> new LinkedHashSet<>()).add(row.teacherName());
        }
        Map<String, String> joined = new HashMap<>();
        names.forEach((k, v) -> joined.put(k, String.join(", ", v)));
        return joined;
    }

    /** Active students of a class division who are enrolled in the subject, by roll number. */
    public List<Student> roster(Long subjectId, String classLevel, String section) {
        List<Student> answer = new ArrayList<>();
        for (Student student : studentRepository.findByClassLevelAndSectionOrderByRollNumberAsc(classLevel, section)) {
            if ("ACTIVE".equals(student.getStatus())
                    && enrollmentRepository.existsByStudentIdAndSubjectId(student.getId(), subjectId)) {
                answer.add(student);
            }
        }
        return answer;
    }

    /** Roster sizes for many assessments at once, keyed like {@link #key}. */
    public Map<String, List<Student>> rosters() {
        Set<String> enrolled = new HashSet<>();
        for (Enrollment enrollment : enrollmentRepository.findAll()) {
            enrolled.add(enrollment.getStudentId() + ":" + enrollment.getSubjectId());
        }
        Map<String, List<Student>> answer = new HashMap<>();
        List<Student> students = studentRepository.findAllByOrderByClassLevelAscSectionAscRollNumberAsc().stream()
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .toList();
        for (ScheduleRow row : scheduleRepository.allRows()) {
            String k = key(row.subjectId(), row.classLevel(), row.section());
            if (answer.containsKey(k)) {
                continue;
            }
            answer.put(k, students.stream()
                    .filter(student -> Objects.equals(student.getClassLevel(), row.classLevel())
                            && Objects.equals(student.getSection(), row.section())
                            && enrolled.contains(student.getId() + ":" + row.subjectId()))
                    .toList());
        }
        return answer;
    }

    public Student requireStudent(AppUser me) {
        Long studentId = me.getStudentId();
        if (studentId == null) {
            throw new BadRequestException("This sign-in is not linked to a student profile");
        }
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("No student found with id " + studentId));
    }

    public Set<Long> enrolledSubjects(Long studentId) {
        return enrollmentRepository.findByStudentIdOrderByIdAsc(studentId).stream()
                .map(Enrollment::getSubjectId)
                .collect(Collectors.toSet());
    }

    public Long requireTeacherId(AppUser me) {
        if (me.getTeacherId() == null) {
            throw new AccessDeniedException("This sign-in is not linked to a teacher record");
        }
        return me.getTeacherId();
    }

    public static String key(Long subjectId, String classLevel, String section) {
        return subjectId + "\u0001" + AttendanceService.pair(classLevel, section);
    }
}
