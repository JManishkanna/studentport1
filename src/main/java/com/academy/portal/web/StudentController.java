package com.academy.portal.web;

import com.academy.portal.dto.AccountLoginDto;
import com.academy.portal.dto.AccountLoginReset;
import com.academy.portal.dto.EnrolledSubject;
import com.academy.portal.dto.LoginResetRequest;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.StudentDto;
import com.academy.portal.service.BadRequestException;
import com.academy.portal.service.CurrentUserService;
import com.academy.portal.service.StudentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final CurrentUserService currentUserService;

    public StudentController(StudentService studentService, CurrentUserService currentUserService) {
        this.studentService = studentService;
        this.currentUserService = currentUserService;
    }

    // ---- directory: administrators and faculty only -------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<StudentDto> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String classLevel,
                                 @RequestParam(required = false) String status) {
        return studentService.search(q, classLevel, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public StudentDto get(@PathVariable Long id) {
        return studentService.get(id);
    }

    @GetMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<ScheduleRow> schedule(@PathVariable Long id) {
        return studentService.schedule(id);
    }

    @GetMapping("/{id}/subjects")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<EnrolledSubject> subjects(@PathVariable Long id) {
        return studentService.enrolledSubjects(id);
    }

    @GetMapping("/{id}/classmates")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<StudentDto> classmates(@PathVariable Long id) {
        return studentService.classmates(id);
    }

    // ---- self service: the signed-in student's own record -------------------

    @GetMapping("/me")
    public StudentDto me() {
        return studentService.get(ownStudentId());
    }

    @GetMapping("/me/schedule")
    public List<ScheduleRow> mySchedule() {
        return studentService.schedule(ownStudentId());
    }

    @GetMapping("/me/subjects")
    public List<EnrolledSubject> mySubjects() {
        return studentService.enrolledSubjects(ownStudentId());
    }

    @GetMapping("/me/classmates")
    public List<StudentDto> myClassmates() {
        return studentService.classmates(ownStudentId());
    }

    // ---- administration ----------------------------------------------------

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDto create(@Valid @RequestBody StudentDto dto) {
        return studentService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentDto update(@PathVariable Long id, @Valid @RequestBody StudentDto dto) {
        return studentService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Map.of("status", "deleted", "id", String.valueOf(id));
    }

    // ---- administration of a student's sign-in -----------------------------

    @GetMapping("/{id}/login")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountLoginDto login(@PathVariable Long id) {
        return studentService.loginInfo(id);
    }

    @PostMapping("/{id}/login/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountLoginReset resetLogin(@PathVariable Long id,
                                        @RequestBody(required = false) LoginResetRequest request) {
        return studentService.resetLogin(id, request == null ? null : request.password());
    }

    @PostMapping("/{id}/subjects/{subjectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> enroll(@PathVariable Long id, @PathVariable Long subjectId) {
        studentService.enroll(id, subjectId);
        return Map.of("status", "enrolled");
    }

    @DeleteMapping("/{id}/subjects/{subjectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> drop(@PathVariable Long id, @PathVariable Long subjectId) {
        studentService.dropEnrollment(id, subjectId);
        return Map.of("status", "dropped");
    }

    @GetMapping("/class-levels")
    public List<String> classLevels() {
        return studentService.classLevels();
    }

    private Long ownStudentId() {
        return currentUserService.ownStudentId()
                .orElseThrow(() -> new BadRequestException(
                        "This sign-in is not linked to a student profile. Use the student directory instead."));
    }
}
