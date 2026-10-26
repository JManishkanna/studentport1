package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.dto.StudentLoginDto;
import com.academy.portal.dto.StudentLoginReset;
import com.academy.portal.repository.AppUserRepository;
import com.academy.portal.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the sign-in that belongs to each student: the academy email is the username and everyone
 * starts on the shared default password, which the student can replace on the account page.
 */
@Service
public class StudentAccountService {

    private static final Logger log = LoggerFactory.getLogger(StudentAccountService.class);
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private final AppUserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;
    private final String legacyUsername;

    public StudentAccountService(AppUserRepository userRepository,
                                 StudentRepository studentRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${portal.student.default-password:PASSWORD}") String defaultPassword,
                                 @Value("${portal.seed.student.username:student}") String legacyUsername) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
        this.legacyUsername = legacyUsername;
    }

    /**
     * Creates the account on admission and keeps an existing one in step with the student record.
     * The old single demo login is adopted: the email becomes the username and the password goes
     * back to the documented default, so the sign-in shown on the login page is accurate.
     */
    @Transactional
    public AppUser ensureAccount(Student student) {
        AppUser user = userRepository.findFirstByStudentId(student.getId()).orElse(null);
        if (user == null) {
            return create(student);
        }
        boolean renamed = !user.getUsername().equalsIgnoreCase(student.getEmail());
        if (renamed && user.getUsername().equalsIgnoreCase(legacyUsername)) {
            user.setPasswordHash(passwordEncoder.encode(defaultPassword));
            user.setPasswordIsDefault(true);
        }
        if (renamed) {
            user.setUsername(student.getEmail().trim());
        }
        user.setFullName(fullName(student));
        user.setEmail(student.getEmail().trim());
        AppUser saved = userRepository.saveAndFlush(user);
        if (renamed) {
            log.info("Student {} now signs in as {}", student.getRollNumber(), saved.getUsername());
        }
        return saved;
    }

    @Transactional
    public void removeForStudent(Long studentId) {
        userRepository.findFirstByStudentId(studentId).ifPresent(userRepository::delete);
    }

    @Transactional(readOnly = true)
    public StudentLoginDto loginInfo(Long studentId) {
        Student student = loadStudent(studentId);
        AppUser user = userRepository.findFirstByStudentId(studentId).orElse(null);
        return new StudentLoginDto(
                user == null ? student.getEmail() : user.getUsername(),
                user != null && user.isPasswordIsDefault(),
                defaultPassword);
    }

    /** Restores the default password, or sets the one the administrator typed, and returns it once. */
    @Transactional
    public StudentLoginReset reset(Long studentId, String requested) {
        Student student = loadStudent(studentId);
        String password = requested == null || requested.isBlank() ? defaultPassword : requested.trim();
        if (password.length() < MIN_LENGTH) {
            throw new BadRequestException("The new password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BadRequestException("The new password must be at most " + MAX_LENGTH + " characters");
        }

        AppUser user = userRepository.findFirstByStudentId(studentId).orElseGet(() -> {
            AppUser fresh = new AppUser();
            fresh.setUsername(student.getEmail().trim());
            fresh.setFullName(fullName(student));
            fresh.setEmail(student.getEmail().trim());
            fresh.setRole(Role.STUDENT);
            fresh.setStudentId(student.getId());
            fresh.setEnabled(true);
            return fresh;
        });
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordIsDefault(password.equals(defaultPassword));
        AppUser saved = userRepository.saveAndFlush(user);
        return new StudentLoginReset(saved.getUsername(), password, saved.isPasswordIsDefault());
    }

    private AppUser create(Student student) {
        AppUser user = new AppUser();
        user.setUsername(student.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setPasswordIsDefault(true);
        user.setFullName(fullName(student));
        user.setEmail(student.getEmail().trim());
        user.setRole(Role.STUDENT);
        user.setStudentId(student.getId());
        user.setEnabled(true);
        AppUser saved = userRepository.saveAndFlush(user);
        log.info("Opened a sign-in for {} on the academy default password", saved.getUsername());
        return saved;
    }

    private Student loadStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("No student found with id " + studentId));
    }

    private String fullName(Student student) {
        return (student.getFirstName() + " " + student.getLastName()).trim();
    }
}
