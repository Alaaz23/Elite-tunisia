package tn.elitetunisia.auth.dto;

import java.util.UUID;
import java.util.Set;

public final class AuthResponse {
	private final String accessToken;
	private final long expiresIn;
	private final UUID userId;
	private final String email;
	private final Set<String> roles;

	public AuthResponse(String accessToken, long expiresIn, UUID userId, String email, Set<String> roles) {
		this.accessToken = accessToken;
		this.expiresIn = expiresIn;
		this.userId = userId;
		this.email = email;
		this.roles = Set.copyOf(roles);
	}

	public String getAccessToken() { return accessToken; }
	public long getExpiresIn() { return expiresIn; }
	public UUID getUserId() { return userId; }
	public String getEmail() { return email; }
	public Set<String> getRoles() { return roles; }
}