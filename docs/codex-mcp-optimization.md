# Codex MCP Optimization

This document captures the changes that most improve whether Codex actually calls JMCP instead of answering from memory.

## What matters most

1. The MCP server must be enabled in Codex for the active project.
2. The auth mechanism must match the way the server is really protected.
3. Tool metadata must explain when to use a tool, not only what it does.
4. High-level intent-shaped tools are selected more reliably than multi-step low-level tools.
5. Tool outputs should be typed and linked back to MCP resources.
6. Repository rules should explicitly tell Codex to use JMCP first for Java and Spring questions.

## Applied in JMCP

- Project-scoped Codex config in [`/Users/mivi/IdeaProjects/jmcp/.codex/config.toml`](/Users/mivi/IdeaProjects/jmcp/.codex/config.toml)
- Repo instructions in [`/Users/mivi/IdeaProjects/jmcp/AGENTS.md`](/Users/mivi/IdeaProjects/jmcp/AGENTS.md)
- High-level `java-docs` tool in [`/Users/mivi/IdeaProjects/jmcp/src/main/java/com/example/javamcp/mcp/NativeMcpSpecificationFactory.java`](/Users/mivi/IdeaProjects/jmcp/src/main/java/com/example/javamcp/mcp/NativeMcpSpecificationFactory.java)
- Read-only/idempotent tool annotations
- Structured MCP `outputSchema` for tool results
- `resource_link` content for doc-oriented tool results
- Stricter input schemas with `additionalProperties: false`

## Operating guidance

- Prefer the project-scoped `.codex/config.toml` over a one-off global `codex mcp add` entry.
- Prefer `env_http_headers = { "X-API-Key" = "JMCP_API_KEY" }` when JMCP is protected by an API key header.
- Restart Codex after changing config or environment variables.
- Keep local and prod servers declared, but only one enabled by default per project.
- Avoid exposing maintenance tools as the main path in prompting. The retrieval and analysis tools should dominate the guidance.

## Golden prompts

Use these prompts to verify Codex reaches for JMCP:

1. `How do I configure Spring Security CSRF requestMatchers in Boot 4?`
Expected tool: `java-docs`

2. `Review this Java service and fix the field injection smell.`
Expected tool: `analyze`

3. `Who calls this method in the snippet below?`
Expected tool: `symbols`

4. `Help me migrate this Gradle project from Java 17 / Boot 3.3 to Java 25 / Boot 4.`
Expected tool: `migration-assistant`

5. `Find docs for virtual threads in Java 25.`
Expected tool: `java-docs`

## Why these changes work

- Codex supports project-level MCP configuration and repo instruction files, so the project can bias tool use without depending on the user’s global state.
- OpenAI tool metadata guidance emphasizes strong titles, descriptions, and clearly typed schemas.
- The MCP spec explicitly recommends `outputSchema`, structured content, and resource links for better client behavior.
- Context7’s usage model leans heavily on client rules and a small number of predictable docs tools; JMCP now follows the same pattern for Java.

## Sources

- [Codex MCP docs](https://developers.openai.com/codex/mcp)
- [Codex rules docs](https://developers.openai.com/codex/rules)
- [OpenAI function calling guide](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI metadata optimization guide](https://developers.openai.com/apps-sdk/guides/optimize-metadata)
- [MCP tools specification](https://modelcontextprotocol.io/specification/2025-06-18/server/tools)
- [Context7 MCP architecture summary](https://deepwiki.com/upstash/context7-mcp)
