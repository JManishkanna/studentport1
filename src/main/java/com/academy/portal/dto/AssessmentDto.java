package com.academy.portal.dto;

import java.time.LocalDate;

/**
 * One assessment as the caller may see it. Staff get the marking progress; a student gets
 * their own marks instead, so the counts are null for them and the marks are null for staff.
 */
public record AssessmentDto(
        Long id,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String teacherName,
        String classLevel,
        String section,
        String title,
        String kind,
        LocalDate dueDate,
        int maxMarks,
        String description,
        Integer gradedCount,
        Integer rosterCount,
        Double averagePercent,
        Double myMarks,
        Double myPercent,
        String myRemark,
        boolean canEdit) {
}
