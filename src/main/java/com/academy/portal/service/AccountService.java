package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service password change for whichever account is signed in. */
@Service
public class AccountService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AccountService(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        AppUser user = currentUserService.requireCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("The current password is not correct");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("The new password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordIsDefault(false);
        userRepository.save(user);
    }
}
