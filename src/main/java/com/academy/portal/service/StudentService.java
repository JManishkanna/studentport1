package com.academy.portal.service;

import com.academy.portal.domain.Enrollment;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Subject;
import com.academy.portal.dto.EnrolledSubject;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.StudentDto;
import com.academy.portal.repository.ClassScheduleRepository;
import com.academy.portal.repository.EnrollmentRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final StudentAccountService studentAccountService;

    public StudentService(StudentRepository studentRepository,
                          EnrollmentRepository enrollmentRepository,
                          SubjectRepository subjectRepository,
                          ClassScheduleRepository scheduleRepository,
                          StudentAccountService studentAccountService) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.scheduleRepository = scheduleRepository;
        this.studentAccountService = studentAccountService;
    }

    @Transactional(readOnly = true)
    public List<StudentDto> search(String query, String classLevel, String status) {
        String pattern = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase() + "%";
        String level = classLevel == null || classLevel.isBlank() ? null : classLevel.trim();
        String statusCode = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return studentRepository.search(pattern, level, statusCode).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentDto get(Long id) {
        return toDto(load(id));
    }

    @Transactional(readOnly = true)
    public List<String> classLevels() {
        return studentRepository.findDistinctClassLevels();
    }

    @Transactional
    public StudentDto create(StudentDto dto) {
        if (dto.id() != null) {
            throw new BadRequestException("id must be empty when admitting a student");
        }
        String roll = dto.rollNumber().trim();
        if (studentRepository.findByRollNumberIgnoreCase(roll).isPresent()) {
            throw new ConflictException("Roll number " + roll + " is already admitted");
        }
        if (emailExists(dto.email())) {
            throw new ConflictException("Email " + dto.email() + " is already registered");
        }
        Student student = new Student();
        apply(student, dto);
        Student saved = studentRepository.save(student);
        studentAccountService.ensureAccount(saved);
        return toDto(saved);
    }

    @Transactional
    public StudentDto update(Long id, StudentDto dto) {
        Student student = load(id);
        String roll = dto.rollNumber().trim();
        if (studentRepository.existsByRollNumberIgnoreCaseAndIdNot(roll, id)) {
            throw new ConflictException("Roll number " + roll + " is already admitted");
        }
        if (studentRepository.existsByEmailIgnoreCaseAndIdNot(dto.email().trim(), id)) {
            throw new ConflictException("Email " + dto.email() + " is already registered");
        }
        apply(student, dto);
        Student saved = studentRepository.save(student);
        studentAccountService.ensureAccount(saved);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Student student = load(id);
        studentAccountService.removeForStudent(student.getId());
        enrollmentRepository.findByStudentIdOrderByIdAsc(student.getId())
                .forEach(enrollmentRepository::delete);
        studentRepository.delete(student);
    }

    @Transactional(readOnly = true)
    public List<ScheduleRow> schedule(Long studentId) {
        Student student = load(studentId);
        if (student.getClassLevel() == null || student.getSection() == null) {
            return List.of();
        }
        return scheduleRepository.rowsForClass(student.getClassLevel(), student.getSection());
    }

    /** Subjects the student is enrolled in, with whoever currently teaches them in that class. */
    @Transactional(readOnly = true)
    public List<EnrolledSubject> enrolledSubjects(Long studentId) {
        Student student = load(studentId);
        Map<Long, String> teacherBySubject = new LinkedHashMap<>();
        for (ScheduleRow row : schedule(studentId)) {
            teacherBySubject.merge(row.subjectId(), row.teacherName(),
                    (existing, added) -> existing.contains(added) ? existing : existing + ", " + added);
        }

        Map<Long, Subject> catalog = new LinkedHashMap<>();
        subjectRepository.findAllByOrderByCodeAsc().forEach(s -> catalog.put(s.getId(), s));

        return enrollmentRepository.findByStudentIdOrderByIdAsc(studentId).stream()
                .map(Enrollment::getSubjectId)
                .filter(catalog::containsKey)
                .distinct()
                .map(subjectId -> {
                    Subject subject = catalog.get(subjectId);
                    return new EnrolledSubject(subject.getId(), subject.getCode(), subject.getName(),
                            subject.getDepartment(), subject.getCredits(), teacherBySubject.get(subjectId));
                })
                .toList();
    }

    @Transactional
    public void enroll(Long studentId, Long subjectId) {
        Student student = load(studentId);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new NotFoundException("No subject found with id " + subjectId));
        if (enrollmentRepository.existsByStudentIdAndSubjectId(student.getId(), subject.getId())) {
            throw new ConflictException("Already enrolled in " + subject.getName());
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(student.getId());
        enrollment.setSubjectId(subject.getId());
        enrollment.setEnrolledAt(LocalDate.now());
        enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void dropEnrollment(Long studentId, Long subjectId) {
        load(studentId);
        if (!enrollmentRepository.existsByStudentIdAndSubjectId(studentId, subjectId)) {
            throw new NotFoundException("No enrollment found for student " + studentId + " in subject " + subjectId);
        }
        enrollmentRepository.deleteByStudentIdAndSubjectId(studentId, subjectId);
    }

    @Transactional(readOnly = true)
    public List<Long> enrolledSubjectIds(Long studentId) {
        load(studentId);
        return enrollmentRepository.findByStudentIdOrderByIdAsc(studentId).stream()
                .map(Enrollment::getSubjectId)
                .toList();
    }

    private Student load(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No student found with id " + id));
    }

    private boolean emailExists(String email) {
        String normalized = email.trim();
        return studentRepository.search(null, null, null).stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(normalized));
    }

    /** Convenience for the profile page: everyone in the same class and section. */
    @Transactional(readOnly = true)
    public List<StudentDto> classmates(Long studentId) {
        Student student = load(studentId);
        return studentRepository.search(null, student.getClassLevel(), null).stream()
                .filter(s -> student.getSection() == null || student.getSection().equals(s.getSection()))
                .filter(s -> !s.getId().equals(studentId))
                .map(this::toDto)
                .toList();
    }

    private void apply(Student student, StudentDto dto) {
        student.setRollNumber(dto.rollNumber().trim());
        student.setFirstName(dto.firstName().trim());
        student.setLastName(dto.lastName().trim());
        student.setEmail(dto.email().trim());
        student.setPhone(trimOrNull(dto.phone()));
        student.setDateOfBirth(dto.dateOfBirth());
        student.setGender(trimOrNull(dto.gender()));
        student.setClassLevel(dto.classLevel().trim());
        student.setSection(trimOrNull(dto.section()));
        student.setAddressLine(trimOrNull(dto.addressLine()));
        student.setCity(trimOrNull(dto.city()));
        student.setGuardianName(trimOrNull(dto.guardianName()));
        student.setGuardianPhone(trimOrNull(dto.guardianPhone()));
        student.setGuardianEmail(trimOrNull(dto.guardianEmail()));
        student.setAdmissionDate(dto.admissionDate() == null ? LocalDate.now() : dto.admissionDate());
        student.setStatus(dto.status() == null || dto.status().isBlank() ? "ACTIVE" : dto.status().trim().toUpperCase());
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private StudentDto toDto(Student s) {
        return new StudentDto(s.getId(), s.getRollNumber(), s.getFirstName(), s.getLastName(), s.getEmail(),
                s.getPhone(), s.getDateOfBirth(), s.getGender(), s.getClassLevel(), s.getSection(),
                s.getAddressLine(), s.getCity(), s.getGuardianName(), s.getGuardianPhone(), s.getGuardianEmail(),
                s.getAdmissionDate(), s.getStatus());
    }
}
