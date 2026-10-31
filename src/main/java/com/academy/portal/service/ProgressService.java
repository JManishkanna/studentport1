package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Assessment;
import com.academy.portal.domain.AttendanceRecord;
import com.academy.portal.domain.Grade;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.dto.ProgressDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.repository.AssessmentRepository;
import com.academy.portal.repository.AttendanceRepository;
import com.academy.portal.repository.GradeRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marks and attendance rolled up per subject, with the trend across assessments. */
@Service
public class ProgressService {

    private static final int ATTENDANCE_WINDOW = 30;

    private final AssessmentRepository assessmentRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final CurrentUserService currentUserService;
    private final TeachingScope scope;

    public ProgressService(AssessmentRepository assessmentRepository,
                           GradeRepository gradeRepository,
                           AttendanceRepository attendanceRepository,
                           StudentRepository studentRepository,
                           SubjectRepository subjectRepository,
                           CurrentUserService currentUserService,
                           TeachingScope scope) {
        this.assessmentRepository = assessmentRepository;
        this.gradeRepository = gradeRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.currentUserService = currentUserService;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public ProgressDto mine() {
        AppUser me = currentUserService.requireCurrentUser();
        Student student = scope.requireStudent(me);
        return build(student);
    }

    @Transactional(readOnly = true)
    public ProgressDto forStudent(Long studentId) {
        AppUser me = currentUserService.requireCurrentUser();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("No student found with id " + studentId));
        boolean allowed = switch (me.getRole()) {
            case ADMIN -> true;
            case TEACHER -> scope.teachesClass(me, student.getClassLevel(), student.getSection());
            case STUDENT -> Objects.equals(me.getStudentId(), student.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("You can only read progress for students you teach");
        }
        return build(student);
    }

    ProgressDto build(Student student) {
        LocalDate today = LocalDate.now();
        Map<Long, Subject> catalog = subjectRepository.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
        Map<String, String> teachers = scope.teacherNames();

        Set<Long> subjects = new LinkedHashSet<>(scope.enrolledSubjects(student.getId()));
        List<ScheduleRow> classRows = new ArrayList<>();
        for (ScheduleRow row : scope.rowsForClass(student.getClassLevel(), student.getSection())) {
            classRows.add(row);
            subjects.add(row.subjectId());
        }

        List<Assessment> mine = assessmentRepository.findAllByOrderByDueDateAscIdAsc().stream()
                .filter(a -> Objects.equals(a.getClassLevel(), student.getClassLevel())
                        && Objects.equals(a.getSection(), student.getSection())
                        && subjects.contains(a.getSubjectId()))
                .sorted(Comparator.comparing(Assessment::getDueDate).thenComparing(Assessment::getId))
                .toList();
        Map<Long, Grade> grades = gradeRepository.findByStudentId(student.getId()).stream()
                .collect(Collectors.toMap(Grade::getAssessmentId, Function.identity()));

        LocalDate since = today.minusDays(ATTENDANCE_WINDOW - 1L);
        List<AttendanceRecord> attendance = attendanceRepository.findForStudent(student.getId(), since, today);
        Map<Long, long[]> attendanceBySubject = new HashMap<>();
        long[] attendanceTotal = new long[3];
        for (AttendanceRecord record : attendance) {
            AttendanceService.tally(attendanceBySubject.computeIfAbsent(record.getSubjectId(), ignored -> new long[3]),
                    record.getStatus());
            AttendanceService.tally(attendanceTotal, record.getStatus());
        }

        List<ProgressDto.SubjectProgress> rows = new ArrayList<>();
        for (Long subjectId : subjects) {
            Subject subject = catalog.get(subjectId);
            if (subject == null) {
                continue;
            }
            List<Assessment> forSubject = mine.stream()
                    .filter(a -> subjectId.equals(a.getSubjectId()))
                    .toList();
            List<ProgressDto.Point> points = forSubject.stream()
                    .map(a -> {
                        Grade grade = grades.get(a.getId());
                        Double marks = grade == null ? null : grade.getMarks().doubleValue();
                        return new ProgressDto.Point(a.getId(), a.getTitle(), a.getKind(), a.getDueDate(),
                                a.getMaxMarks(), marks,
                                marks == null ? null : percent(marks, a.getMaxMarks()),
                                grade == null ? null : grade.getRemark());
                    })
                    .toList();

            double earned = 0;
            int possible = 0;
            int graded = 0;
            int pending = 0;
            for (Assessment a : forSubject) {
                if (grades.containsKey(a.getId())) {
                    graded++;
                    earned += grades.get(a.getId()).getMarks().doubleValue();
                    possible += a.getMaxMarks();
                } else if (!a.getDueDate().isBefore(today)) {
                    pending++;
                }
            }
            Double subjectPercent = possible == 0 ? null : AssessmentService.round1(earned * 100.0 / possible);
            List<ProgressDto.Point> scored = points.stream().filter(p -> p.percent() != null).toList();
            Double latest = scored.isEmpty() ? null : scored.get(scored.size() - 1).percent();
            Double change = scored.size() < 2
                    ? null
                    : AssessmentService.round1(scored.get(scored.size() - 1).percent()
                    - scored.get(scored.size() - 2).percent());

            long[] count = attendanceBySubject.getOrDefault(subjectId, new long[3]);
            long total = AttendanceService.sum(count);
            double rate = AttendanceService.rate(count[0], count[2], total);
            rows.add(new ProgressDto.SubjectProgress(subject.getId(), subject.getCode(), subject.getName(),
                    teachers.get(TeachingScope.key(subject.getId(), student.getClassLevel(), student.getSection())),
                    subjectPercent, graded, pending, latest, change, rate, total,
                    total > 0 && rate < AttendanceService.LOW_RATE, points));
        }
        rows.sort(Comparator.comparing(ProgressDto.SubjectProgress::subjectCode, String.CASE_INSENSITIVE_ORDER));

        double earned = 0;
        int possible = 0;
        for (Assessment a : mine) {
            Grade grade = grades.get(a.getId());
            if (grade != null) {
                earned += grade.getMarks().doubleValue();
                possible += a.getMaxMarks();
            }
        }
        long total = AttendanceService.sum(attendanceTotal);
        double rate = AttendanceService.rate(attendanceTotal[0], attendanceTotal[2], total);
        return new ProgressDto(student.getId(), (student.getFirstName() + " " + student.getLastName()).trim(),
                student.getRollNumber(), student.getClassLevel(), student.getSection(),
                possible == 0 ? null : AssessmentService.round1(earned * 100.0 / possible),
                rate, total, AttendanceService.LOW_RATE, total > 0 && rate < AttendanceService.LOW_RATE, rows);
    }

    /** Days until the assessment falls due; negative once the date has passed. */
    static long daysLeft(LocalDate due, LocalDate today) {
        return ChronoUnit.DAYS.between(today, due);
    }

    private static double percent(double marks, int max) {
        return Math.round(marks * 1000.0 / max) / 10.0;
    }
}
