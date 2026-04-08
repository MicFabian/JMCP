package com.example.javamcp.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class McpAcceptCompatibilityConfig {

    private static final String STREAMABLE_ACCEPT =
            MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.TEXT_EVENT_STREAM_VALUE;

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> mcpAcceptCompatibilityFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setName("mcpAcceptCompatibilityFilter");
        registration.setFilter(new McpAcceptCompatibilityFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/mcp", "/mcp/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> mcpImmediateFlushFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setName("mcpImmediateFlushFilter");
        registration.setFilter(new McpImmediateFlushFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/mcp", "/mcp/*");
        return registration;
    }

    private static final class McpAcceptCompatibilityFilter extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !HttpMethod.POST.matches(request.getMethod());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String normalizedAccept = normalizeAccept(request.getHeader(HttpHeaders.ACCEPT));
            if (normalizedAccept == null) {
                filterChain.doFilter(request, response);
                return;
            }

            filterChain.doFilter(new AcceptHeaderRequestWrapper(request, normalizedAccept), response);
        }

        private String normalizeAccept(String accept) {
            if (accept == null || accept.isBlank()) {
                return STREAMABLE_ACCEPT;
            }

            String lowered = accept.toLowerCase(Locale.ROOT);
            boolean hasJson = lowered.contains(MediaType.APPLICATION_JSON_VALUE);
            boolean hasEventStream = lowered.contains(MediaType.TEXT_EVENT_STREAM_VALUE);

            if (hasJson && hasEventStream) {
                return null;
            }
            if (lowered.contains("*/*")) {
                return STREAMABLE_ACCEPT;
            }
            if (hasJson) {
                return accept + ", " + MediaType.TEXT_EVENT_STREAM_VALUE;
            }
            if (hasEventStream) {
                return accept + ", " + MediaType.APPLICATION_JSON_VALUE;
            }
            return null;
        }
    }

    private static final class McpImmediateFlushFilter extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !HttpMethod.GET.matches(request.getMethod());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            filterChain.doFilter(request, response);

            if (!request.isAsyncStarted()) {
                return;
            }

            String contentType = response.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                return;
            }

            response.flushBuffer();
        }
    }

    private static final class AcceptHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String accept;

        private AcceptHeaderRequestWrapper(HttpServletRequest request, String accept) {
            super(request);
            this.accept = accept;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
                return accept;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(accept));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(HttpHeaders.ACCEPT);
            return Collections.enumeration(names);
        }
    }
}
