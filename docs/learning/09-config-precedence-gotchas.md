# Config Precedence Gotchas: the Haiku/Sonnet Saga

**Where this comes from:** Task 7 — a real production bug found via manual, live wire-level testing
(MCP Inspector against a real Anthropic API), not caught by any unit test, that took three separate
fix attempts to actually resolve.

**Level:** mid-level → advanced. This is a genuinely subtle bug about configuration precedence and
per-request option overrides — worth reading slowly even if you're experienced with Spring.

## The symptom

Manually calling the deployed `research_company` MCP tool for a real ticker (AAPL) failed with:

```
Anthropic 400: 'claude-haiku-4-5-20251001' does not support programmatic tool calling.
```

Nothing in this project's code ever explicitly asked for Haiku. The question this bug forces you to
confront: **if nobody asked for it, why is a specific model being used at all — and why is it the
wrong one?**

## Root cause #1: no chat model was pinned anywhere

Spring AI, absent any explicit configuration, has its own internal default chat model — in this
version, `claude-haiku-4-5-20251001`. Nothing in the plan or the design spec had ever set
`spring.ai.anthropic.chat.model` in `application.yml`, so the framework's own default silently took
over. This is a common category of bug: **an unset configuration value doesn't mean "no model is
used" — it means "the library's own default is used," and that default might not be compatible with
what your code actually needs.** Here specifically: Anthropic's hosted `web_search` tool requires
"programmatic tool calling" support, which — per Anthropic's own model-compatibility table — Haiku
does not support, only Sonnet/Opus-tier models do. The fix seemed obvious: set the model explicitly.

```yaml
spring:
  ai:
    anthropic:
      chat:
        model: claude-sonnet-5
```

Re-ran the live test. **Byte-identical error.** Same Haiku model, same failure. The obvious fix hadn't
worked at all.

## Root cause #2, found by decompiling `AnthropicChatModel` itself

This is the part worth genuinely understanding, not just memorizing as a war story. `application.yml`
configures Spring AI's **default** `ChatOptions` — but `CompanyResearchAgent` doesn't rely on that
default. It builds its **own** `AnthropicChatOptions` explicitly, specifically because it needs to
attach the web-search tool per request:

```java
Prompt prompt = new Prompt(
    promptText,
    AnthropicChatOptions.builder()
        .webSearchTool(AnthropicWebSearchTool.builder().maxUses(5).build())
        .build()
);
```

Decompiling `AnthropicChatModel.createRequest(...)` via `javap` revealed the actual behavior: **when
the `Prompt`'s options are already an `AnthropicChatOptions` instance — which is always true here,
since the agent constructs one itself — Spring AI uses it exactly as given, with no merge against the
property-configured defaults.** An unset `model` field on that per-request options object doesn't
"fall back" to `application.yml`'s value at all; it falls through to the underlying Anthropic Java
SDK's own hardcoded default, which is Haiku, completely bypassing Spring AI's own configuration layer.

In other words: **the YAML property only applies when you let Spring AI build the `ChatOptions` for
you from its own defaults. The moment your own code constructs and passes its own `ChatOptions`
instance — which this agent has to do, to attach a per-call tool — that object is used as-is, with no
merge step, no matter what `application.yml` says.**

## The actual fix: set the model explicitly, on every request

```java
public CompanyResearchAgent(
    ChatModel chatModel,
    ObjectMapper objectMapper,
    ResearchPromptBuilder promptBuilder,
    int timeoutSeconds,
    @Value("${spring.ai.anthropic.chat.model:claude-sonnet-5}") String model,
    @Value("${research.agent.web-search-max-uses:5}") long webSearchMaxUses
) { /* ... */ }

// inside the request-building code:
AnthropicChatOptions.builder()
    .webSearchTool(AnthropicWebSearchTool.builder().maxUses(webSearchMaxUses).build())
    .model(com.anthropic.models.messages.Model.of(model))
    .build();
```

Two things had to change together: the model now comes in via `@Value` injection (with a sensible
default matching the existing pattern already used for `timeoutSeconds`), *and* it's set explicitly on
the per-request builder via `.model(Model.of(model))` — confirmed, again via `javap`, that
`Model.of(String)` is the right factory method (no typed `CLAUDE_SONNET_5` constant existed yet in the
bundled Anthropic SDK version, but the open-string factory works fine). Setting a YAML property alone
was never going to be sufficient once the code path builds its own options object — the fix had to
happen at the exact point where that object is constructed.

## A second, smaller trap found in the same investigation: test config shadowing main config

While verifying the fix, it became clear the new `@Value` needed its **own default value**
(`:claude-sonnet-5`), rather than only relying on `application.yml`. Why: `src/test/resources/
application.yml` (a minimal test-only config file, created back in Task 1, with a dummy API key)
**shadows** `src/main/resources/application.yml` entirely on the test classpath — Maven puts
`test-classes` ahead of `classes` on the classpath, so for any test that boots a Spring context, the
test resource file wins completely, not just for keys it explicitly overrides. This matches an
existing pattern already in the codebase (`research.agent.timeout-seconds` already had its own
`@Value` default for exactly this reason) — recognizing the existing pattern was what made the fix
obvious once found, rather than needing to duplicate config into the test file.

## The general lesson: "I set the config" is not the same claim as "the config takes effect here"

This bug is worth remembering as a category, not just a specific fix: **whenever a library lets you
both (a) set global/default configuration, and (b) construct a fully-formed options object yourself
per call-site, always check whether (b) merges with (a) or completely replaces it.** Many frameworks
merge (later config on top of earlier defaults); some frameworks — as discovered here, specifically
for this version of Spring AI — replace wholesale the moment you hand in your own fully-built options
object. You cannot know which behavior applies from reading the property name alone; you have to
either read the actual merge logic (here: decompiled via `javap`) or test the actual behavior live.

## Takeaways for your own code

1. An unset configuration value is not "no behavior" — it's "the library's own default," which may be
   silently incompatible with what your code actually needs. Always know what the fallback default
   actually is, for anything load-bearing.
2. When a framework lets you both configure global defaults *and* construct fully-formed
   per-call-site option objects, verify whether the two merge or whether the per-call-site object
   wins outright, replacing the defaults entirely — don't assume either way.
3. A config change that "should obviously work" and doesn't, on the first attempt, is worth
   re-verifying at the actual code path that consumes it (via decompilation or logging the resolved
   value at the point of use) rather than assuming a typo and re-trying the same fix differently.
4. Test-only resource files can silently shadow main resource files entirely (not just override
   specific keys) depending on classpath ordering — give `@Value`-injected fields sensible defaults
   rather than assuming your main config always applies during tests.
