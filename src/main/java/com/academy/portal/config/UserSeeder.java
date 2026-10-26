package com.academy.portal.config;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.repository.AppUserRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.TeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the three demo sign-in accounts. Passwords come from application.properties and are
 * stored as BCrypt hashes; the demo rows in db/data.sql do not carry credentials.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final AppUserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${portal.seed.users.enabled:true}")
    private boolean enabled;

    @Value("${portal.seed.admin.username:admin}")
    private String adminUsername;

    @Value("${portal.seed.admin.password:admin123}")
    private String adminPassword;

    @Value("${portal.seed.teacher.username:teacher}")
    private String teacherUsername;

    @Value("${portal.seed.teacher.password:teacher123}")
    private String teacherPassword;

    @Value("${portal.seed.student.username:student}")
    private String studentUsername;

    @Value("${portal.seed.student.password:student123}")
    private String studentPassword;

    public UserSeeder(AppUserRepository userRepository,
                      StudentRepository studentRepository,
                      TeacherRepository teacherRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Demo account seeding is disabled (portal.seed.users.enabled=false)");
            return;
        }

        AppUser admin = createIfMissing(adminUsername, adminPassword, "Portal Administrator",
                "admin@academy.edu", Role.ADMIN, null, null);
        if (admin != null) {
            log.info("Seeded ADMIN account '{}' with the configured password", adminUsername);
        }

        teacherRepository.findByEmployeeCodeIgnoreCase("ACAD-T-001").ifPresent(teacher -> {
            AppUser created = createIfMissing(teacherUsername, teacherPassword,
                    teacher.getFirstName() + " " + teacher.getLastName(), teacher.getEmail(),
                    Role.TEACHER, null, teacher.getId());
            if (created != null) {
                log.info("Seeded TEACHER account '{}' for {}", teacherUsername, teacher.getEmail());
            }
        });

        studentRepository.findByRollNumberIgnoreCase("ACAD-1101").ifPresent(student -> {
            AppUser created = createIfMissing(studentUsername, studentPassword,
                    student.getFirstName() + " " + student.getLastName(), student.getEmail(),
                    Role.STUDENT, student.getId(), null);
            if (created != null) {
                log.info("Seeded STUDENT account '{}' for {}", studentUsername, student.getEmail());
            }
        });
    }

    private AppUser createIfMissing(String username, String password, String fullName, String email,
                                    Role role, Long studentId, Long teacherId) {
        if (userRepository.findByUsername(username).isPresent()) {
            return null;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setStudentId(studentId);
        user.setTeacherId(teacherId);
        user.setEnabled(true);
        return userRepository.save(user);
    }
}
