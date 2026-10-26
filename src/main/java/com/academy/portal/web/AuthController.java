package com.academy.portal.web;

import com.academy.portal.dto.MeDto;
import com.academy.portal.dto.PasswordChangeRequest;
import com.academy.portal.service.AccountService;
import com.academy.portal.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;
    private final AccountService accountService;

    public AuthController(CurrentUserService currentUserService, AccountService accountService) {
        this.currentUserService = currentUserService;
        this.accountService = accountService;
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

    @PostMapping("/password")
    public Map<String, String> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        accountService.changePassword(request.currentPassword(), request.newPassword());
        return Map.of("status", "password-changed");
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
