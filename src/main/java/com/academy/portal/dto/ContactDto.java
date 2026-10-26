package com.academy.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactDto(
        Long id,
        @NotBlank @Size(max = 30) String category,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 80) String department,
        @NotBlank @Size(max = 30) String phone,
        @Size(max = 30) String alternatePhone,
        @Email @Size(max = 120) String email,
        @Size(max = 120) String location,
        @Size(max = 60) String availableHours) {
}
