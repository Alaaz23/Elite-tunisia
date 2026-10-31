package tn.elitetunisia.auth.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.dto.AuthResponse;
import tn.elitetunisia.auth.dto.LoginRequest;
import tn.elitetunisia.auth.dto.RegisterRequest;
import tn.elitetunisia.auth.entity.RefreshTokenEntity;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.RefreshTokenRepository;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.auth.security.TokenService;
import tn.elitetunisia.common.exception.EmailAlreadyRegisteredException;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.customer.CustomerEntity;
import tn.elitetunisia.customer.CustomerRepository;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final long refreshTokenDays;
    private final RoleRepository roles;
    private final CustomerRepository customers;
    private final AuditService audit;
    private final LoginThrottle throttle;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            RoleRepository roles, CustomerRepository customers, AuthenticationManager authenticationManager, TokenService tokens,
            AuditService audit, LoginThrottle throttle, @Value("${app.security.jwt.refresh-token-days}") long refreshTokenDays) {
        this.users = users;
        this.roles = roles;
        this.customers = customers;
        this.audit = audit;
        this.throttle = throttle;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public LoginResult register(RegisterRequest request, String ipAddress) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        UserEntity user = new UserEntity(email, passwordEncoder.encode(request.getPassword()));
        user.addRole(roles.findById("CUSTOMER").orElseThrow());
        user = users.save(user);
        customers.save(new CustomerEntity(user, request.getFirstName().trim(), request.getLastName().trim(), request.getPhone().trim()));
        audit.recordAudit(user.getId(), "REGISTER", "USER", user.getId(), ipAddress, "SUCCESS");
        return issue(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResult login(LoginRequest request, String ipAddress) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String throttleKey = LoginThrottle.key(email, ipAddress);
        throttle.checkAllowed(throttleKey);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException exception) {
            throttle.recordFailure(throttleKey);
            audit.recordAudit(null, "LOGIN", "SESSION", null, ipAddress, "FAILURE");
            throw exception;
        }
        throttle.recordSuccess(throttleKey);
        UserEntity user = users.findByEmailIgnoreCase(email).orElseThrow();
        audit.recordAudit(user.getId(), "LOGIN", "SESSION", null, ipAddress, "SUCCESS");
        return issue(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResult refresh(String rawToken, String ipAddress) {
        RefreshTokenEntity current = refreshTokens.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId());
            audit.recordAudit(current.getUser().getId(), "REFRESH_REPLAY", "SESSION", current.getId(), ipAddress, "FAILURE");
            throw new InvalidRefreshTokenException();
        }
        if (!current.getExpiresAt().isAfter(Instant.now()) || !current.getUser().isEnabled()) {
            current.revoke();
            revokeFamily(current.getFamilyId());
            audit.recordAudit(current.getUser().getId(), "REFRESH", "SESSION", current.getId(), ipAddress, "FAILURE");
            throw new InvalidRefreshTokenException();
        }
        current.revoke();
        LoginResult result = issue(current.getUser(), current.getFamilyId());
        audit.recordAudit(current.getUser().getId(), "REFRESH", "SESSION", current.getId(), ipAddress, "SUCCESS");
        return result;
    }

    @Transactional
    public void logout(String rawToken, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token -> {
            token.revoke();
            audit.recordAudit(token.getUser().getId(), "LOGOUT", "SESSION", token.getId(), ipAddress, "SUCCESS");
        });
    }

    private void revokeFamily(UUID familyId) {
        refreshTokens.findAllByFamilyIdAndRevokedAtIsNull(familyId).forEach(RefreshTokenEntity::revoke);
    }

    private LoginResult issue(UserEntity user, UUID familyId) {
        String refreshToken = newRefreshToken();
        refreshTokens.save(new RefreshTokenEntity(user, hash(refreshToken), familyId,
                Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS)));
        AuthResponse response = new AuthResponse(tokens.createAccessToken(user.getEmail()),
            tokens.accessTokenSeconds(), user.getId(), user.getEmail(), user.getRoleKeys());
        return new LoginResult(response, refreshToken);
    }

    public record LoginResult(AuthResponse response, String refreshToken) { }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

}