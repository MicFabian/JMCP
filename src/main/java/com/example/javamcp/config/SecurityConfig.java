package com.example.javamcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   McpSecurityProperties securityProperties,
                                                   McpIngressProperties ingressProperties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (ingressProperties.isEnforceHttps()) {
            http.addFilterBefore(new HttpsRedirectFilter(), AnonymousAuthenticationFilter.class);
        }
        configureHsts(http, ingressProperties);

        if (!securityProperties.enabled()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        if (ingressProperties.isEnforceHttps()) {
            http.addFilterBefore(new ApiKeyFilter(securityProperties), HttpsRedirectFilter.class);
        } else {
            http.addFilterBefore(new ApiKeyFilter(securityProperties), AnonymousAuthenticationFilter.class);
        }
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
        );
        http.httpBasic(Customizer.withDefaults());
        return http.build();
    }

    private void configureHsts(HttpSecurity http, McpIngressProperties ingressProperties) throws Exception {
        if (!ingressProperties.isHstsEnabled()) {
            http.headers(headers -> headers.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable));
            return;
        }

        http.headers(headers -> headers.httpStrictTransportSecurity(hsts -> {
            hsts.maxAgeInSeconds(ingressProperties.getHstsMaxAgeSeconds());
            hsts.includeSubDomains(ingressProperties.isHstsIncludeSubDomains());
            hsts.preload(ingressProperties.isHstsPreload());
        }));
    }

    private static final class HttpsRedirectFilter extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            if (HttpMethod.OPTIONS.matches(request.getMethod())) {
                return true;
            }
            return request.isSecure();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String host = headerOrDefault(request, "X-Forwarded-Host", request.getHeader("Host"));
            if (host == null || host.isBlank()) {
                host = request.getServerName();
            }

            StringBuilder location = new StringBuilder("https://")
                    .append(host)
                    .append(request.getRequestURI());
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                location.append('?').append(request.getQueryString());
            }

            response.setStatus(HttpServletResponse.SC_PERMANENT_REDIRECT);
            response.setHeader("Location", location.toString());
        }

        private String headerOrDefault(HttpServletRequest request, String header, String fallback) {
            String value = request.getHeader(header);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }

    private static final class ApiKeyFilter extends OncePerRequestFilter {

        private final McpSecurityProperties securityProperties;

        private ApiKeyFilter(McpSecurityProperties securityProperties) {
            this.securityProperties = securityProperties;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            if (HttpMethod.OPTIONS.matches(request.getMethod())) {
                return true;
            }
            return path.startsWith("/actuator/health")
                    || "/actuator/info".equals(path)
                    || "/actuator/prometheus".equals(path);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String provided = resolveProvidedApiKey(request);
            if (!isValidApiKey(provided, securityProperties.getApiKey())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("""
                        {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Missing or invalid API key"}
                        """);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "mcp-api-key",
                            null,
                            AuthorityUtils.NO_AUTHORITIES
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        private String resolveProvidedApiKey(HttpServletRequest request) {
            String directHeader = request.getHeader(securityProperties.getHeaderName());
            if (directHeader != null && !directHeader.isBlank()) {
                return directHeader;
            }

            String authorization = request.getHeader("Authorization");
            if (authorization == null) {
                return null;
            }
            if (!authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                return null;
            }
            return authorization.substring("Bearer ".length()).trim();
        }

        private boolean isValidApiKey(String provided, String expected) {
            if (provided == null || provided.isBlank() || expected == null || expected.isBlank()) {
                return false;
            }
            byte[] providedBytes = provided.trim().getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(providedBytes, expectedBytes);
        }
    }
}
