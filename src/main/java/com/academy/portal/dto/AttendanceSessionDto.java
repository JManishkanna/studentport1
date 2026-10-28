package com.academy.portal.dto;

import java.time.LocalTime;

/** One timetable session on a chosen date, with how much of its roster has been marked. */
public record AttendanceSessionDto(
        Long scheduleId,
        String classLevel,
        String section,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String teacherName,
        LocalTime startTime,
        LocalTime endTime,
        String room,
        int studentCount,
        int markedCount,
        boolean complete) {
}
