package com.academy.portal.dto;

import java.time.LocalDate;
import java.util.List;

/** Students whose attendance in the range is under the threshold, lowest first. */
public record AtRiskDto(LocalDate from, LocalDate to, double threshold, List<Row> students) {

    /** needed is how many classes in a row the student must attend to reach the threshold again. */
    public record Row(
            Long studentId,
            String rollNumber,
            String fullName,
            String photoUrl,
            String classLevel,
            String section,
            long present,
            long absent,
            long late,
            long total,
            double rate,
            long needed,
            String weakestSubject,
            Double weakestRate) {
    }
}
