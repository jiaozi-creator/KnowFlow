package com.knowflow.security;

import java.io.Serializable;

public record UserPrincipal(Long userId, Long tenantId, String email, String role) implements Serializable {}
