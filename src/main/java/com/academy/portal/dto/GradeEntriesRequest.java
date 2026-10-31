package com.academy.portal.dto;

import java.math.BigDecimal;
import java.util.List;

/** Marks for a sheet. A null mark clears that student's grade. */
public record GradeEntriesRequest(List<Entry> entries) {

    public record Entry(Long studentId, BigDecimal marks, String remark) {
    }
}
