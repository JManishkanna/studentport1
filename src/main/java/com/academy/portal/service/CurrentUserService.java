package com.academy.portal.service;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;
import com.academy.portal.dto.MeDto;
import com.academy.portal.repository.AppUserRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Resolves the signed-in user, so page-level scoping is driven by the session, not by the client. */
@Service
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    public AppUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new NotFoundException("Signed-in user no longer exists"));
    }

    public MeDto me() {
        return MeDto.from(requireCurrentUser());
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    private boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role.name()).equals(a.getAuthority()));
    }

    /** Returns the student id the caller is allowed to read as their own profile. */
    public Optional<Long> ownStudentId() {
        return currentUser().map(AppUser::getStudentId);
    }
}
