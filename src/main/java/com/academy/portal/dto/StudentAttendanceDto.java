package com.academy.portal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** A student's own attendance, grouped by subject and by day, plus their session history. */
public record StudentAttendanceDto(
        long present,
        long absent,
        long late,
        long total,
        double rate,
        List<SubjectRow> subjects,
        List<DayRow> days,
        List<HistoryRow> history) {

    public record SubjectRow(
            Long subjectId,
            String subjectCode,
            String subjectName,
            long present,
            long absent,
            long late,
            long total,
            double rate) {
    }

    public record DayRow(
            LocalDate date,
            long present,
            long absent,
            long late,
            long total,
            double rate) {
    }

    public record HistoryRow(
            LocalDate date,
            Long subjectId,
            String subjectCode,
            String subjectName,
            LocalTime startTime,
            String status) {
    }
}
