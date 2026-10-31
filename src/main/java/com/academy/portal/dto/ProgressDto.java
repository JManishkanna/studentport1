package com.academy.portal.dto;

import java.time.LocalDate;
import java.util.List;

/** A student's marks per subject over time, with attendance for the last 30 days alongside. */
public record ProgressDto(
        Long studentId,
        String studentName,
        String rollNumber,
        String classLevel,
        String section,
        Double overallPercent,
        double attendanceRate,
        long attendanceTotal,
        double threshold,
        boolean lowAttendance,
        List<SubjectProgress> subjects) {

    public record SubjectProgress(
            Long subjectId,
            String subjectCode,
            String subjectName,
            String teacherName,
            Double percent,
            int graded,
            int pending,
            Double latestPercent,
            Double change,
            double attendanceRate,
            long attendanceTotal,
            boolean lowAttendance,
            List<Point> points) {
    }

    public record Point(
            Long assessmentId,
            String title,
            String kind,
            LocalDate dueDate,
            int maxMarks,
            Double marks,
            Double percent,
            String remark) {
    }
}
