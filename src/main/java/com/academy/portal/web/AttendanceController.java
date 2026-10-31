package com.academy.portal.web;

import com.academy.portal.dto.AttendanceMarkRequest;
import com.academy.portal.dto.AttendanceSessionDto;
import com.academy.portal.dto.AttendanceSheetDto;
import com.academy.portal.dto.AtRiskDto;
import com.academy.portal.dto.AttendanceSummaryDto;
import com.academy.portal.dto.StudentAttendanceDto;
import com.academy.portal.service.AttendanceService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<AttendanceSessionDto> sessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String classLevel,
            @RequestParam(required = false) String section) {
        return attendanceService.sessions(date, classLevel, section);
    }

    @GetMapping("/sheet")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSheetDto sheet(
            @RequestParam Long scheduleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.sheet(scheduleId, date);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSheetDto mark(@RequestBody AttendanceMarkRequest request) {
        return attendanceService.mark(request);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AttendanceSummaryDto summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String classLevel,
            @RequestParam(required = false) String section) {
        return attendanceService.summary(from, to, classLevel, section);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentAttendanceDto mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendanceService.myAttendance(from, to);
    }

    @GetMapping("/at-risk")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AtRiskDto atRisk(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String classLevel,
            @RequestParam(required = false) String section) {
        return attendanceService.atRisk(from, to, classLevel, section);
    }
}
