# Spring Architecture Notes

This document captures the Spring-specific production patterns applied in JMCP and the reasoning behind them.

## Applied

- Validated `@ConfigurationProperties` for ingest, search, ingress, and security settings.
- `ProblemDetail` responses enabled and enriched with request path, timestamp, and validation errors.
- `ApiExceptionHandler` ordered at highest precedence so custom API errors win over lower-priority default MVC handlers.
- Virtual-thread task execution wrapped with `ContextPropagatingTaskDecorator` so observation and request context survive async fan-out.
- Core ingestion, indexing, search, and tool operations wrapped in Micrometer `Observation`s.
- `@Configuration(proxyBeanMethods = false)` applied to configuration classes and the main application class to avoid unnecessary CGLIB proxying.
- Startup rebuild and scheduled refresh jobs guarded by `@ConditionalOnProperty` so disabled features do not create live beans or scheduled invocations.
- Architectural boundaries enforced with ArchUnit:
  - top-level packages must be cycle-free
  - core packages may not depend on transport adapters
  - transport adapters may not depend on each other
  - REST controllers and controller advice stay in the `api` adapter

## Why

### Fail fast on broken configuration

Spring Boot supports validating `@ConfigurationProperties`, which is the right place to reject invalid operational settings before the app starts serving requests. JMCP now treats invalid search limits, missing remote source fields, and malformed ingress/security values as startup failures instead of runtime surprises.

### Preserve context when using virtual threads

JMCP fans out work during search and indexing. Spring Framework provides `ContextPropagatingTaskDecorator` specifically so logging, tracing, and observation context are not lost when work leaves the request thread. This matters once the app is deployed behind real observability tooling.

### Prefer RFC 9457-style API errors

Spring MVC’s `ProblemDetail` support is the correct HTTP surface for machine consumers. Enriching it with `path`, `timestamp`, and structured validation errors gives agents and clients a stable shape to react to, while still staying inside Spring’s error model.

### Keep configuration classes lite

Spring’s guidance around `proxyBeanMethods = false` exists for a reason: when inter-bean method interception is not needed, the app should not pay for proxied configuration classes. JMCP uses straightforward dependency injection, so lite mode is the correct default.

### Enforce boundaries in tests, not in tribal knowledge

Spring Modulith is the most natural long-term fit for application module verification, but its Spring Boot 4 support is still on preview-era lines. JMCP stays on the stable stack and uses ArchUnit to enforce the same core idea today: transport adapters stay thin, core services do not reach upward, and package cycles are not allowed.

## Practical Effect

- Faster failure on bad configuration.
- Lower accidental coupling between REST, GraphQL, gRPC, and native MCP adapters.
- More reliable observability across async execution.
- Less wasted search work for non-hybrid modes.
- Clearer operational behavior when index rebuilds or schedules are disabled.

## Sources

- [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html)
- [Spring Boot actuator observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Spring Framework observability and context propagation](https://docs.spring.io/spring-framework/reference/integration/observability.html)
- [Spring Framework REST exceptions and `ProblemDetail`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [`@Configuration` Javadoc and `proxyBeanMethods`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Configuration.html)
- [Spring Modulith appendix](https://docs.spring.io/spring-modulith/reference/2.1/appendix.html)
