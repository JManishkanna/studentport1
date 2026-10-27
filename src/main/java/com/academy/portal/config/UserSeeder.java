package com.academy.portal.config;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.domain.Student;
import com.academy.portal.domain.Teacher;
import com.academy.portal.repository.AppUserRepository;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.TeacherRepository;
import com.academy.portal.service.LinkedAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the administration demo account, then makes sure every student and teacher has a
 * sign-in on their academy email. Passwords come from application.properties and are stored as
 * BCrypt hashes; the demo rows in db/data.sql do not carry credentials.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final AppUserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final LinkedAccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @Value("${portal.seed.users.enabled:true}")
    private boolean enabled;

    @Value("${portal.seed.admin.username:admin}")
    private String adminUsername;

    @Value("${portal.seed.admin.password:admin123}")
    private String adminPassword;

    @Value("${portal.seed.admin.phone:+91 80 4000 1010}")
    private String adminPhone;

    public UserSeeder(AppUserRepository userRepository,
                      StudentRepository studentRepository,
                      TeacherRepository teacherRepository,
                      LinkedAccountService accountService,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Demo account seeding is disabled (portal.seed.users.enabled=false)");
            return;
        }

        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            AppUser admin = new AppUser();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setPasswordIsDefault(true);
            admin.setFullName("Portal Administrator");
            admin.setEmail("admin@academy.edu");
            admin.setPhone(adminPhone);
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("Seeded ADMIN account '{}' with the configured password", adminUsername);
        }

        for (Teacher teacher : teacherRepository.findAll()) {
            accountService.ensureAccount(Role.TEACHER, teacher.getId(), teacher.getEmail(), name(teacher));
        }
        for (Student student : studentRepository.findAll()) {
            accountService.ensureAccount(Role.STUDENT, student.getId(), student.getEmail(), name(student));
        }
        log.info("Student and teacher sign-ins are their academy email, on the default password until changed");
    }

    private String name(Teacher teacher) {
        return (teacher.getFirstName() + " " + teacher.getLastName()).trim();
    }

    private String name(Student student) {
        return (student.getFirstName() + " " + student.getLastName()).trim();
    }
}
