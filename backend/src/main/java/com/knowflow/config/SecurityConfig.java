package com.knowflow.config;

import com.knowflow.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    /**
     * Spring Security 核心配置
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter
    ) throws Exception {

        return http
                // 前后端分离 + JWT，不使用 CSRF
                .csrf(csrf -> csrf.disable())

                // 启用 CORS
                .cors(cors -> {})

                // JWT 无状态认证，不创建 Session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /*
                 * 处理 Spring Security 层产生的 401 / 403。
                 *
                 * 如果不配置这一部分，
                 * Spring Security 某些情况下会只返回状态码而没有 JSON body，
                 * 前端执行 response.json() 时就可能出现：
                 *
                 * Unexpected end of JSON input
                 */
                .exceptionHandling(exception -> exception

                        // 未登录 / Token 无效 / Token 过期
                        .authenticationEntryPoint(
                                (request, response, authException) -> {

                                    response.setStatus(401);
                                    response.setCharacterEncoding("UTF-8");
                                    response.setContentType(
                                            "application/json;charset=UTF-8"
                                    );

                                    response.getWriter().write(
                                            """
                                            {
                                              "code": 40100,
                                              "message": "登录状态已失效，请重新登录",
                                              "data": null
                                            }
                                            """
                                    );
                                }
                        )

                        // 已登录，但没有访问权限
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) -> {

                                    response.setStatus(403);
                                    response.setCharacterEncoding("UTF-8");
                                    response.setContentType(
                                            "application/json;charset=UTF-8"
                                    );

                                    response.getWriter().write(
                                            """
                                            {
                                              "code": 40300,
                                              "message": "无权执行该操作",
                                              "data": null
                                            }
                                            """
                                    );
                                }
                        )
                )

                /*
                 * 接口权限配置
                 */
                .authorizeHttpRequests(auth -> auth

                        // 登录、注册、刷新 Token
                        .requestMatchers(
                                "/api/auth/**"
                        )
                        .permitAll()

                        // 健康检查
                        .requestMatchers(
                                "/actuator/health"
                        )
                        .permitAll()

                        // Swagger
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        // 浏览器 CORS 预检请求
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()

                        // 其他接口必须登录
                        .anyRequest()
                        .authenticated()
                )

                /*
                 * JWT Filter 必须放在
                 * UsernamePasswordAuthenticationFilter 前面。
                 */
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    /**
     * 密码加密器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 跨域配置
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            AppProperties.Cors properties
    ) {

        CorsConfiguration configuration =
                new CorsConfiguration();

        // 从 application.yml 中读取允许访问的前端地址
        configuration.setAllowedOrigins(
                properties.allowedOrigins()
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        // Authorization、Content-Type 等全部允许
        configuration.setAllowedHeaders(
                List.of("*")
        );

        // 允许前端读取下载文件名等响应头
        configuration.setExposedHeaders(
                List.of(
                        "Content-Disposition"
                )
        );

        // 允许携带认证信息
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}