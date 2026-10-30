package com.academy.portal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/**
 * A whole week asked for in one go: the class, the days it meets, the clock rhythm of the day and
 * the ordered periods to lay across those days. The portal works out every start and end time.
 */
public record WeekPlanRequest(
        @NotBlank @Size(max = 30) String classLevel,
        @NotBlank @Size(max = 10) String section,
        @Size(max = 40) String room,
        @Size(max = 30) String term,
        @NotEmpty List<@Min(1) @Max(7) Integer> days,
        @NotNull LocalTime firstStart,
        @NotNull @Min(10) @Max(240) Integer periodMinutes,
        @Min(0) @Max(180) Integer breakMinutes,
        @NotNull @Min(1) @Max(8) Integer periodsPerDay,
        @NotEmpty List<Period> periods) {

    /** One teaching slot: who takes the class, in no particular order within the week. */
    public record Period(@NotNull Long subjectId, @NotNull Long teacherId) {
    }
}
