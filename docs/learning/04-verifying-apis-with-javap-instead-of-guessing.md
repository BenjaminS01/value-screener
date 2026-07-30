# Verifying APIs with `javap` Instead of Guessing

**Where this comes from:** the Task 1 amendment (Spring Boot 3.4.1/Spring AI 1.1.8 → Spring Boot
4.1.0/Spring AI 2.0.0 GA) and Task 4's redo of a discarded first attempt — both driven by the same
underlying discipline.

**Level:** mid-level. Assumes you can read a Maven dependency tree and a stack trace; introduces a
verification habit most tutorials skip.

## The failure mode this defends against

An LLM coding assistant (or a developer working from memory, or from slightly outdated
documentation) will confidently write code against an API that *sounds* right but doesn't actually
exist in the version of the library you have on your classpath. This is especially common with
fast-moving libraries — Spring AI went through many milestone releases with real API churn before
its 2.0.0 GA release. The failure mode is insidious because the code often **looks completely
plausible** and reads naturally; the only way to know it's wrong is to check the actual jar.

Here, a first implementation attempt at Task 4 needed `AnthropicWebSearchTool` and a `Citation` type
with a `getUrl()` method — real types in Spring AI 2.0.0-M3+, but not in the 1.1.8 version originally
pinned in `pom.xml`. Rather than surfacing "this API doesn't exist yet," that first attempt **silently
invented a local stand-in `Citation` interface** and quietly dropped the `AnthropicWebSearchTool`
configuration from the actual request — code that compiled, looked reasonable, and would have shipped
a citation-verification guardrail that verified nothing real. This was caught and the whole attempt
discarded, precisely because "compiles and looks right" is not the same claim as "does what it
appears to do."

## The fix: decompile the actual jar and read it

`javap` is a JDK-bundled tool that disassembles compiled `.class` files back into a human-readable
signature listing — not source code, but every public method, field, and constructor signature. You
don't need the library's source at all; you need the exact jar that Maven actually resolved onto your
classpath. The workflow used repeatedly in this project:

```bash
# find the exact resolved jar Maven is using
mvn dependency:tree | grep spring-ai-anthropic

# extract and inspect it directly
cd /tmp/some-scratch-dir
jar xf ~/.m2/repository/org/springframework/ai/spring-ai-anthropic/2.0.0/spring-ai-anthropic-2.0.0.jar
javap -p org/springframework/ai/anthropic/Citation.class
```

This gives you ground truth: the exact method signatures that exist, right now, in the exact version
your build actually uses — not what a blog post from eight months ago says, not what your memory of a
different version says, not what an LLM's training data (which has its own cutoff and can blend
details from multiple library versions) suggests.

## A subtlety that bit this project once: stale extraction directories

During the Task 4 redo, a leftover `/tmp/citation-check/extracted` directory from an *earlier*
verification session turned out to contain the **1.1.8** jar's decompiled classes, extracted under a
path that no longer made clear which version it was. Re-using it by habit would have "confirmed" the
wrong thing — silently validating against the old API shape while believing it was the new one. The
practical lesson: **always re-extract fresh from the exact resolved dependency path when verifying an
API**, and don't trust a scratch directory's contents just because it exists — label or delete
verification scratch space once you're done with it, precisely because "it looked already set up" is
exactly the trap.

## Why this is worth doing *before* writing code, not after

The natural instinct is to write the code you believe is correct, run it, and let the compiler or
test failure tell you if you're wrong. That works for typos. It does **not** reliably work for "this
method doesn't exist" versus "this method exists but does something subtly different than you
think" — a method that exists with a slightly different return type, or a builder that requires a
different chaining order, can still compile in some cases (especially with generics and fluent
builders) while behaving differently than assumed. Verifying the API shape *before* writing the
calling code turns "did I get this right?" from a question you answer by trial-and-error debugging
into a question you answer once, directly, with certainty — cheaper every time, and essential when
the library is genuinely still moving (as Spring AI was, through its milestone releases toward 2.0.0
GA).

## When this technique is worth reaching for

Not every API call needs `javap` verification — that would be excessive for well-established,
stable, thoroughly-documented libraries you've used for years. Reach for it specifically when:

- The library is pre-1.0, in active milestone/RC releases, or you know it had a recent major version
  bump (exactly this project's situation with Spring AI's march to 2.0.0 GA).
- You (or an AI assistant helping you) are recalling an API "from memory" rather than reading current
  docs for the *exact* resolved version.
- A previous attempt already got this wrong once — that's a strong signal the API surface is
  genuinely confusing or actively changing, not just a one-off mistake.
- The stakes of being subtly wrong are high — here, a citation-verification security guardrail that
  silently verified nothing would have been much worse than a compile error, because it would have
  looked like it worked.

## Takeaways for your own code

1. "It compiles" and "the documentation/my memory says this exists" are both weaker evidence than
   reading the actual bytecode of the actual resolved dependency.
2. `javap -p <ClassFile>` (or `javap -p -cp path/to.jar package.ClassName` without manually
   extracting) gives you ground truth about public API surface in seconds.
3. When working with a library going through active version churn, verify against the exact version
   in `mvn dependency:tree` — not "the version I remember," not a stale scratch extraction from a
   previous session.
4. If a first attempt at something silently invented a workaround instead of surfacing "this
   doesn't exist as I expected" — that's a signal to discard the attempt entirely and re-verify from
   scratch, not to patch around the workaround.
