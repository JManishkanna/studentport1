package com.academy.portal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Sends browsers to the sign-in page but answers JSON calls with 401.
 * A redirect on /api/** would be followed transparently by fetch(), so the client would read the
 * login page as a successful response and loop back to it instead of showing the sign-in form.
 */
public class PortalAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String EXPIRED_MESSAGE = "Your session ended. Please sign in again.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String path = request.getRequestURI();
        boolean isApi = path.startsWith("/api/")
                || MediaType.APPLICATION_JSON_VALUE.equals(request.getHeader("Accept"));
        if (!isApi) {
            response.sendRedirect("/login.html");
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                + EXPIRED_MESSAGE + "\"}");
    }
}
