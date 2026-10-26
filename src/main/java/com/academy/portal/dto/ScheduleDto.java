package com.academy.portal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record ScheduleDto(
        Long id,
        @NotNull Long subjectId,
        @NotNull Long teacherId,
        @NotBlank @Size(max = 30) String classLevel,
        @NotBlank @Size(max = 10) String section,
        @Size(max = 40) String room,
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 30) String term) {
}
