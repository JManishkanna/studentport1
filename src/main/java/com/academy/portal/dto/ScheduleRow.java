package com.academy.portal.dto;

import java.time.LocalTime;

/** A schedule row already joined with its subject and teacher. */
public record ScheduleRow(
        Long id,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long teacherId,
        String teacherName,
        String classLevel,
        String section,
        String room,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String term) {
}
