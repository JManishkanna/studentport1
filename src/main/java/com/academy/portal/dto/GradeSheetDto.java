package com.academy.portal.dto;

import java.util.List;

/** The marking sheet for one assessment: every student on the roster with their marks so far. */
public record GradeSheetDto(AssessmentDto assessment, List<Row> students) {

    public record Row(
            Long studentId,
            String rollNumber,
            String fullName,
            String photoUrl,
            Double marks,
            String remark) {
    }
}
