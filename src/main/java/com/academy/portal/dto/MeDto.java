package com.academy.portal.dto;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;

public record MeDto(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        Role role,
        Long studentId,
        Long teacherId,
        String photoUrl,
        boolean passwordIsDefault) {

    public static MeDto from(AppUser user, String photoUrl) {
        return new MeDto(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole(), user.getStudentId(), user.getTeacherId(), photoUrl,
                user.isPasswordIsDefault());
    }
}
