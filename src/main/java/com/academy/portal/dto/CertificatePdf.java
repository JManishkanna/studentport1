package com.academy.portal.dto;

/** A rendered certificate pack: the bytes to download and the name the browser should save it as. */
public record CertificatePdf(String filename, int students, byte[] content) {
}
