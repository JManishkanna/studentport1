package com.academy.portal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** The roster of one class session, ready to be marked. A null status means "not marked yet". */
public record AttendanceSheetDto(
        Long scheduleId,
        LocalDate date,
        String classLevel,
        String section,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String teacherName,
        LocalTime startTime,
        LocalTime endTime,
        String room,
        List<StudentRow> students) {

    public record StudentRow(
            Long studentId,
            String rollNumber,
            String studentName,
            String photoUrl,
            String status) {
    }
}
