package com.academy.portal.dto;

/** The new credential is handed back once, because only its hash is kept from here on. */
public record StudentLoginReset(String username, String password, boolean passwordIsDefault) {
}
