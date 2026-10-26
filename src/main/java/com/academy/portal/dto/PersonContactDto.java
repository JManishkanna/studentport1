package com.academy.portal.dto;

/**
 * A person the signed-in user is allowed to reach, derived from their own classes rather than from
 * the manually maintained desk directory. Scope is decided server-side: a student gets the faculty
 * who teach their class plus the administration mailbox, a teacher gets the students they teach,
 * an admin gets both sides.
 */
public record PersonContactDto(
        Long id,
        String kind,
        String name,
        String code,
        String phone,
        String alternatePhone,
        String email,
        String detail,
        String group,
        String division,
        String photoUrl) {

    public static final String FACULTY = "FACULTY";
    public static final String STUDENT = "STUDENT";
    public static final String ADMINISTRATION = "ADMINISTRATION";
}
