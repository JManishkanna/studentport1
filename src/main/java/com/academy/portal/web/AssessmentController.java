package com.academy.portal.web;

import com.academy.portal.dto.AssessmentDto;
import com.academy.portal.dto.AssessmentRequest;
import com.academy.portal.dto.GradeEntriesRequest;
import com.academy.portal.dto.GradeSheetDto;
import com.academy.portal.service.AssessmentService;
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
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping
    public List<AssessmentDto> list(@RequestParam(required = false) String classLevel,
                                    @RequestParam(required = false) String section,
                                    @RequestParam(required = false) Long subjectId) {
        return assessmentService.list(classLevel, section, subjectId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AssessmentDto create(@RequestBody AssessmentRequest request) {
        return assessmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public AssessmentDto update(@PathVariable Long id, @RequestBody AssessmentRequest request) {
        return assessmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Map<String, String> delete(@PathVariable Long id) {
        assessmentService.delete(id);
        return Map.of("status", "deleted", "id", String.valueOf(id));
    }

    @GetMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public GradeSheetDto sheet(@PathVariable Long id) {
        return assessmentService.sheet(id);
    }

    @PutMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public GradeSheetDto saveGrades(@PathVariable Long id, @RequestBody GradeEntriesRequest request) {
        return assessmentService.saveGrades(id, request);
    }
}
