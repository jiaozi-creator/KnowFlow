package com.knowflow.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.BusinessException;
import com.knowflow.config.AppProperties;
import com.knowflow.security.JwtService;
import com.knowflow.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper memberMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties.Jwt jwtProperties;

    public AuthService(UserMapper userMapper, OrganizationMapper organizationMapper,
                       OrganizationMemberMapper memberMapper, RefreshTokenMapper refreshTokenMapper,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AppProperties.Jwt jwtProperties) {
        this.userMapper = userMapper;
        this.organizationMapper = organizationMapper;
        this.memberMapper = memberMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userMapper.findByEmail(request.email()) != null) throw BusinessException.badRequest("邮箱已注册");

        UserEntity user = new UserEntity();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        OrganizationEntity org = new OrganizationEntity();
        org.setName(request.organizationName().trim());
        org.setSlug("org-" + UUID.randomUUID().toString().substring(0, 8));
        organizationMapper.insert(org);

        OrganizationMemberEntity member = new OrganizationMemberEntity();
        member.setOrganizationId(org.getId());
        member.setUserId(user.getId());
        member.setRole("OWNER");
        memberMapper.insert(member);
        return issue(user, member);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserEntity user = userMapper.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("邮箱或密码错误");
        }
        OrganizationMemberEntity member = memberMapper.firstByUserId(user.getId());
        if (member == null) throw BusinessException.forbidden("用户尚未加入企业");
        return issue(user, member);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshTokenEntity saved = refreshTokenMapper.findActive(hash(request.refreshToken()));
        if (saved == null) throw BusinessException.unauthorized("刷新令牌无效或已过期");
        saved.setRevokedAt(OffsetDateTime.now());
        refreshTokenMapper.updateById(saved);
        UserEntity user = userMapper.selectById(saved.getUserId());
        OrganizationMemberEntity member = memberMapper.selectOne(new LambdaQueryWrapper<OrganizationMemberEntity>()
                .eq(OrganizationMemberEntity::getOrganizationId, saved.getOrganizationId())
                .eq(OrganizationMemberEntity::getUserId, saved.getUserId()));
        return issue(user, member);
    }

    private AuthDtos.AuthResponse issue(UserEntity user, OrganizationMemberEntity member) {
        UserPrincipal principal = new UserPrincipal(user.getId(), member.getOrganizationId(), user.getEmail(), member.getRole());
        String access = jwtService.createAccessToken(principal);
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUserId(user.getId());
        token.setOrganizationId(member.getOrganizationId());
        token.setTokenHash(hash(rawRefresh));
        token.setExpiresAt(OffsetDateTime.now().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenMapper.insert(token);
        return new AuthDtos.AuthResponse(access, rawRefresh, jwtService.accessTokenSeconds(),
                new AuthDtos.UserView(user.getId(), user.getEmail(), user.getDisplayName(), member.getOrganizationId(), member.getRole()));
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
