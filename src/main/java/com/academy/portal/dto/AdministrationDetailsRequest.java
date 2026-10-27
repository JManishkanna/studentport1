package com.academy.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The administration line on the contacts page: name, mailbox and phone. */
public record AdministrationDetailsRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Email @Size(max = 120) String email,
        @Size(max = 20) String phone) {
}
