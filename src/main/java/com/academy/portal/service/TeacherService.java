package com.academy.portal.service;

import com.academy.portal.domain.Role;
import com.academy.portal.domain.Teacher;
import com.academy.portal.dto.AccountLoginDto;
import com.academy.portal.dto.AccountLoginReset;
import com.academy.portal.dto.TeacherDto;
import com.academy.portal.repository.TeacherRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final LinkedAccountService accountService;

    public TeacherService(TeacherRepository teacherRepository, LinkedAccountService accountService) {
        this.teacherRepository = teacherRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<TeacherDto> search(String query, String department) {
        String pattern = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase() + "%";
        String dept = department == null || department.isBlank() ? null : department.trim();
        return teacherRepository.search(pattern, dept).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TeacherDto get(Long id) {
        return toDto(load(id));
    }

    @Transactional(readOnly = true)
    public List<String> departments() {
        return teacherRepository.findDistinctDepartments();
    }

    @Transactional
    public TeacherDto create(TeacherDto dto) {
        if (dto.id() != null) {
            throw new BadRequestException("id must be empty when adding a teacher");
        }
        String code = dto.employeeCode().trim();
        if (teacherRepository.findByEmployeeCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException("Employee code " + code + " is already in use");
        }
        if (emailExists(dto.email())) {
            throw new ConflictException("Email " + dto.email() + " is already registered");
        }
        Teacher teacher = new Teacher();
        apply(teacher, dto);
        Teacher saved = teacherRepository.save(teacher);
        accountService.ensureAccount(Role.TEACHER, saved.getId(), saved.getEmail(), fullName(saved));
        return toDto(saved);
    }

    @Transactional
    public TeacherDto update(Long id, TeacherDto dto) {
        Teacher teacher = load(id);
        String code = dto.employeeCode().trim();
        if (teacherRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Employee code " + code + " is already in use");
        }
        if (teacherRepository.existsByEmailIgnoreCaseAndIdNot(dto.email().trim(), id)) {
            throw new ConflictException("Email " + dto.email() + " is already registered");
        }
        apply(teacher, dto);
        Teacher saved = teacherRepository.save(teacher);
        accountService.ensureAccount(Role.TEACHER, saved.getId(), saved.getEmail(), fullName(saved));
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Teacher teacher = load(id);
        accountService.removeFor(Role.TEACHER, teacher.getId());
        teacherRepository.delete(teacher);
    }

    @Transactional(readOnly = true)
    public AccountLoginDto loginInfo(Long teacherId) {
        Teacher teacher = load(teacherId);
        return accountService.loginInfo(Role.TEACHER, teacherId, teacher.getEmail());
    }

    @Transactional
    public AccountLoginReset resetLogin(Long teacherId, String requested) {
        Teacher teacher = load(teacherId);
        return accountService.reset(Role.TEACHER, teacherId, teacher.getEmail(), fullName(teacher), requested);
    }

    private String fullName(Teacher teacher) {
        return (teacher.getFirstName() + " " + teacher.getLastName()).trim();
    }

    private boolean emailExists(String email) {
        String normalized = email.trim();
        return teacherRepository.findAll().stream()
                .anyMatch(t -> t.getEmail().equalsIgnoreCase(normalized));
    }

    private Teacher load(Long id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No teacher found with id " + id));
    }

    private void apply(Teacher teacher, TeacherDto dto) {
        teacher.setEmployeeCode(dto.employeeCode().trim());
        teacher.setFirstName(dto.firstName().trim());
        teacher.setLastName(dto.lastName().trim());
        teacher.setDesignation(trimOrNull(dto.designation()));
        teacher.setDepartment(dto.department().trim());
        teacher.setEmail(dto.email().trim());
        teacher.setPhone(trimOrNull(dto.phone()));
        teacher.setSpecialization(trimOrNull(dto.specialization()));
        teacher.setQualification(trimOrNull(dto.qualification()));
        teacher.setOfficeRoom(trimOrNull(dto.officeRoom()));
        teacher.setJoiningDate(dto.joiningDate() == null ? LocalDate.now() : dto.joiningDate());
        teacher.setStatus(dto.status() == null || dto.status().isBlank()
                ? "ACTIVE" : dto.status().trim().toUpperCase());
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TeacherDto toDto(Teacher t) {
        return new TeacherDto(t.getId(), t.getEmployeeCode(), t.getFirstName(), t.getLastName(),
                t.getDesignation(), t.getDepartment(), t.getEmail(), t.getPhone(), t.getSpecialization(),
                t.getQualification(), t.getOfficeRoom(), t.getJoiningDate(), t.getStatus());
    }
}
