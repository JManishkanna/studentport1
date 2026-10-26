package com.academy.portal.dto;

import java.util.List;

/** The fixed wording shared by every page, plus one entry per graduate in the run. */
public record CertificatePreviewDto(String heading, String certifyLine, List<CertificatePageDto> pages) {
}
