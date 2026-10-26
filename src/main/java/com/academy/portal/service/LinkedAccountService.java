package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.dto.AccountLoginDto;
import com.academy.portal.dto.AccountLoginReset;
import com.academy.portal.repository.AppUserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the sign-in that belongs to a student or teacher record: the academy email is the username
 * and everyone starts on the shared default password, which they can replace on the account page.
 */
@Service
public class LinkedAccountService {

    private static final Logger log = LoggerFactory.getLogger(LinkedAccountService.class);
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;
    private final String legacyStudentUsername;
    private final String legacyTeacherUsername;

    public LinkedAccountService(AppUserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${portal.default-password:PASSWORD}") String defaultPassword,
                                @Value("${portal.seed.student.username:student}") String legacyStudentUsername,
                                @Value("${portal.seed.teacher.username:teacher}") String legacyTeacherUsername) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
        this.legacyStudentUsername = legacyStudentUsername;
        this.legacyTeacherUsername = legacyTeacherUsername;
    }

    /**
     * Creates the account on admission and keeps an existing one in step with the record. The old
     * demo logins are adopted: the email becomes the username and the password goes back to the
     * documented default, so the sign-in shown on the login page is accurate.
     */
    @Transactional
    public AppUser ensureAccount(Role role, Long linkedId, String email, String fullName) {
        AppUser user = find(role, linkedId).orElse(null);
        if (user == null) {
            return create(role, linkedId, email, fullName);
        }
        boolean renamed = !user.getUsername().equalsIgnoreCase(email);
        if (renamed && user.getUsername().equalsIgnoreCase(legacyUsername(role))) {
            user.setPasswordHash(passwordEncoder.encode(defaultPassword));
            user.setPasswordIsDefault(true);
        }
        if (renamed) {
            user.setUsername(email.trim());
        }
        user.setFullName(fullName);
        user.setEmail(email.trim());
        AppUser saved = userRepository.saveAndFlush(user);
        if (renamed) {
            log.info("{} {} now signs in as {}", role, linkedId, saved.getUsername());
        }
        return saved;
    }

    @Transactional
    public void removeFor(Role role, Long linkedId) {
        find(role, linkedId).ifPresent(userRepository::delete);
    }

    @Transactional(readOnly = true)
    public AccountLoginDto loginInfo(Role role, Long linkedId, String fallbackEmail) {
        AppUser user = find(role, linkedId).orElse(null);
        return new AccountLoginDto(
                user == null ? fallbackEmail : user.getUsername(),
                user != null && user.isPasswordIsDefault(),
                defaultPassword);
    }

    /** Restores the default password, or sets the one the administrator typed, and returns it once. */
    @Transactional
    public AccountLoginReset reset(Role role, Long linkedId, String email, String fullName, String requested) {
        String password = requested == null || requested.isBlank() ? defaultPassword : requested.trim();
        if (password.length() < MIN_LENGTH) {
            throw new BadRequestException("The new password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BadRequestException("The new password must be at most " + MAX_LENGTH + " characters");
        }

        AppUser user = find(role, linkedId).orElseGet(() -> {
            AppUser fresh = new AppUser();
            fresh.setUsername(email.trim());
            fresh.setEmail(email.trim());
            link(fresh, role, linkedId);
            return fresh;
        });
        user.setFullName(fullName);
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordIsDefault(password.equals(defaultPassword));
        AppUser saved = userRepository.saveAndFlush(user);
        return new AccountLoginReset(saved.getUsername(), password, saved.isPasswordIsDefault());
    }

    private AppUser create(Role role, Long linkedId, String email, String fullName) {
        AppUser user = new AppUser();
        user.setUsername(email.trim());
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setPasswordIsDefault(true);
        user.setFullName(fullName);
        user.setEmail(email.trim());
        user.setEnabled(true);
        link(user, role, linkedId);
        AppUser saved = userRepository.saveAndFlush(user);
        log.info("Opened a {} sign-in for {} on the academy default password", role, saved.getUsername());
        return saved;
    }

    private Optional<AppUser> find(Role role, Long linkedId) {
        return role == Role.STUDENT
                ? userRepository.findFirstByStudentId(linkedId)
                : userRepository.findFirstByTeacherId(linkedId);
    }

    private void link(AppUser user, Role role, Long linkedId) {
        user.setRole(role);
        if (role == Role.STUDENT) {
            user.setStudentId(linkedId);
        } else {
            user.setTeacherId(linkedId);
        }
    }

    private String legacyUsername(Role role) {
        return role == Role.STUDENT ? legacyStudentUsername : legacyTeacherUsername;
    }
}
