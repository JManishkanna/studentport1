package com.academy.portal.dto;

import java.util.List;

/**
 * One certificate page as text. The picker draws it for the preview and the PDF renderer writes the
 * same strings, so what is shown on screen cannot drift from what is downloaded.
 */
public record CertificatePageDto(
        String academyName,
        String studentName,
        String rollLine,
        String statement,
        String subjects,
        String conferredLine,
        List<Signature> signatures,
        String serialLine) {

    public record Signature(String name, String role) {
    }
}
