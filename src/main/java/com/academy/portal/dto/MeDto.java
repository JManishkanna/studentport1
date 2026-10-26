package com.academy.portal.dto;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;

public record MeDto(
        Long id,
        String username,
        String fullName,
        Role role,
        Long studentId,
        Long teacherId,
        String photoUrl,
        boolean passwordIsDefault) {

    public static MeDto from(AppUser user, String photoUrl) {
        return new MeDto(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getStudentId(), user.getTeacherId(), photoUrl, user.isPasswordIsDefault());
    }
}
