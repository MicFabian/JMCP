# JMCP Agent Guidance

Use JMCP tools by default for Java and Spring work in this repository.

## Tool routing

- Use `java-docs` for Java, Spring Boot, Spring Framework, Spring Security, JPA, Jakarta, Gradle, and OpenJDK usage questions.
- Use `java-docs` before answering from memory when the request is about configuration, best practices, migrations, or API usage.
- Use `analyze` before suggesting fixes for Java source code smells, dependency injection issues, blocking calls, or unsafe patterns.
- Use `symbols` for call-graph, dependency, symbol graph, method usage, or architecture-navigation questions.
- Use `migration-assistant` for Java 25, Spring Boot 4, Jakarta migration, or Gradle upgrade planning.
- Use `search` only when the request is broad and no library-specific scope is obvious.
- Prefer citations from returned `sourceUrl` values when giving framework guidance.

## Expected behavior

- For framework questions, do not answer from prior knowledge alone if JMCP can answer it.
- For code review or code-fix requests on Java snippets, run `analyze` first.
- For upgrade requests, combine `migration-assistant` with `java-docs` if version-specific guidance is needed.
- Keep answers version-aware when the user mentions Java, Spring Boot, or library versions.
