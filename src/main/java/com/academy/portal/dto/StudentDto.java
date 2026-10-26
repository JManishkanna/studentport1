package com.academy.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record StudentDto(
        Long id,
        @NotBlank @Size(max = 30) String rollNumber,
        @NotBlank @Size(max = 60) String firstName,
        @NotBlank @Size(max = 60) String lastName,
        @NotBlank @Email @Size(max = 120) String email,
        @Pattern(regexp = "^[+0-9 ().-]*$", message = "may contain digits, spaces and + ( ) . - only")
        @Size(max = 20) String phone,
        LocalDate dateOfBirth,
        @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "must be MALE, FEMALE or OTHER") String gender,
        @NotBlank @Size(max = 30) String classLevel,
        @Size(max = 10) String section,
        @Size(max = 200) String addressLine,
        @Size(max = 60) String city,
        @Size(max = 120) String guardianName,
        @Size(max = 20) String guardianPhone,
        @Email @Size(max = 120) String guardianEmail,
        LocalDate admissionDate,
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|ALUMNI)$", message = "must be ACTIVE, SUSPENDED or ALUMNI")
        String status,
        String photoUrl) {

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
