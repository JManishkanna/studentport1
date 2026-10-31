package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Assessment;
import com.academy.portal.domain.Grade;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.dto.AssessmentDto;
import com.academy.portal.dto.AssessmentRequest;
import com.academy.portal.dto.GradeEntriesRequest;
import com.academy.portal.dto.GradeSheetDto;
import com.academy.portal.repository.AssessmentRepository;
import com.academy.portal.repository.GradeRepository;
import com.academy.portal.repository.SubjectRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {

    public static final Set<String> KINDS = Set.of("ASSIGNMENT", "QUIZ", "EXAM");
    private static final int MAX_MARKS_CEILING = 1000;

    private final AssessmentRepository assessmentRepository;
    private final GradeRepository gradeRepository;
    private final SubjectRepository subjectRepository;
    private final CurrentUserService currentUserService;
    private final TeachingScope scope;
    private final ProfilePhotoService photoService;

    public AssessmentService(AssessmentRepository assessmentRepository,
                             GradeRepository gradeRepository,
                             SubjectRepository subjectRepository,
                             CurrentUserService currentUserService,
                             TeachingScope scope,
                             ProfilePhotoService photoService) {
        this.assessmentRepository = assessmentRepository;
        this.gradeRepository = gradeRepository;
        this.subjectRepository = subjectRepository;
        this.currentUserService = currentUserService;
        this.scope = scope;
        this.photoService = photoService;
    }

    // ---- listing -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AssessmentDto> list(String classLevel, String section, Long subjectId) {
        AppUser me = currentUserService.requireCurrentUser();
        String level = AttendanceService.trimToNull(classLevel);
        String division = AttendanceService.trimToNull(section);

        List<Assessment> visible = visibleTo(me).stream()
                .filter(a -> level == null || level.equals(a.getClassLevel()))
                .filter(a -> division == null || division.equals(a.getSection()))
                .filter(a -> subjectId == null || subjectId.equals(a.getSubjectId()))
                .toList();

        Map<Long, Subject> catalog = catalog();
        Map<String, String> teachers = scope.teacherNames();
        if (me.getRole() == Role.STUDENT) {
            Long studentId = me.getStudentId();
            Map<Long, Grade> mine = gradeRepository.findByStudentId(studentId).stream()
                    .collect(Collectors.toMap(Grade::getAssessmentId, Function.identity()));
            return visible.stream()
                    .map(a -> studentView(a, catalog, teachers, mine.get(a.getId())))
                    .toList();
        }

        Map<String, List<Student>> rosters = scope.rosters();
        Map<Long, List<Grade>> grades = gradeRepository
                .findByAssessmentIdIn(visible.stream().map(Assessment::getId).toList()).stream()
                .collect(Collectors.groupingBy(Grade::getAssessmentId));
        return visible.stream()
                .map(a -> staffView(a, catalog, teachers,
                        rosters.getOrDefault(key(a), List.of()), grades.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    /** The assessments a person may see: all, their teaching, or their class and enrolled subjects. */
    List<Assessment> visibleTo(AppUser me) {
        List<Assessment> all = assessmentRepository.findAllByOrderByDueDateAscIdAsc();
        return switch (me.getRole()) {
            case ADMIN -> all;
            case TEACHER -> {
                Set<String> keys = scope.teacherKeys(me);
                yield all.stream().filter(a -> keys.contains(key(a))).toList();
            }
            case STUDENT -> {
                Student student = scope.requireStudent(me);
                Set<Long> subjects = scope.enrolledSubjects(student.getId());
                yield all.stream()
                        .filter(a -> Objects.equals(a.getClassLevel(), student.getClassLevel())
                                && Objects.equals(a.getSection(), student.getSection())
                                && subjects.contains(a.getSubjectId()))
                        .toList();
            }
        };
    }

    // ---- create, update, delete --------------------------------------------

    @Transactional
    public AssessmentDto create(AssessmentRequest request) {
        AppUser me = currentUserService.requireCurrentUser();
        Assessment assessment = new Assessment();
        apply(assessment, request, me, false);
        assessment.setCreatedBy(me.getId());
        return staffDto(assessmentRepository.save(assessment));
    }

    @Transactional
    public AssessmentDto update(Long id, AssessmentRequest request) {
        AppUser me = currentUserService.requireCurrentUser();
        Assessment assessment = load(id);
        assertCanManage(assessment, me);
        apply(assessment, request, me, !gradeRepository.findByAssessmentId(id).isEmpty());
        return staffDto(assessmentRepository.save(assessment));
    }

    @Transactional
    public void delete(Long id) {
        AppUser me = currentUserService.requireCurrentUser();
        Assessment assessment = load(id);
        assertCanManage(assessment, me);
        gradeRepository.deleteAll(gradeRepository.findByAssessmentId(id));
        assessmentRepository.delete(assessment);
    }

    private void apply(Assessment assessment, AssessmentRequest request, AppUser me, boolean hasMarks) {
        if (request == null) {
            throw new BadRequestException("Send the assessment details");
        }
        if (request.subjectId() == null) {
            throw new BadRequestException("Choose the subject");
        }
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new NotFoundException("No subject found with id " + request.subjectId()));
        String level = AttendanceService.trimToNull(request.classLevel());
        String division = AttendanceService.trimToNull(request.section());
        if (level == null) {
            throw new BadRequestException("Choose the class");
        }
        if (!scope.onTimetable(subject.getId(), level, division)) {
            throw new BadRequestException(subject.getName() + " is not on the timetable for " + level
                    + (division == null ? "" : " section " + division) + ".");
        }
        if (!scope.teaches(me, subject.getId(), level, division)) {
            throw new AccessDeniedException("You can only set work for subjects you teach in that class");
        }
        boolean moved = assessment.getId() != null
                && (!Objects.equals(assessment.getSubjectId(), subject.getId())
                || !Objects.equals(assessment.getClassLevel(), level)
                || !Objects.equals(assessment.getSection(), division));
        if (moved && hasMarks) {
            throw new BadRequestException("Marks are already recorded, so the subject and class cannot change. "
                    + "Create a new assessment instead.");
        }

        String title = AttendanceService.trimToNull(request.title());
        if (title == null) {
            throw new BadRequestException("Give the assessment a title");
        }
        if (title.length() > 120) {
            throw new BadRequestException("Keep the title to 120 characters or fewer");
        }
        String kind = request.kind() == null ? "" : request.kind().trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            throw new BadRequestException("The type must be ASSIGNMENT, QUIZ or EXAM");
        }
        if (request.dueDate() == null) {
            throw new BadRequestException("Choose the due date");
        }
        Integer max = request.maxMarks();
        if (max == null || max < 1 || max > MAX_MARKS_CEILING) {
            throw new BadRequestException("Maximum marks must be between 1 and " + MAX_MARKS_CEILING);
        }
        if (hasMarks) {
            BigDecimal highest = gradeRepository.findByAssessmentId(assessment.getId()).stream()
                    .map(Grade::getMarks)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            if (highest.compareTo(BigDecimal.valueOf(max)) > 0) {
                throw new BadRequestException("A student already has " + highest.stripTrailingZeros().toPlainString()
                        + " marks, so the maximum cannot be lower than that");
            }
        }
        String description = AttendanceService.trimToNull(request.description());
        if (description != null && description.length() > 500) {
            throw new BadRequestException("Keep the description to 500 characters or fewer");
        }
        Long ownId = assessment.getId() == null ? -1L : assessment.getId();
        if (assessmentRepository.existsBySubjectIdAndClassLevelAndSectionAndTitleIgnoreCaseAndIdNot(
                subject.getId(), level, division, title, ownId)) {
            throw new ConflictException("\"" + title + "\" already exists for " + subject.getName() + " in this class");
        }

        assessment.setSubjectId(subject.getId());
        assessment.setClassLevel(level);
        assessment.setSection(division);
        assessment.setTitle(title);
        assessment.setKind(kind);
        assessment.setDueDate(request.dueDate());
        assessment.setMaxMarks(max);
        assessment.setDescription(description);
    }

    // ---- marking -----------------------------------------------------------

    @Transactional(readOnly = true)
    public GradeSheetDto sheet(Long id) {
        AppUser me = currentUserService.requireCurrentUser();
        Assessment assessment = load(id);
        assertCanManage(assessment, me);
        return buildSheet(assessment);
    }

    @Transactional
    public GradeSheetDto saveGrades(Long id, GradeEntriesRequest request) {
        AppUser me = currentUserService.requireCurrentUser();
        Assessment assessment = load(id);
        assertCanManage(assessment, me);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new BadRequestException("Send at least one student's marks");
        }

        Map<Long, Student> roster = scope.roster(assessment.getSubjectId(), assessment.getClassLevel(),
                        assessment.getSection()).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<Long, Grade> existing = gradeRepository.findByAssessmentId(id).stream()
                .collect(Collectors.toMap(Grade::getStudentId, Function.identity()));
        BigDecimal max = BigDecimal.valueOf(assessment.getMaxMarks());
        Set<Long> seen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (GradeEntriesRequest.Entry entry : request.entries()) {
            if (entry == null || entry.studentId() == null) {
                throw new BadRequestException("Every row needs a student");
            }
            Student student = roster.get(entry.studentId());
            if (student == null) {
                throw new BadRequestException("Student " + entry.studentId() + " is not enrolled in this class");
            }
            if (!seen.add(student.getId())) {
                throw new BadRequestException("Roll " + student.getRollNumber() + " was sent twice");
            }
            Grade grade = existing.get(student.getId());
            if (entry.marks() == null) {
                if (grade != null) {
                    gradeRepository.delete(grade);
                }
                continue;
            }
            BigDecimal marks = entry.marks();
            if (marks.signum() < 0 || marks.compareTo(max) > 0) {
                throw new BadRequestException("Marks for roll " + student.getRollNumber() + " must be between 0 and "
                        + assessment.getMaxMarks());
            }
            if (marks.stripTrailingZeros().scale() > 2) {
                throw new BadRequestException("Marks can have at most two decimal places");
            }
            String remark = AttendanceService.trimToNull(entry.remark());
            if (remark != null && remark.length() > 200) {
                throw new BadRequestException("Keep the remark for roll " + student.getRollNumber()
                        + " to 200 characters or fewer");
            }
            if (grade == null) {
                grade = new Grade();
                grade.setAssessmentId(id);
                grade.setStudentId(student.getId());
            }
            grade.setMarks(marks);
            grade.setRemark(remark);
            grade.setGradedBy(me.getId());
            grade.setGradedAt(now);
            gradeRepository.save(grade);
        }
        gradeRepository.flush();
        return buildSheet(assessment);
    }

    private GradeSheetDto buildSheet(Assessment assessment) {
        Map<Long, Grade> grades = gradeRepository.findByAssessmentId(assessment.getId()).stream()
                .collect(Collectors.toMap(Grade::getStudentId, Function.identity()));
        List<GradeSheetDto.Row> rows = scope.roster(assessment.getSubjectId(), assessment.getClassLevel(),
                        assessment.getSection()).stream()
                .map(student -> {
                    Grade grade = grades.get(student.getId());
                    return new GradeSheetDto.Row(student.getId(), student.getRollNumber(),
                            (student.getFirstName() + " " + student.getLastName()).trim(),
                            photoService.urlForPerson(Role.STUDENT, student.getId()).orElse(null),
                            grade == null ? null : grade.getMarks().doubleValue(),
                            grade == null ? null : grade.getRemark());
                })
                .toList();
        return new GradeSheetDto(staffDto(assessment), rows);
    }

    // ---- mapping -----------------------------------------------------------

    private AssessmentDto staffDto(Assessment assessment) {
        List<Student> roster = scope.roster(assessment.getSubjectId(), assessment.getClassLevel(), assessment.getSection());
        return staffView(assessment, catalog(), scope.teacherNames(), roster,
                gradeRepository.findByAssessmentId(assessment.getId()));
    }

    private AssessmentDto staffView(Assessment a, Map<Long, Subject> catalog, Map<String, String> teachers,
                                    List<Student> roster, List<Grade> grades) {
        Set<Long> onRoster = roster.stream().map(Student::getId).collect(Collectors.toSet());
        List<Grade> counted = grades.stream().filter(g -> onRoster.contains(g.getStudentId())).toList();
        Double average = counted.isEmpty() ? null : round1(counted.stream()
                .mapToDouble(g -> g.getMarks().doubleValue()).average().orElse(0) * 100.0 / a.getMaxMarks());
        Subject subject = catalog.get(a.getSubjectId());
        return new AssessmentDto(a.getId(), a.getSubjectId(), code(subject), name(subject, a),
                teachers.get(key(a)), a.getClassLevel(), a.getSection(), a.getTitle(), a.getKind(), a.getDueDate(),
                a.getMaxMarks(), a.getDescription(), counted.size(), roster.size(), average,
                null, null, null, true);
    }

    private AssessmentDto studentView(Assessment a, Map<Long, Subject> catalog, Map<String, String> teachers,
                                      Grade grade) {
        Subject subject = catalog.get(a.getSubjectId());
        Double marks = grade == null ? null : grade.getMarks().doubleValue();
        return new AssessmentDto(a.getId(), a.getSubjectId(), code(subject), name(subject, a),
                teachers.get(key(a)), a.getClassLevel(), a.getSection(), a.getTitle(), a.getKind(), a.getDueDate(),
                a.getMaxMarks(), a.getDescription(), null, null, null,
                marks, marks == null ? null : round1(marks * 100.0 / a.getMaxMarks()),
                grade == null ? null : grade.getRemark(), false);
    }

    private void assertCanManage(Assessment assessment, AppUser me) {
        if (!scope.teaches(me, assessment.getSubjectId(), assessment.getClassLevel(), assessment.getSection())) {
            throw new AccessDeniedException("You can only manage work for subjects you teach in that class");
        }
    }

    private Assessment load(Long id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No assessment found with id " + id));
    }

    Map<Long, Subject> catalog() {
        return subjectRepository.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    static String key(Assessment a) {
        return TeachingScope.key(a.getSubjectId(), a.getClassLevel(), a.getSection());
    }

    private static String code(Subject subject) {
        return subject == null ? null : subject.getCode();
    }

    private static String name(Subject subject, Assessment a) {
        return subject == null ? "Subject " + a.getSubjectId() : subject.getName();
    }

    static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
