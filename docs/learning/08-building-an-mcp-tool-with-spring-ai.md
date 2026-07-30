# Building an MCP Tool with Spring AI

**Where this comes from:** Task 6 — `CompanyResearchTool`, which exposes
`CompanyResearchAgent.research(...)` as an MCP tool an AI client (like Claude Code, or Claude
Desktop) can call.

**Level:** junior → mid-level. Assumes you know what a REST endpoint is; MCP is introduced from
scratch.

## What MCP actually is, in one paragraph

The Model Context Protocol (MCP) is a standard way for an AI application (a "client" — e.g. Claude
Desktop, Claude Code, or any MCP-aware agent) to discover and call **tools** exposed by a separate
**server**, without the client needing custom, hand-written integration code per tool. Think of it as
similar in spirit to how OpenAPI/Swagger lets a generic HTTP client discover a REST API's shape — MCP
lets a generic AI client discover "here are the tools you can call, here's what each one needs as
input, here's what it returns," and then actually invoke them, all through one shared protocol.
Concretely in this project: `company-research-agent` is a standalone Spring Boot application that
runs as its **own MCP server**, separate from the main `value-screener` backend — an AI client
connects to it and can call the one tool it exposes, `research_company`.

## Declaring a tool: `@McpTool` / `@McpToolParam`

```java
@Component
public class CompanyResearchTool {

    private final CompanyResearchAgent agent;

    public CompanyResearchTool(CompanyResearchAgent agent) {
        this.agent = agent;
    }

    @McpTool(name = "research_company", description = "Researches a company's quarterly report and business context for a given ticker")
    public CallToolResult researchCompany(
        @McpToolParam(description = "Stock ticker symbol") String ticker,
        @McpToolParam(description = "Full company name") String companyName
    ) {
        try {
            CompanyResearchResult result = agent.research(ticker, companyName);
            return CallToolResult.builder()
                .structuredContent(result)
                .build();
        } catch (ResearchTimeoutException | ResearchResponseParseException e) {
            return CallToolResult.builder()
                .isError(true)
                .addTextContent("Research failed: " + e.getMessage())
                .build();
        }
    }
}
```

This is almost the whole pattern. `@McpTool` marks a method as an MCP tool; Spring AI's MCP
annotation scanner finds it automatically at startup (no manual registration list to maintain) and
exposes it to any connecting client, using the `name`/`description` for tool discovery.
`@McpToolParam` does the same for each parameter — this is what lets a connecting AI client know
"this tool needs a `ticker` (a stock symbol) and a `companyName` (the full name)" *without* the client
needing hand-written knowledge of your specific method signature. You can confirm this actually
worked at runtime by checking the startup log for a line like `Registered tools: 1` — proof the tool
was genuinely discovered, not just that the code compiled.

## Why the tool method returns `CallToolResult`, not the raw result or a thrown exception

A naive first instinct might be to let `researchCompany(...)` simply return
`CompanyResearchResult` directly, and let any exception propagate normally (the way you might write
a plain Java method). This project deliberately does neither:

- **Returning the domain type directly** would work for the success path, but MCP's `CallToolResult`
  wrapper is what lets you *also* express "this call failed" as a first-class, protocol-level concept
  (`isError(true)`) rather than only being expressible via an exception.
- **Letting exceptions propagate raw** would mean a timeout or a parse failure inside
  `CompanyResearchAgent` crashes the tool call ungracefully from the calling AI client's perspective —
  instead of a clean, informative "this failed, here's why" response the client can reason about and
  potentially retry or explain to its own user.

So the method explicitly catches the two checked-turned-unchecked failure types this agent can throw
(`ResearchTimeoutException`, `ResearchResponseParseException` — see
`07-timeouts-and-virtual-threads.md` for where the first comes from) and converts them into a
graceful `CallToolResult` with `isError(true)`, rather than letting them propagate as raw exceptions
across the MCP boundary.

## Exhaustiveness as a code-review question, not just a testing one

Code review on this task specifically confirmed that the two-exception catch clause is
**exhaustive** — that `research(...)` genuinely cannot throw anything else, by checking that both
`ResearchTimeoutException` and `ResearchResponseParseException` are `RuntimeException` subclasses
with only a `(String, Throwable)` constructor, and tracing every code path in `research(...)` to
confirm no other runtime exception can escape uncaught. This is a habit worth adopting generally at
any boundary where you're translating internal exceptions into an external-facing response format
(REST controller advice, MCP tool wrappers, message queue handlers, etc.): don't just catch "the
exceptions I remember exist" — trace the actual call graph to confirm you've genuinely covered
everything that can be thrown, or an uncaught exception will still leak past your graceful-response
layer exactly when you didn't expect it.

## Takeaways for your own code

1. MCP tools are declared with annotations (`@McpTool`, `@McpToolParam`) that a framework-provided
   scanner discovers automatically — you don't hand-maintain a tool registry.
2. Verify tool registration actually happened at runtime (a startup log line, or a client like MCP
   Inspector actually listing the tool) — "the annotation is present" and "the tool is actually
   discoverable by a client" are different claims.
3. At any protocol boundary (MCP, REST, message queues), convert internal exceptions into an
   explicit, structured failure response rather than letting them propagate raw — the calling side
   should get something it can reason about, not a crash.
4. When you add exception handling at such a boundary, verify exhaustiveness by tracing the actual
   call graph — don't just catch the exceptions you remember writing.
