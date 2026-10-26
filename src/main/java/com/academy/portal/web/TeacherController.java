package com.academy.portal.web;

import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.dto.TeacherDto;
import com.academy.portal.service.BadRequestException;
import com.academy.portal.service.CurrentUserService;
import com.academy.portal.service.ScheduleService;
import com.academy.portal.service.TeacherService;
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
@RequestMapping("/api/teachers")
public class TeacherController {

    private final TeacherService teacherService;
    private final ScheduleService scheduleService;
    private final CurrentUserService currentUserService;

    public TeacherController(TeacherService teacherService,
                             ScheduleService scheduleService,
                             CurrentUserService currentUserService) {
        this.teacherService = teacherService;
        this.scheduleService = scheduleService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<TeacherDto> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String department) {
        return teacherService.search(q, department);
    }

    @GetMapping("/departments")
    public List<String> departments() {
        return teacherService.departments();
    }

    @GetMapping("/{id}")
    public TeacherDto get(@PathVariable Long id) {
        return teacherService.get(id);
    }

    @GetMapping("/{id}/schedule")
    public List<ScheduleRow> schedule(@PathVariable Long id) {
        return scheduleService.forTeacher(id);
    }

    /** Faculty can always reach their own record and timetable, whatever else they can browse. */
    @GetMapping("/me")
    public TeacherDto me() {
        return teacherService.get(ownTeacherId());
    }

    @GetMapping("/me/schedule")
    public List<ScheduleRow> mySchedule() {
        return scheduleService.forTeacher(ownTeacherId());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TeacherDto create(@Valid @RequestBody TeacherDto dto) {
        return teacherService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TeacherDto update(@PathVariable Long id, @Valid @RequestBody TeacherDto dto) {
        return teacherService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return Map.of("status", "deleted", "id", String.valueOf(id));
    }

    private Long ownTeacherId() {
        Long teacherId = currentUserService.requireCurrentUser().getTeacherId();
        if (teacherId == null) {
            throw new BadRequestException("This sign-in is not linked to a teacher record.");
        }
        return teacherId;
    }
}
