package tn.elitetunisia.auth.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.validation.Valid;
import tn.elitetunisia.auth.dto.AuthResponse;
import tn.elitetunisia.auth.dto.LoginRequest;
import tn.elitetunisia.auth.dto.RegisterRequest;
import tn.elitetunisia.auth.service.AuthService;
import tn.elitetunisia.auth.service.InvalidRefreshTokenException;
import tn.elitetunisia.user.dto.UserSummary;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {
    private static final String REFRESH_COOKIE = "elite_refresh";
    private final AuthService authService;
    private final AccountService accounts;
    private final boolean secureCookies;

    public AuthController(AuthService authService, AccountService accounts,
            @Value("${app.security.secure-cookies}") boolean secureCookies) {
        this.authService = authService;
        this.accounts = accounts;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Créer un compte client")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response,
            HttpServletRequest servletRequest) {
        var result = authService.register(request, servletRequest.getRemoteAddr());
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(201).body(result.response());
    }

    @PostMapping("/login")
    @Operation(summary = "Ouvrir une session")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response,
            HttpServletRequest servletRequest) {
        var result = authService.login(request, servletRequest.getRemoteAddr());
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouveler les jetons de session")
    public AuthResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response, HttpServletRequest servletRequest) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        var result = authService.refresh(refreshToken, servletRequest.getRemoteAddr());
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/logout")
    @Operation(summary = "Révoquer la session de renouvellement")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response, HttpServletRequest servletRequest) {
        authService.logout(refreshToken, servletRequest.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Consulter l'utilisateur authentifié")
    public UserSummary me(org.springframework.security.core.Authentication authentication) {
        return accounts.current(authentication.getName());
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(value, Duration.ofDays(30)).toString());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }

    @GetMapping("/csrf")
    @Operation(summary = "Initialiser le jeton CSRF du navigateur")
    public java.util.Map<String, String> csrf(CsrfToken csrfToken) {
        return java.util.Map.of("token", csrfToken.getToken());
    }
}