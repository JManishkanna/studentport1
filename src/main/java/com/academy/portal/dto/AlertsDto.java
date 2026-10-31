package com.academy.portal.dto;

import java.time.LocalDate;
import java.util.List;

/** Warnings worked out from attendance, marks and deadlines when the caller asks for them. */
public record AlertsDto(double threshold, LocalDate generatedOn, int total, List<Alert> alerts) {

    /** level is danger, warn or info. */
    public record Alert(String level, String kind, String title, String detail, LocalDate date, String link) {
    }
}
