# Real-World AI Cost Control (and the Limits of Client-Side Cancellation)

**Where this comes from:** the same-day follow-up to Task 7 — after a real live call took over 55
seconds and cost more than $1, plus a client-side MCP Inspector timeout error.

**Level:** mid-level. Assumes you've called a paid LLM API before; this is about two specific,
transferable lessons on cost and cancellation.

## Root cause #1: an unbounded tool means an unbounded bill

`AnthropicWebSearchTool.builder()` had been used without ever setting `maxUses(...)` — meaning the
model was free to issue as many web searches as it wanted per single call, with no ceiling. Every
search round adds both latency and token cost (each round's results get fed back into the model's
context). Verified via `javap` that the builder does in fact support a `maxUses(long)` method; the fix
was to actually call it:

```java
AnthropicWebSearchTool.builder()
    .maxUses(webSearchMaxUses) // @Value("${research.agent.web-search-max-uses:5}")
    .build()
```

The general principle, worth internalizing for any tool-using AI integration you build: **giving a
model an autonomous tool (web search, code execution, function calling in a loop) without an explicit
usage ceiling means the model — not you — decides how much of that tool to use, and therefore, how
much you pay.** Always ask, for any tool you attach to a model call: "does this tool have a built-in
usage cap, and have I actually set it, or am I trusting the model's own judgment about when to stop?"
A configurable ceiling (here, `research.agent.web-search-max-uses`, defaulting to 5) turns an
open-ended cost into a bounded, predictable one — you may still want to tune the exact number later
(this project's own design docs note the cap might be too low for companies with unusual fiscal-year
reporting, requiring more search rounds to disambiguate — see the screening-cost-redesign spec's
Section 7 for how this was later addressed), but a *bounded, tunable* cost is a fundamentally
different risk profile than an *unbounded* one.

## Root cause #2: a client-side timeout does not cancel the underlying request

Separately, the same investigation confirmed something worth knowing deeply, not just as a one-off
fact: the `future.cancel(true)` call inside the `TimeoutException` branch of `callWithTimeout(...)`
(see `07-timeouts-and-virtual-threads.md`) does **not** actually stop the real, already-in-flight,
already-billed Anthropic API request. Confirmed via `mvn dependency:tree` that Spring AI's Anthropic
client sits on top of OkHttp 4.12.0, and that `Thread.interrupt()` — which is what
`Future.cancel(true)` ultimately relies on to signal cancellation — is simply **ignored by a plain
blocking socket read**. The thread that's actually waiting on the network response doesn't respond to
interruption; it just keeps waiting until the real response arrives or the OS-level socket itself
times out, wholly independent of whether your application gave up waiting on the `Future` a while ago.

Genuinely cancelling the request would require reaching past Spring AI's `ChatModel` abstraction
entirely and manipulating the underlying `okhttp3.Call` object directly (which would call `.cancel()`
on the actual HTTP call) — but Spring AI's `ChatModel` interface never exposes that object to calling
code, by design, since it's meant to abstract away the specific HTTP client. This project judged
actually plumbing through real cancellation to be out of scope for now, and instead added an explicit
`slf4j` warning log on timeout, stating plainly that the underlying request may still be running (and
still being billed) even though the application has already given up and returned an error to its own
caller — **making the limitation visible and honest, rather than silently misleading**.

## Why "honest logging over false confidence" matters here specifically

It would have been easy to simply let the timeout path look identical to a real, complete
cancellation from the outside — the caller gets an error either way, so why would it matter? It
matters because whoever operates this system (here: the same user tracking a real dollar-cost problem
across this whole sub-project) needs to know that **a timeout on their side is not evidence the
Anthropic API call actually stopped, or that they weren't billed for it.** Silently treating "we gave
up waiting" as equivalent to "the operation was cancelled" would have actively hidden the exact kind
of cost information this project was built to surface (see the sibling investigation in
`docs/superpowers/specs/2026-07-30-screening-cost-redesign-design.md`, which exists precisely because
a real call cost about $1). A limitation you can't fix immediately is still worth logging honestly,
specifically so it doesn't get mistaken for something it isn't.

## The general pattern: distinguish "my code gave up" from "the operation stopped"

This is worth generalizing well beyond LLM calls: any time your code uses a timeout/cancellation
mechanism that operates on your *own* thread or future (rather than reaching into the actual
underlying I/O operation), ask explicitly: **does this actually stop the remote operation, or does it
only stop my own thread from waiting on it?** The two are easy to conflate because they produce the
same visible symptom on your side (an exception, an error response) — but they have very different
real-world consequences when the remote operation has side effects (cost, a partially-completed
write, a queued job) that outlive your own thread's patience.

## Takeaways for your own code

1. Any autonomous tool you give an LLM (web search, code execution, repeated function calls) needs an
   explicit, configurable usage ceiling — an unbounded tool means the model, not you, controls your
   bill.
2. `Future.cancel(true)` relies on `Thread.interrupt()`, which plain blocking I/O (like a classic
   socket read underneath most HTTP clients) generally ignores — a client-side timeout very often
   does not cancel the real remote operation, only your own wait for its result.
3. When you can't fix a limitation like this immediately (here: true cancellation would require
   bypassing an abstraction layer the project deliberately doesn't want to bypass), log it explicitly
   and honestly rather than letting the symptom look identical to a real fix — operators need to know
   what actually happened, especially where cost or side effects are involved.
4. Verify library internals (here: which HTTP client sits underneath an abstraction, and how it
   handles interruption) via the dependency tree and decompilation, the same discipline covered in
   `04-verifying-apis-with-javap-instead-of-guessing.md` — this bug was found the same way, not by
   guessing.
