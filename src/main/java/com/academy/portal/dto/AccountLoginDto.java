package com.academy.portal.dto;

/** What the administrator sees about a student's or teacher's sign-in: the login id and the password state. */
public record AccountLoginDto(String username, boolean passwordIsDefault, String defaultPassword) {
}
