package com.academy.portal.dto;

import java.time.LocalDateTime;

public record CommentDto(
        Long id,
        Long studentId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String authorName,
        String authorRole,
        String body,
        LocalDateTime createdAt,
        boolean canDelete) {
}
