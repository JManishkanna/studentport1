package com.academy.portal.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** What the admin picked on the certificates page: who graduates, and the wording printed on the page. */
public record CertificateRequest(
        List<Long> studentIds,
        @NotNull(message = "must be a date") LocalDate conferredOn,
        String term) {
}
