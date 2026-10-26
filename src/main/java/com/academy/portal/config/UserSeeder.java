package com.academy.portal.config;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.repository.AppUserRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.TeacherRepository;
import com.academy.portal.service.StudentAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the administration and faculty demo accounts, then makes sure every student has a
 * sign-in on their academy email. Passwords come from application.properties and are stored as
 * BCrypt hashes; the demo rows in db/data.sql do not carry credentials.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final AppUserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccountService studentAccountService;
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

    public UserSeeder(AppUserRepository userRepository,
                      StudentRepository studentRepository,
                      TeacherRepository teacherRepository,
                      StudentAccountService studentAccountService,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.studentAccountService = studentAccountService;
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

        for (Student student : studentRepository.findAll()) {
            studentAccountService.ensureAccount(student);
        }
        log.info("Student sign-ins are their academy email, on the configured default password until changed");
    }

    private AppUser createIfMissing(String username, String password, String fullName, String email,
                                    Role role, Long studentId, Long teacherId) {
        if (userRepository.findByUsername(username).isPresent()) {
            return null;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordIsDefault(true);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setStudentId(studentId);
        user.setTeacherId(teacherId);
        user.setEnabled(true);
        return userRepository.save(user);
    }
}
