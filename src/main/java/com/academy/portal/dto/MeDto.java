package com.academy.portal.dto;

import com.academy.portal.domain.AppUser;
import com.academy.portal.domain.Role;

public record MeDto(
        Long id,
        String username,
        String fullName,
        Role role,
        Long studentId,
        Long teacherId) {

    public static MeDto from(AppUser user) {
        return new MeDto(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getStudentId(), user.getTeacherId());
    }
}
