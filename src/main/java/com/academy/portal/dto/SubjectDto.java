package com.academy.portal.dto;

public record SubjectDto(
        Long id,
        String code,
        String name,
        String department,
        Integer credits) {
}
