package com.academy.portal.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance across every class the caller may see. A late arrival counts as attended, so
 * {@code rate} is (present + late) / total rather than present / total.
 */
public record AttendanceSummaryDto(
        LocalDate from,
        LocalDate to,
        long present,
        long absent,
        long late,
        long total,
        double rate,
        List<ClassRow> classes,
        List<DayRow> days) {

    public record ClassRow(
            String classLevel,
            String section,
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
}
