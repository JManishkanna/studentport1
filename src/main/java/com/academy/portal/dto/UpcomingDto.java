package com.academy.portal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record UpcomingDto(LocalDate from, LocalDate to, List<ClassItem> classes, List<AssessmentItem> assessments) {

    public record ClassItem(
            LocalDate date,
            Integer dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String teacherName,
            String classLevel,
            String section,
            String room) {
    }

    /** graded is the student's own marks being in; it is null for staff. */
    public record AssessmentItem(
            Long id,
            String title,
            String kind,
            LocalDate dueDate,
            long daysLeft,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String classLevel,
            String section,
            int maxMarks,
            Boolean graded) {
    }
}
