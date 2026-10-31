package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Assessment;
import com.academy.portal.domain.AttendanceRecord;
import com.academy.portal.domain.Grade;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.domain.StudentComment;
import com.academy.portal.dto.AlertsDto;
import com.academy.portal.dto.AtRiskDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.StudentAttendanceDto;
import com.academy.portal.dto.UpcomingDto;
import com.academy.portal.repository.AssessmentRepository;
import com.academy.portal.repository.AttendanceRepository;
import com.academy.portal.repository.GradeRepository;
import com.academy.portal.repository.StudentCommentRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Warnings and what is coming up next, worked out from attendance, marks and the timetable. */
@Service
public class InsightService {

    private static final int RECENT_DAYS = 7;
    private static final int UNMARKED_WINDOW = 7;
    private static final int OVERDUE_WINDOW = 30;
    private static final int MAX_ALERTS = 30;
    private static final int MAX_UPCOMING_CLASSES = 8;
    private static final int MAX_AT_RISK_ALERTS = 6;

    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);

    private final AttendanceRepository attendanceRepository;
    private final AssessmentRepository assessmentRepository;
    private final GradeRepository gradeRepository;
    private final StudentCommentRepository commentRepository;
    private final CurrentUserService currentUserService;
    private final AttendanceService attendanceService;
    private final AssessmentService assessmentService;
    private final TeachingScope scope;

    public InsightService(AttendanceRepository attendanceRepository,
                          AssessmentRepository assessmentRepository,
                          GradeRepository gradeRepository,
                          StudentCommentRepository commentRepository,
                          CurrentUserService currentUserService,
                          AttendanceService attendanceService,
                          AssessmentService assessmentService,
                          TeachingScope scope) {
        this.attendanceRepository = attendanceRepository;
        this.assessmentRepository = assessmentRepository;
        this.gradeRepository = gradeRepository;
        this.commentRepository = commentRepository;
        this.currentUserService = currentUserService;
        this.attendanceService = attendanceService;
        this.assessmentService = assessmentService;
        this.scope = scope;
    }

    // ---- alerts ------------------------------------------------------------

    @Transactional(readOnly = true)
    public AlertsDto alerts() {
        AppUser me = currentUserService.requireCurrentUser();
        LocalDate today = LocalDate.now();
        List<AlertsDto.Alert> alerts = switch (me.getRole()) {
            case STUDENT -> studentAlerts(me, today);
            case TEACHER -> staffAlerts(me, today, true);
            case ADMIN -> staffAlerts(me, today, false);
        };
        alerts.sort(Comparator.comparingInt((AlertsDto.Alert alert) -> rank(alert.level()))
                .thenComparing(AlertsDto.Alert::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AlertsDto.Alert::title));
        int total = alerts.size();
        return new AlertsDto(AttendanceService.LOW_RATE, today, total,
                alerts.stream().limit(MAX_ALERTS).toList());
    }

    private List<AlertsDto.Alert> studentAlerts(AppUser me, LocalDate today) {
        List<AlertsDto.Alert> alerts = new ArrayList<>();
        StudentAttendanceDto attendance = attendanceService.myAttendance(today.minusDays(29), today);
        if (attendance.total() > 0 && attendance.rate() < AttendanceService.LOW_RATE) {
            long attended = attendance.present() + attendance.late();
            long needed = Math.max(0, (long) Math.ceil(3.0 * attendance.total() - 4.0 * attended));
            alerts.add(new AlertsDto.Alert("danger", "ATTENDANCE", "Attendance below 75%",
                    "You attended " + attended + " of " + attendance.total() + " marked classes ("
                            + String.format(Locale.ENGLISH, "%.1f", attendance.rate())
                            + "%). Attend " + needed + " more in a row to get back above the line.",
                    today, "attendance.html"));
        }
        for (StudentAttendanceDto.SubjectRow subject : attendance.subjects()) {
            if (subject.total() > 0 && subject.rate() < AttendanceService.LOW_RATE) {
                alerts.add(new AlertsDto.Alert("warn", "ATTENDANCE",
                        "Low attendance in " + subject.subjectName(),
                        subject.absent() + " of " + subject.total() + " classes missed, a rate of "
                                + String.format(Locale.ENGLISH, "%.1f", subject.rate()) + "%.",
                        today, "attendance.html"));
            }
        }
        LocalDate recent = today.minusDays(RECENT_DAYS - 1L);
        for (StudentAttendanceDto.HistoryRow row : attendance.history()) {
            if ("ABSENT".equals(row.status()) && !row.date().isBefore(recent)) {
                alerts.add(new AlertsDto.Alert("warn", "ABSENCE",
                        "Missed " + (row.subjectName() == null ? "a class" : row.subjectName()),
                        "Marked absent for the " + row.startTime() + " class on " + row.date().format(SHORT_DAY) + ".",
                        row.date(), "attendance.html"));
            }
        }

        Set<Long> graded = gradeRepository.findByStudentId(me.getStudentId()).stream()
                .map(Grade::getAssessmentId).collect(Collectors.toSet());
        Map<Long, Subject> catalog = assessmentService.catalog();
        for (Assessment assessment : assessmentService.visibleTo(me)) {
            long daysLeft = ProgressService.daysLeft(assessment.getDueDate(), today);
            if (daysLeft < 0 || daysLeft > RECENT_DAYS) {
                continue;
            }
            String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
            alerts.add(new AlertsDto.Alert(daysLeft <= 2 ? "danger" : "warn", "DEADLINE",
                    assessment.getTitle() + " due " + when,
                    kindLabel(assessment.getKind()) + " for " + subjectName(catalog, assessment) + " on "
                            + assessment.getDueDate().format(SHORT_DAY)
                            + (graded.contains(assessment.getId()) ? ", marks already in." : ", marks not in yet."),
                    assessment.getDueDate(), "progress.html"));
        }

        for (StudentComment comment : commentRepository
                .findByStudentIdAndCreatedAtAfterOrderByCreatedAtDesc(me.getStudentId(),
                        today.minusDays(RECENT_DAYS - 1L).atStartOfDay())) {
            alerts.add(new AlertsDto.Alert("info", "COMMENT",
                    "Note from " + comment.getAuthorName(),
                    shorten(comment.getBody()), comment.getCreatedAt().toLocalDate(),
                    "student-profile.html?me=1#notes"));
        }
        return alerts;
    }

    private List<AlertsDto.Alert> staffAlerts(AppUser me, LocalDate today, boolean perSession) {
        List<AlertsDto.Alert> alerts = new ArrayList<>();

        // Sessions from the past week that still have an incomplete roll.
        Map<String, Integer> marked = new HashMap<>();
        for (AttendanceRecord record : attendanceRepository.findInRange(
                today.minusDays(UNMARKED_WINDOW - 1L), today, null, null)) {
            marked.merge(AttendanceService.sessionKey(record.getSubjectId(), record.getClassLevel(),
                    record.getSection(), record.getStartTime()), 1, Integer::sum);
        }
        Map<String, List<Student>> rosters = scope.rosters();
        Map<LocalDate, List<String>> byDate = new TreeMap<>();
        for (LocalDate date = today.minusDays(UNMARKED_WINDOW - 1L); !date.isAfter(today); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            for (ScheduleRow row : scope.rows(me)) {
                if (row.dayOfWeek() == null || row.dayOfWeek() != weekday) {
                    continue;
                }
                if (date.equals(today) && row.startTime().isAfter(LocalTime.now())) {
                    continue;
                }
                int size = rosters.getOrDefault(TeachingScope.key(row.subjectId(), row.classLevel(),
                        row.section()), List.of()).size();
                if (size == 0) {
                    continue;
                }
                int done = marked.getOrDefault(AttendanceService.sessionKey(row.subjectId(), row.classLevel(),
                        row.section(), row.startTime()), 0);
                if (done >= size) {
                    continue;
                }
                if (perSession) {
                    alerts.add(new AlertsDto.Alert("warn", "UNMARKED",
                            "Attendance not marked for " + row.subjectName(),
                            row.classLevel() + (row.section() == null ? "" : " " + row.section()) + " at "
                                    + row.startTime() + " on " + date.format(SHORT_DAY) + ", " + done + " of "
                                    + size + " marked.",
                            date, "attendance.html?date=" + date));
                } else {
                    byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(row.subjectCode());
                }
            }
        }
        byDate.forEach((date, codes) -> alerts.add(new AlertsDto.Alert("warn", "UNMARKED",
                codes.size() + " session" + (codes.size() == 1 ? "" : "s") + " not marked on "
                        + date.format(SHORT_DAY),
                "Still open: " + String.join(", ", codes) + ".", date, "attendance.html?date=" + date)));

        // Students under the attendance threshold in the caller's classes.
        AtRiskDto atRisk = attendanceService.atRisk(today.minusDays(29), today, null, null);
        for (AtRiskDto.Row row : atRisk.students().stream().limit(MAX_AT_RISK_ALERTS).toList()) {
            String rate = String.format(Locale.ENGLISH, "%.1f", row.rate());
            alerts.add(new AlertsDto.Alert("danger", "AT_RISK",
                    row.fullName() + " is at " + rate + "% attendance",
                    row.rollNumber() + " · " + row.classLevel() + (row.section() == null ? "" : " " + row.section())
                            + " · " + row.absent() + " of " + row.total() + " missed. "
                            + row.needed() + " more classes in a row reaches 75%.",
                    today, "student-profile.html?id=" + row.studentId()));
        }

        // Past work with marks still missing.
        Map<Long, Subject> catalog = assessmentService.catalog();
        List<Assessment> visible = assessmentService.visibleTo(me);
        List<Assessment> overdue = visible.stream()
                .filter(a -> a.getDueDate().isBefore(today)
                        && !a.getDueDate().isBefore(today.minusDays(OVERDUE_WINDOW)))
                .toList();
        Map<Long, Long> gradedCounts = gradeRepository.findByAssessmentIdIn(
                        visible.stream().map(Assessment::getId).toList()).stream()
                .collect(Collectors.groupingBy(Grade::getAssessmentId, Collectors.counting()));
        for (Assessment assessment : overdue) {
            int size = rosters.getOrDefault(AssessmentService.key(assessment), List.of()).size();
            long graded = gradedCounts.getOrDefault(assessment.getId(), 0L);
            if (size == 0 || graded >= size) {
                continue;
            }
            alerts.add(new AlertsDto.Alert("warn", "MARKS",
                    "Marks missing for " + assessment.getTitle(),
                    subjectName(catalog, assessment) + " · " + assessment.getClassLevel()
                            + (assessment.getSection() == null ? "" : " " + assessment.getSection()) + " · due "
                            + assessment.getDueDate().format(SHORT_DAY) + ", " + graded + " of " + size
                            + " marked.",
                    assessment.getDueDate(), "progress.html"));
        }
        for (Assessment assessment : visible) {
            long daysLeft = ProgressService.daysLeft(assessment.getDueDate(), today);
            if (daysLeft < 0 || daysLeft > RECENT_DAYS) {
                continue;
            }
            alerts.add(new AlertsDto.Alert("info", "DEADLINE",
                    assessment.getTitle() + " due " + (daysLeft == 0 ? "today" : "in " + daysLeft + " days"),
                    kindLabel(assessment.getKind()) + " for " + subjectName(catalog, assessment) + " · "
                            + assessment.getClassLevel()
                            + (assessment.getSection() == null ? "" : " " + assessment.getSection()) + ", "
                            + assessment.getMaxMarks() + " marks.",
                    assessment.getDueDate(), "progress.html"));
        }
        return alerts;
    }

    // ---- upcoming ----------------------------------------------------------

    @Transactional(readOnly = true)
    public UpcomingDto upcoming(int days) {
        AppUser me = currentUserService.requireCurrentUser();
        LocalDate today = LocalDate.now();
        int window = days < 1 ? 14 : Math.min(days, 60);
        LocalDate until = today.plusDays(window - 1L);

        List<UpcomingDto.ClassItem> classes = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = today.plusDays(offset);
            int weekday = date.getDayOfWeek().getValue();
            for (ScheduleRow row : scope.rows(me)) {
                if (row.dayOfWeek() == null || row.dayOfWeek() != weekday) {
                    continue;
                }
                if (offset == 0 && row.startTime().isBefore(LocalTime.now())) {
                    continue;
                }
                classes.add(new UpcomingDto.ClassItem(date, weekday, row.startTime(), row.endTime(),
                        row.subjectId(), row.subjectCode(), row.subjectName(), row.teacherName(),
                        row.classLevel(), row.section(), row.room()));
            }
        }
        classes.sort(Comparator.comparing(UpcomingDto.ClassItem::date)
                .thenComparing(UpcomingDto.ClassItem::startTime));

        Set<Long> graded = me.getRole() == Role.STUDENT && me.getStudentId() != null
                ? gradeRepository.findByStudentId(me.getStudentId()).stream()
                .map(Grade::getAssessmentId).collect(Collectors.toSet())
                : Set.of();
        Map<String, List<Student>> rosters = me.getRole() == Role.STUDENT ? Map.of() : scope.rosters();
        Map<Long, Subject> catalog = assessmentService.catalog();
        Map<Long, Long> gradedCounts = me.getRole() == Role.STUDENT
                ? Map.of()
                : gradeRepository.findByAssessmentIdIn(assessmentService.visibleTo(me).stream()
                .map(Assessment::getId).toList()).stream()
                .collect(Collectors.groupingBy(Grade::getAssessmentId, Collectors.counting()));

        List<UpcomingDto.AssessmentItem> assessments = new ArrayList<>();
        for (Assessment assessment : assessmentService.visibleTo(me)) {
            if (assessment.getDueDate().isBefore(today) || assessment.getDueDate().isAfter(until)) {
                continue;
            }
            Boolean done;
            if (me.getRole() == Role.STUDENT) {
                done = graded.contains(assessment.getId());
            } else {
                int size = rosters.getOrDefault(AssessmentService.key(assessment), List.of()).size();
                done = size > 0 && gradedCounts.getOrDefault(assessment.getId(), 0L) >= size;
            }
            assessments.add(new UpcomingDto.AssessmentItem(assessment.getId(), assessment.getTitle(),
                    assessment.getKind(), assessment.getDueDate(),
                    ProgressService.daysLeft(assessment.getDueDate(), today), assessment.getSubjectId(),
                    subjectCode(catalog, assessment), subjectName(catalog, assessment), assessment.getClassLevel(),
                    assessment.getSection(), assessment.getMaxMarks(), done));
        }
        assessments.sort(Comparator.comparing(UpcomingDto.AssessmentItem::dueDate)
                .thenComparing(UpcomingDto.AssessmentItem::id));

        return new UpcomingDto(today, until,
                classes.stream().limit(MAX_UPCOMING_CLASSES).toList(), assessments);
    }

    // ---- helpers -----------------------------------------------------------

    private static String subjectName(Map<Long, Subject> catalog, Assessment assessment) {
        Subject subject = catalog.get(assessment.getSubjectId());
        return subject == null ? "Subject " + assessment.getSubjectId() : subject.getName();
    }

    private static String subjectCode(Map<Long, Subject> catalog, Assessment assessment) {
        Subject subject = catalog.get(assessment.getSubjectId());
        return subject == null ? null : subject.getCode();
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "ASSIGNMENT" -> "Assignment";
            case "QUIZ" -> "Quiz";
            case "EXAM" -> "Exam";
            default -> "Work";
        };
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String clean = body.replaceAll("\\s+", " ").trim();
        return clean.length() <= 140 ? clean : clean.substring(0, 139) + "\u2026";
    }

    private static int rank(String level) {
        return switch (level) {
            case "danger" -> 0;
            case "warn" -> 1;
            default -> 2;
        };
    }
}
