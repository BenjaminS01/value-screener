# Jackson 2 vs. Jackson 3, and Spring Boot 4 Autoconfiguration

**Where this comes from:** a regression discovered *during* Task 4, outside that task's own file
scope — `ResearchAgentApplicationTests.contextLoads` broke after adding `CompanyResearchAgent`, even
though `CompanyResearchAgent`'s own unit tests were all green.

**Level:** mid-level. Assumes basic Spring Boot dependency injection knowledge; introduces a
real-world "two versions of a library on the same classpath" scenario.

## The symptom

`CompanyResearchAgent`'s own test suite (`CompanyResearchAgentTest`) passed 4/4 — but a
*pre-existing, previously-green* test elsewhere in the same module,
`ResearchAgentApplicationTests.contextLoads`, started failing with:

```
NoSuchBeanDefinitionException: No qualifying bean of type
'com.fasterxml.jackson.databind.ObjectMapper' available
```

This is a genuinely confusing failure mode for anyone who hasn't seen it before: the class you just
wrote has a dependency (`ObjectMapper`) that is an extremely common, seemingly-guaranteed-to-exist
Spring Boot bean. Why would it suddenly not be there?

## Why `CompanyResearchAgentTest` didn't catch this

`CompanyResearchAgentTest` constructs `CompanyResearchAgent` directly with `new ObjectMapper()` — it
never goes through Spring's dependency injection container at all. A plain unit test that
hand-constructs its subject under test will never notice a wiring problem, because there's no wiring
happening. `ResearchAgentApplicationTests.contextLoads`, by contrast, boots the **real** Spring
application context — the same one that would run in production — and that's where the gap surfaced.
This is exactly why both kinds of tests matter: fast, isolated unit tests (fast feedback, but blind to
wiring) and at least one real context-loading test (slower, but the only thing that proves the whole
application actually assembles correctly).

## The root cause: two different `ObjectMapper` types, only one gets a bean

This is the genuinely interesting part. Spring Boot 4 introduced Jackson 3 as its **new default** JSON
stack — but Jackson 3 is a different package/type from classic Jackson 2:

| | Jackson 2 (classic) | Jackson 3 (Spring Boot 4 default) |
|---|---|---|
| Group/package | `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| Who registers a Spring bean for it, by default, in Boot 4 | nobody, unless you ask | Spring Boot's own autoconfiguration |

`mvn dependency:tree` confirmed both jars were present on the classpath simultaneously — but for two
completely different reasons:

- **Jackson 3** was pulled in transitively via `spring-ai-starter-mcp-server-webmvc` →
  `spring-boot-starter-web`, and Spring Boot 4.1's autoconfiguration duly registers a bean for it —
  this is the "default" JSON handling path Spring Boot expects most application code to use going
  forward.
- **Jackson 2** was *also* present — but only as a transitive dependency of `com.anthropic:
  anthropic-java-core` (the underlying Anthropic Java SDK that Spring AI's Anthropic integration
  wraps), which still uses the classic Jackson 2 API internally. Spring Boot has no reason to
  register a bean for a type it doesn't autoconfigure by default — the jar being *present on the
  classpath* is not the same thing as a *bean existing in the application context*.

`CompanyResearchAgent`'s constructor — written against `com.fasterxml.jackson.databind.ObjectMapper`
(Jackson 2), because that's what the brief specified and what the codebase generally used — asked
Spring to autowire a bean type that genuinely didn't exist anywhere in the context. Not a typo, not a
missing dependency — a real gap between "what's on the classpath" and "what Spring Boot's
autoconfiguration decided to expose as an injectable bean."

## The fix: an explicit `@Bean`

```java
@Configuration
public class JacksonConfig {
    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
```

One line of real substance: register the missing bean type explicitly, since Spring Boot's
autoconfiguration won't do it for you when your actual dependency needs the *other* Jackson major
version than the framework's own default. After adding this, the full module suite went from 14/15 to
15/15 — `contextLoads` (previously failing) now passes because the bean genuinely exists.

## The general lesson: framework version bumps can silently change *implicit* wiring, not just APIs

Most developers think of a major framework version bump (Spring Boot 3 → 4, here) primarily in terms
of explicit breaking API changes — methods removed, signatures changed, things that show up as
compile errors. This bug is a different, quieter category: **the framework's autoconfiguration
defaults changed** (Boot 4 defaults to Jackson 3), which doesn't break compilation at all — your code
using Jackson 2 still compiles fine, since the jar is still on the classpath transitively. It only
breaks at *runtime*, and specifically only in the one test (or production startup) that actually boots
the real container, not in any test that manually constructs its subject.

This is why "does the app actually boot with real Spring wiring" is a test worth having even when
your unit tests are thorough and fast — some categories of bug are invisible to any test that
bypasses the container.

## Takeaways for your own code

1. A jar being present on the classpath (even confirmed via `mvn dependency:tree`) does not mean
   Spring Boot registers a bean for every type inside it — autoconfiguration is selective and
   version-dependent.
2. When two libraries in your dependency tree depend on *different major versions* of the same
   underlying library (here: your app code wants Jackson 2, Spring Boot 4's own autoconfiguration
   defaults to Jackson 3), diagnose with `mvn dependency:tree`, not guesswork — it tells you exactly
   which artifact pulled in which version and why.
3. Keep at least one test that boots the real Spring application context (`@SpringBootTest`-style),
   even if most of your tests are fast, isolated unit tests that hand-construct their subject — this
   is the only test category that catches DI wiring gaps like this one.
4. When you find a regression like this outside your current task's file scope, the disciplined move
   is to surface it explicitly (as its own documented finding) rather than silently patching it inside
   an unrelated task — it needs its own explicit decision about which task/PR should own the fix.
