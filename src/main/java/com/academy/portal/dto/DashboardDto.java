package com.academy.portal.dto;

import java.util.List;

public record DashboardDto(
        long totalStudents,
        long activeStudents,
        long totalTeachers,
        long totalSubjects,
        long sessionsToday,
        List<ScheduleRow> todaySessions,
        List<String> classLevels) {
}
