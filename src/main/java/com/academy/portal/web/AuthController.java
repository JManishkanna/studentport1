package com.academy.portal.web;

import com.academy.portal.dto.MeDto;
import com.academy.portal.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /** Hands the CSRF token to the JS client, which echoes it in the XSRF-TOKEN header on writes. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = resolveToken(request);
        Map<String, String> body = new LinkedHashMap<>();
        if (token != null) {
            body.put("headerName", token.getHeaderName());
            body.put("parameterName", token.getParameterName());
            body.put("token", token.getToken());
        }
        return body;
    }

    @GetMapping("/me")
    public MeDto me() {
        return currentUserService.me();
    }

    private CsrfToken resolveToken(HttpServletRequest request) {
        Object deferred = request.getAttribute(DeferredCsrfToken.class.getName());
        if (deferred instanceof DeferredCsrfToken token) {
            return token.get();
        }
        Object legacy = request.getAttribute(CsrfToken.class.getName());
        return legacy instanceof CsrfToken token ? token : null;
    }
}
