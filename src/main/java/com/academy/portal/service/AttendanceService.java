package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.AttendanceRecord;
import com.academy.portal.domain.ClassSchedule;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.dto.AttendanceMarkRequest;
import com.academy.portal.dto.AttendanceSessionDto;
import com.academy.portal.dto.AtRiskDto;
import com.academy.portal.dto.AttendanceSheetDto;
import com.academy.portal.dto.AttendanceSummaryDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.StudentAttendanceDto;
import com.academy.portal.repository.AttendanceRepository;
import com.academy.portal.repository.ClassScheduleRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {

    public static final String PRESENT = "PRESENT";
    public static final String ABSENT = "ABSENT";
    public static final String LATE = "LATE";
    private static final Set<String> STATUSES = Set.of(PRESENT, ABSENT, LATE);
    private static final int MAX_RANGE_DAYS = 366;
    public static final double LOW_RATE = 75.0;

    private final AttendanceRepository attendanceRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final CurrentUserService currentUserService;
    private final ProfilePhotoService photoService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             ClassScheduleRepository scheduleRepository,
                             StudentRepository studentRepository,
                             SubjectRepository subjectRepository,
                             CurrentUserService currentUserService,
                             ProfilePhotoService photoService) {
        this.attendanceRepository = attendanceRepository;
        this.scheduleRepository = scheduleRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.currentUserService = currentUserService;
        this.photoService = photoService;
    }

    // ---- sessions on a date ------------------------------------------------

    /** Every timetable session that runs on the date, scoped to the caller's own classes for faculty. */
    @Transactional(readOnly = true)
    public List<AttendanceSessionDto> sessions(LocalDate date, String classLevel, String section) {
        LocalDate day = date == null ? LocalDate.now() : date;
        AppUser me = currentUserService.requireCurrentUser();
        List<ScheduleRow> rows = switch (me.getRole()) {
            case ADMIN -> scheduleRepository.allRows();
            case TEACHER -> scheduleRepository.rowsForTeacher(requireTeacherId(me));
            default -> throw new AccessDeniedException("Attendance is managed by administrators and faculty");
        };

        String level = trimToNull(classLevel);
        String division = trimToNull(section);
        Map<String, Integer> marks = markedCounts(day);
        Map<String, List<Student>> rosters = new HashMap<>();
        int weekday = day.getDayOfWeek().getValue();

        List<AttendanceSessionDto> answer = new ArrayList<>();
        for (ScheduleRow row : rows) {
            if (row.dayOfWeek() == null || row.dayOfWeek() != weekday) {
                continue;
            }
            if (level != null && !level.equals(row.classLevel())) {
                continue;
            }
            if (division != null && !division.equals(row.section())) {
                continue;
            }
            List<Student> roster = rosters.computeIfAbsent(pair(row.classLevel(), row.section()),
                    ignored -> activeRoster(row.classLevel(), row.section()));
            int count = roster.size();
            int done = marks.getOrDefault(sessionKey(row.subjectId(), row.classLevel(), row.section(), row.startTime()), 0);
            answer.add(new AttendanceSessionDto(row.id(), row.classLevel(), row.section(), row.subjectId(),
                    row.subjectCode(), row.subjectName(), row.teacherName(), row.startTime(), row.endTime(),
                    row.room(), count, done, count > 0 && done >= count));
        }

        answer.sort(Comparator.comparing(AttendanceSessionDto::classLevel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.section() == null ? "" : row.section())
                .thenComparing(AttendanceSessionDto::startTime));
        return answer;
    }

    // ---- marking -----------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceSheetDto sheet(Long scheduleId, LocalDate date) {
        ClassSchedule schedule = loadSchedule(scheduleId);
        LocalDate day = date == null ? LocalDate.now() : date;
        AppUser me = currentUserService.requireCurrentUser();
        assertCanRecord(schedule, me);
        assertSessionDay(schedule, day);
        return buildSheet(schedule, day);
    }

    /** Saves marks for a session, creating or replacing each student's row for that date. */
    @Transactional
    public AttendanceSheetDto mark(AttendanceMarkRequest request) {
        if (request == null) {
            throw new BadRequestException("Send the session and the attendance rows");
        }
        ClassSchedule schedule = loadSchedule(request.scheduleId());
        if (request.date() == null) {
            throw new BadRequestException("Choose the date of the session");
        }
        AppUser me = currentUserService.requireCurrentUser();
        assertCanRecord(schedule, me);
        assertSessionDay(schedule, request.date());

        List<AttendanceMarkRequest.Mark> entries = request.entries();
        if (entries == null || entries.isEmpty()) {
            throw new BadRequestException("Send at least one student to mark");
        }

        List<Student> roster = activeRoster(schedule.getClassLevel(), schedule.getSection());
        if (roster.isEmpty()) {
            throw new BadRequestException("No active students are enrolled in this class");
        }
        Map<Long, Student> byId = roster.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        Map<Long, AttendanceRecord> existing = attendanceRepository
                .findByClassLevelAndSectionAndSubjectIdAndSessionDateAndStartTime(schedule.getClassLevel(),
                        schedule.getSection(), schedule.getSubjectId(), request.date(), schedule.getStartTime())
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));

        Set<Long> seen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (AttendanceMarkRequest.Mark entry : entries) {
            if (entry == null || entry.studentId() == null) {
                throw new BadRequestException("Every attendance row needs a student");
            }
            Student student = byId.get(entry.studentId());
            if (student == null) {
                throw new BadRequestException("Student " + entry.studentId() + " is not in this class");
            }
            if (!seen.add(student.getId())) {
                throw new BadRequestException("Roll " + student.getRollNumber() + " was sent twice");
            }
            String status = normalizeStatus(entry.status());
            AttendanceRecord record = existing.get(student.getId());
            if (record == null) {
                record = new AttendanceRecord();
                record.setStudentId(student.getId());
                record.setSubjectId(schedule.getSubjectId());
                record.setClassLevel(schedule.getClassLevel());
                record.setSection(schedule.getSection());
                record.setSessionDate(request.date());
                record.setStartTime(schedule.getStartTime());
            }
            record.setStatus(status);
            record.setMarkedBy(me.getId());
            record.setMarkedAt(now);
            attendanceRepository.save(record);
        }

        return buildSheet(schedule, request.date());
    }

    // ---- summaries ---------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceSummaryDto summary(LocalDate from, LocalDate to, String classLevel, String section) {
        DateRange range = normalize(from, to);
        AppUser me = currentUserService.requireCurrentUser();
        if (me.getRole() != Role.ADMIN && me.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Attendance summaries are available to administrators and faculty");
        }

        List<AttendanceRecord> records = scopedRecords(me, range, trimToNull(classLevel), trimToNull(section));

        Map<String, long[]> classes = new TreeMap<>();
        Map<String, String[]> labels = new HashMap<>();
        Map<LocalDate, long[]> days = new TreeMap<>();
        long[] totals = new long[3];
        for (AttendanceRecord record : records) {
            String key = pair(record.getClassLevel(), record.getSection());
            labels.putIfAbsent(key, new String[]{record.getClassLevel(), record.getSection()});
            tally(classes.computeIfAbsent(key, ignored -> new long[3]), record.getStatus());
            tally(days.computeIfAbsent(record.getSessionDate(), ignored -> new long[3]), record.getStatus());
            tally(totals, record.getStatus());
        }

        List<AttendanceSummaryDto.ClassRow> classRows = classes.entrySet().stream()
                .map(entry -> {
                    String[] label = labels.get(entry.getKey());
                    long[] count = entry.getValue();
                    long total = sum(count);
                    return new AttendanceSummaryDto.ClassRow(label[0], label[1],
                            count[0], count[1], count[2], total, rate(count[0], count[2], total));
                })
                .toList();
        List<AttendanceSummaryDto.DayRow> dayRows = days.entrySet().stream()
                .map(entry -> dayRow(entry.getKey(), entry.getValue()))
                .toList();

        return new AttendanceSummaryDto(range.from(), range.to(), totals[0], totals[1], totals[2],
                sum(totals), rate(totals[0], totals[2], sum(totals)), classRows, dayRows);
    }

    @Transactional(readOnly = true)
    public StudentAttendanceDto myAttendance(LocalDate from, LocalDate to) {
        AppUser me = currentUserService.requireCurrentUser();
        if (me.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("This summary belongs to a student sign-in");
        }
        Long studentId = me.getStudentId();
        if (studentId == null) {
            throw new BadRequestException("This sign-in is not linked to a student profile");
        }
        DateRange range = normalize(from, to);
        List<AttendanceRecord> records = attendanceRepository.findForStudent(studentId, range.from(), range.to());
        Map<Long, Subject> catalog = subjectRepository.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        Map<Long, long[]> bySubject = new HashMap<>();
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        long[] totals = new long[3];
        for (AttendanceRecord record : records) {
            tally(bySubject.computeIfAbsent(record.getSubjectId(), ignored -> new long[3]), record.getStatus());
            tally(byDay.computeIfAbsent(record.getSessionDate(), ignored -> new long[3]), record.getStatus());
            tally(totals, record.getStatus());
        }

        List<StudentAttendanceDto.SubjectRow> subjects = bySubject.entrySet().stream()
                .filter(entry -> catalog.containsKey(entry.getKey()))
                .map(entry -> {
                    Subject subject = catalog.get(entry.getKey());
                    long[] count = entry.getValue();
                    long total = sum(count);
                    return new StudentAttendanceDto.SubjectRow(subject.getId(), subject.getCode(), subject.getName(),
                            count[0], count[1], count[2], total, rate(count[0], count[2], total));
                })
                .sorted(Comparator.comparing(StudentAttendanceDto.SubjectRow::subjectCode,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<StudentAttendanceDto.DayRow> days = byDay.entrySet().stream()
                .map(entry -> {
                    long[] count = entry.getValue();
                    long total = sum(count);
                    return new StudentAttendanceDto.DayRow(entry.getKey(), count[0], count[1], count[2], total,
                            rate(count[0], count[2], total));
                })
                .toList();

        List<StudentAttendanceDto.HistoryRow> history = records.stream()
                .limit(120)
                .map(record -> {
                    Subject subject = catalog.get(record.getSubjectId());
                    return new StudentAttendanceDto.HistoryRow(record.getSessionDate(), record.getSubjectId(),
                            subject == null ? null : subject.getCode(),
                            subject == null ? "Subject " + record.getSubjectId() : subject.getName(),
                            record.getStartTime(), record.getStatus());
                })
                .toList();

        return new StudentAttendanceDto(totals[0], totals[1], totals[2], sum(totals),
                rate(totals[0], totals[2], sum(totals)), subjects, days, history);
    }

    // ---- low attendance ----------------------------------------------------

    /**
     * Students under the warning threshold, lowest rate first. The count of classes they need to
     * attend in a row is the smallest n with (attended + n) / (total + n) at or above the threshold.
     */
    @Transactional(readOnly = true)
    public AtRiskDto atRisk(LocalDate from, LocalDate to, String classLevel, String section) {
        DateRange range = normalize(from, to);
        AppUser me = currentUserService.requireCurrentUser();
        if (me.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("The at-risk list is available to administrators and faculty");
        }

        List<AttendanceRecord> records = scopedRecords(me, range, trimToNull(classLevel), trimToNull(section));
        Map<Long, long[]> totals = new HashMap<>();
        Map<Long, Map<Long, long[]>> bySubject = new HashMap<>();
        for (AttendanceRecord record : records) {
            tally(totals.computeIfAbsent(record.getStudentId(), ignored -> new long[3]), record.getStatus());
            bySubject.computeIfAbsent(record.getStudentId(), ignored -> new HashMap<>())
                    .computeIfAbsent(record.getSubjectId(), ignored -> new long[3]);
            tally(bySubject.get(record.getStudentId()).get(record.getSubjectId()), record.getStatus());
        }

        Map<Long, Student> students = studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<Long, Subject> catalog = subjectRepository.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        List<AtRiskDto.Row> rows = new ArrayList<>();
        totals.forEach((studentId, count) -> {
            Student student = students.get(studentId);
            long total = sum(count);
            double rate = rate(count[0], count[2], total);
            if (student == null || total == 0 || rate >= LOW_RATE) {
                return;
            }
            Subject weakest = null;
            double weakestRate = Double.MAX_VALUE;
            for (Map.Entry<Long, long[]> entry : bySubject.getOrDefault(studentId, Map.of()).entrySet()) {
                long subjectTotal = sum(entry.getValue());
                if (subjectTotal == 0) {
                    continue;
                }
                double subjectRate = rate(entry.getValue()[0], entry.getValue()[2], subjectTotal);
                if (subjectRate < weakestRate) {
                    weakestRate = subjectRate;
                    weakest = catalog.get(entry.getKey());
                }
            }
            long needed = Math.max(0, (long) Math.ceil(3.0 * total - 4.0 * (count[0] + count[2])));
            rows.add(new AtRiskDto.Row(student.getId(), student.getRollNumber(),
                    (student.getFirstName() + " " + student.getLastName()).trim(),
                    photoService.urlForPerson(Role.STUDENT, student.getId()).orElse(null),
                    student.getClassLevel(), student.getSection(), count[0], count[1], count[2], total, rate,
                    needed, weakest == null ? null : weakest.getName(),
                    weakest == null ? null : Math.round(weakestRate * 10.0) / 10.0));
        });
        rows.sort(Comparator.comparingDouble(AtRiskDto.Row::rate).thenComparing(AtRiskDto.Row::rollNumber));
        return new AtRiskDto(range.from(), range.to(), LOW_RATE, rows);
    }

    // ---- helpers -----------------------------------------------------------

    /** Attendance records the caller may see, narrowed to their own classes for faculty. */
    private List<AttendanceRecord> scopedRecords(AppUser me, DateRange range, String classLevel, String section) {
        List<AttendanceRecord> records = attendanceRepository.findInRange(range.from(), range.to(),
                classLevel, section);
        if (me.getRole() != Role.TEACHER) {
            return records;
        }
        Set<String> allowed = scheduleRepository.rowsForTeacher(requireTeacherId(me)).stream()
                .map(row -> sessionKey(row.subjectId(), row.classLevel(), row.section(), row.startTime()))
                .collect(Collectors.toSet());
        return records.stream()
                .filter(record -> allowed.contains(sessionKey(record.getSubjectId(), record.getClassLevel(),
                        record.getSection(), record.getStartTime())))
                .toList();
    }

    private AttendanceSheetDto buildSheet(ClassSchedule schedule, LocalDate date) {
        ScheduleRow row = scheduleRepository.rowsForClass(schedule.getClassLevel(), schedule.getSection()).stream()
                .filter(candidate -> candidate.id().equals(schedule.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No timetable row found for this session"));

        Map<Long, String> statuses = attendanceRepository
                .findByClassLevelAndSectionAndSubjectIdAndSessionDateAndStartTime(schedule.getClassLevel(),
                        schedule.getSection(), schedule.getSubjectId(), date, schedule.getStartTime())
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, AttendanceRecord::getStatus));

        List<AttendanceSheetDto.StudentRow> students = activeRoster(schedule.getClassLevel(), schedule.getSection())
                .stream()
                .map(student -> new AttendanceSheetDto.StudentRow(student.getId(), student.getRollNumber(),
                        (student.getFirstName() + " " + student.getLastName()).trim(),
                        photoService.urlForPerson(Role.STUDENT, student.getId()).orElse(null),
                        statuses.get(student.getId())))
                .toList();

        return new AttendanceSheetDto(row.id(), date, row.classLevel(), row.section(), row.subjectId(),
                row.subjectCode(), row.subjectName(), row.teacherName(), row.startTime(), row.endTime(),
                row.room(), students);
    }

    private Map<String, Integer> markedCounts(LocalDate date) {
        Map<String, Integer> counts = new HashMap<>();
        for (AttendanceRecord record : attendanceRepository.findBySessionDate(date)) {
            counts.merge(sessionKey(record.getSubjectId(), record.getClassLevel(), record.getSection(),
                    record.getStartTime()), 1, Integer::sum);
        }
        return counts;
    }

    private List<Student> activeRoster(String classLevel, String section) {
        return studentRepository.findByClassLevelAndSectionOrderByRollNumberAsc(classLevel, section).stream()
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .toList();
    }

    private ClassSchedule loadSchedule(Long scheduleId) {
        if (scheduleId == null) {
            throw new BadRequestException("Choose a class session");
        }
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("No class session found with id " + scheduleId));
    }

    private void assertCanRecord(ClassSchedule schedule, AppUser me) {
        if (me.getRole() == Role.ADMIN) {
            return;
        }
        if (me.getRole() == Role.TEACHER) {
            if (schedule.getTeacherId().equals(me.getTeacherId())) {
                return;
            }
            throw new AccessDeniedException("You can only record attendance for your own classes");
        }
        throw new AccessDeniedException("Attendance is recorded by administrators and faculty");
    }

    private void assertSessionDay(ClassSchedule schedule, LocalDate date) {
        Integer expected = schedule.getDayOfWeek();
        if (expected != null && date.getDayOfWeek().getValue() == expected) {
            return;
        }
        String meets = expected == null
                ? "has no weekday set"
                : "meets on " + DayOfWeek.of(expected).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s";
        throw new BadRequestException("This session " + meets + ", so it cannot be marked on " + date + ".");
    }

    private Long requireTeacherId(AppUser me) {
        Long teacherId = me.getTeacherId();
        if (teacherId == null) {
            throw new BadRequestException("This sign-in is not linked to a teacher record.");
        }
        return teacherId;
    }

    private String normalizeStatus(String raw) {
        String status = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BadRequestException("Attendance must be PRESENT, ABSENT or LATE");
        }
        return status;
    }

    private DateRange normalize(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("The start date must be on or before the end date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BadRequestException("Choose a range of 366 days or less");
        }
        return new DateRange(start, end);
    }

    /** A late arrival still attended, so it is counted with the presents in the rate. */
    static double rate(long present, long late, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((present + late) * 1000.0 / total) / 10.0;
    }

    static void tally(long[] tally, String status) {
        switch (status) {
            case PRESENT -> tally[0]++;
            case ABSENT -> tally[1]++;
            case LATE -> tally[2]++;
            default -> {
            }
        }
    }

    static long sum(long[] tally) {
        return tally[0] + tally[1] + tally[2];
    }

    private static AttendanceSummaryDto.DayRow dayRow(LocalDate date, long[] count) {
        long total = sum(count);
        return new AttendanceSummaryDto.DayRow(date, count[0], count[1], count[2], total,
                rate(count[0], count[2], total));
    }

    static String pair(String classLevel, String section) {
        return classLevel + '\u0001' + (section == null ? "" : section);
    }

    static String sessionKey(Long subjectId, String classLevel, String section, java.time.LocalTime startTime) {
        return subjectId + "\u0001" + pair(classLevel, section) + "\u0001" + startTime;
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
