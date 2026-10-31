package com.academy.portal.dto;

import java.time.LocalDate;

public record AssessmentRequest(
        Long subjectId,
        String classLevel,
        String section,
        String title,
        String kind,
        LocalDate dueDate,
        Integer maxMarks,
        String description) {
}
