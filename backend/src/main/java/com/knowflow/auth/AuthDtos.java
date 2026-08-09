package com.knowflow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(max = 120) String organizationName) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record UserView(Long id, String email, String displayName, Long tenantId, String organizationRole) {}
    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserView user) {}
}
