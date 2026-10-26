package com.academy.portal.web;

import com.academy.portal.dto.DashboardDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.SubjectDto;
import com.academy.portal.repository.SubjectRepository;
import com.academy.portal.service.DashboardService;
import com.academy.portal.service.ScheduleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReferenceDataController {

    private final DashboardService dashboardService;
    private final ScheduleService scheduleService;
    private final SubjectRepository subjectRepository;

    public ReferenceDataController(DashboardService dashboardService,
                                   ScheduleService scheduleService,
                                   SubjectRepository subjectRepository) {
        this.dashboardService = dashboardService;
        this.scheduleService = scheduleService;
        this.subjectRepository = subjectRepository;
    }

    @GetMapping("/api/dashboard")
    public DashboardDto dashboard() {
        return dashboardService.summary();
    }

    @GetMapping("/api/subjects")
    public List<SubjectDto> subjects() {
        return subjectRepository.findAllByOrderByCodeAsc().stream()
                .map(s -> new SubjectDto(s.getId(), s.getCode(), s.getName(), s.getDepartment(), s.getCredits()))
                .toList();
    }

    /** Students may not browse the academy-wide timetable, but they can resolve one class's slots. */
    @GetMapping("/api/schedules/class")
    public List<ScheduleRow> forClass(@RequestParam String classLevel, @RequestParam String section) {
        return scheduleService.find(null, classLevel, section, null, null);
    }
}
