package com.academy.portal.web;

import com.academy.portal.dto.ScheduleDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.WeekPlanRequest;
import com.academy.portal.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** Full timetable grid. Students use /api/students/me/schedule for their own class. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<ScheduleRow> list(@RequestParam(required = false) Integer dayOfWeek,
                                  @RequestParam(required = false) String classLevel,
                                  @RequestParam(required = false) String section,
                                  @RequestParam(required = false) Long teacherId,
                                  @RequestParam(required = false) Long subjectId) {
        return scheduleService.find(dayOfWeek, classLevel, section, teacherId, subjectId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ScheduleDto get(@PathVariable Long id) {
        return scheduleService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto create(@Valid @RequestBody ScheduleDto dto) {
        return scheduleService.create(dto);
    }

    /** Lays an ordered list of periods across the chosen weekdays, times included. */
    @PostMapping("/week")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ScheduleDto> planWeek(@Valid @RequestBody WeekPlanRequest request) {
        return scheduleService.planWeek(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto update(@PathVariable Long id, @Valid @RequestBody ScheduleDto dto) {
        return scheduleService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Map.of("status", "deleted", "id", String.valueOf(id));
    }
}
