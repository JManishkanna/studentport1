package com.academy.portal.dto;

/** A subject a student is enrolled in, with the faculty currently teaching it. */
public record EnrolledSubject(
        Long subjectId,
        String code,
        String name,
        String department,
        Integer credits,
        String teacherNames) {
}
