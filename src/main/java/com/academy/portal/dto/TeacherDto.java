package com.academy.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TeacherDto(
        Long id,
        @NotBlank @Size(max = 30) String employeeCode,
        @NotBlank @Size(max = 60) String firstName,
        @NotBlank @Size(max = 60) String lastName,
        @Size(max = 60) String designation,
        @NotBlank @Size(max = 60) String department,
        @NotBlank @Email @Size(max = 120) String email,
        @Pattern(regexp = "^[+0-9 ().-]*$", message = "may contain digits, spaces and + ( ) . - only")
        @Size(max = 20) String phone,
        @Size(max = 120) String specialization,
        @Size(max = 120) String qualification,
        @Size(max = 40) String officeRoom,
        LocalDate joiningDate,
        @Pattern(regexp = "^(ACTIVE|ON_LEAVE|INACTIVE)$", message = "must be ACTIVE, ON_LEAVE or INACTIVE")
        String status) {

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
