package com.academy.portal.web;

import com.academy.portal.dto.CertificatePdf;
import com.academy.portal.dto.CertificateRequest;
import com.academy.portal.service.CertificateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    /** One page per selected graduate, returned as a single PDF the browser saves directly. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> issue(@Valid @RequestBody CertificateRequest request) {
        CertificatePdf file = certificateService.issue(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .header("X-Certificate-Count", String.valueOf(file.students()))
                .body(file.content());
    }
}
