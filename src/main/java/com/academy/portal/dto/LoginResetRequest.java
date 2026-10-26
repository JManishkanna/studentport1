package com.academy.portal.dto;

/** An empty password asks the server to restore the academy default. */
public record LoginResetRequest(String password) {
}
