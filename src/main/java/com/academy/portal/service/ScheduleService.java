package com.academy.portal.service;

import com.academy.portal.domain.ClassSchedule;
import com.academy.portal.dto.ScheduleDto;
import com.academy.portal.dto.ScheduleRow;
import com.academy.portal.repository.ClassScheduleRepository;
import com.academy.portal.repository.SubjectRepository;
import com.academy.portal.repository.TeacherRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private final ClassScheduleRepository scheduleRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;

    public ScheduleService(ClassScheduleRepository scheduleRepository,
                           SubjectRepository subjectRepository,
                           TeacherRepository teacherRepository) {
        this.scheduleRepository = scheduleRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
    }

    public List<ScheduleRow> find(Integer dayOfWeek, String classLevel, String section,
                                  Long teacherId, Long subjectId) {
        return scheduleRepository.allRows().stream()
                .filter(r -> dayOfWeek == null || Objects.equals(r.dayOfWeek(), dayOfWeek))
                .filter(r -> classLevel == null || classLevel.isBlank() || classLevel.equals(r.classLevel()))
                .filter(r -> section == null || section.isBlank() || section.equalsIgnoreCase(r.section()))
                .filter(r -> teacherId == null || teacherId.equals(r.teacherId()))
                .filter(r -> subjectId == null || subjectId.equals(r.subjectId()))
                .toList();
    }

    public List<ScheduleRow> forTeacher(Long teacherId) {
        return scheduleRepository.rowsForTeacher(teacherId);
    }

    @Transactional(readOnly = true)
    public ScheduleDto get(Long id) {
        return toDto(load(id));
    }

    @Transactional
    public ScheduleDto create(ScheduleDto dto) {
        if (dto.id() != null) {
            throw new BadRequestException("id must be empty when creating a session");
        }
        ClassSchedule entity = new ClassSchedule();
        apply(entity, dto);
        validate(entity, null);
        return toDto(scheduleRepository.save(entity));
    }

    @Transactional
    public ScheduleDto update(Long id, ScheduleDto dto) {
        ClassSchedule entity = load(id);
        apply(entity, dto);
        validate(entity, id);
        return toDto(scheduleRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        scheduleRepository.deleteById(load(id).getId());
    }

    private ClassSchedule load(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No class session found with id " + id));
    }

    private void apply(ClassSchedule entity, ScheduleDto dto) {
        subjectRepository.findById(dto.subjectId())
                .orElseThrow(() -> new NotFoundException("No subject found with id " + dto.subjectId()));
        teacherRepository.findById(dto.teacherId())
                .orElseThrow(() -> new NotFoundException("No teacher found with id " + dto.teacherId()));

        entity.setSubjectId(dto.subjectId());
        entity.setTeacherId(dto.teacherId());
        entity.setClassLevel(dto.classLevel().trim());
        entity.setSection(dto.section().trim().toUpperCase());
        entity.setRoom(trimOrNull(dto.room()));
        entity.setDayOfWeek(dto.dayOfWeek());
        entity.setStartTime(dto.startTime());
        entity.setEndTime(dto.endTime());
        entity.setTerm(dto.term() == null || dto.term().isBlank() ? "2026-ODD" : dto.term().trim());
    }

    private void validate(ClassSchedule candidate, Long selfId) {
        if (!candidate.getStartTime().isBefore(candidate.getEndTime())) {
            throw new BadRequestException("Start time must be earlier than end time");
        }
        for (ClassSchedule other : scheduleRepository.findAll()) {
            if (selfId != null && selfId.equals(other.getId())) {
                continue;
            }
            if (!Objects.equals(other.getDayOfWeek(), candidate.getDayOfWeek())) {
                continue;
            }
            boolean overlaps = candidate.getStartTime().isBefore(other.getEndTime())
                    && other.getStartTime().isBefore(candidate.getEndTime());
            if (!overlaps) {
                continue;
            }
            if (sameClass(other, candidate)) {
                throw new ConflictException("Time clash: %s %s already has a session at %s-%s on %s"
                        .formatted(other.getClassLevel(), other.getSection(), other.getStartTime(),
                                other.getEndTime(), dayName(other.getDayOfWeek())));
            }
            if (Objects.equals(other.getTeacherId(), candidate.getTeacherId())) {
                String teacher = teacherRepository.findById(candidate.getTeacherId())
                        .map(t -> t.getFirstName() + " " + t.getLastName())
                        .orElse("teacher #" + candidate.getTeacherId());
                throw new ConflictException("Double booking: %s is already teaching %s %s at %s-%s on %s"
                        .formatted(teacher, other.getClassLevel(), other.getSection(),
                                other.getStartTime(), other.getEndTime(), dayName(other.getDayOfWeek())));
            }
        }
    }

    private boolean sameClass(ClassSchedule a, ClassSchedule b) {
        return a.getClassLevel().equalsIgnoreCase(b.getClassLevel())
                && a.getSection().equalsIgnoreCase(b.getSection());
    }

    private String dayName(Integer day) {
        return switch (day == null ? 0 : day) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6 -> "Saturday";
            case 7 -> "Sunday";
            default -> "day " + day;
        };
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ScheduleDto toDto(ClassSchedule entity) {
        return new ScheduleDto(entity.getId(), entity.getSubjectId(), entity.getTeacherId(),
                entity.getClassLevel(), entity.getSection(), entity.getRoom(), entity.getDayOfWeek(),
                entity.getStartTime(), entity.getEndTime(), entity.getTerm());
    }
}
