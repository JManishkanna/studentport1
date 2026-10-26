package com.academy.portal.service;

import com.academy.portal.dto.DashboardDto;
import com.academy.portal.repository.StudentRepository;
import com.academy.portal.repository.SubjectRepository;
import com.academy.portal.repository.TeacherRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final ScheduleService scheduleService;
    private final StudentService studentService;

    public DashboardService(StudentRepository studentRepository,
                            TeacherRepository teacherRepository,
                            SubjectRepository subjectRepository,
                            ScheduleService scheduleService,
                            StudentService studentService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.scheduleService = scheduleService;
        this.studentService = studentService;
    }

    @Transactional(readOnly = true)
    public DashboardDto summary() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        var sessionsToday = scheduleService.find(today.getValue(), null, null, null, null);
        return new DashboardDto(
                studentRepository.count(),
                studentRepository.countByStatus("ACTIVE"),
                teacherRepository.count(),
                subjectRepository.count(),
                sessionsToday.size(),
                sessionsToday,
                studentService.classLevels());
    }
}
