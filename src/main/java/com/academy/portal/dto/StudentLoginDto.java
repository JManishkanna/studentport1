package com.academy.portal.dto;

/** What the administrator sees about a student's sign-in: the login id and whether the password is the academy default. */
public record StudentLoginDto(String username, boolean passwordIsDefault, String defaultPassword) {
}
