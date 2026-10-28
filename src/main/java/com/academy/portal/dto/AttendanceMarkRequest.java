package com.academy.portal.dto;

import java.time.LocalDate;
import java.util.List;

public record AttendanceMarkRequest(Long scheduleId, LocalDate date, List<Mark> entries) {

    public record Mark(Long studentId, String status) {
    }
}
