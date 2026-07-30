# Timeouts and Virtual Threads

**Where this comes from:** Task 5 — bounding how long `CompanyResearchAgent` will wait for a Claude
API call before giving up.

**Level:** mid-level. Assumes basic `Thread`/concurrency familiarity; introduces virtual threads
(Java 21+) and the `CompletableFuture` timeout pattern together, since this project uses them
combined.

## The problem: a blocking call with no time limit

`chatModel.call(prompt)` is a synchronous, blocking call to the Anthropic API — it returns whenever
the model finishes (which, with a hosted web-search tool attached, can vary a lot: seconds for a
simple case, over a minute if the model does several search rounds). Without any bound, a single slow
or stuck request could hang the calling thread indefinitely — bad for a server that's supposed to
respond to an MCP tool call within a reasonable time, and directly costly here, since a longer call
is also a more expensive one (see `10-real-world-ai-cost-control.md` in this folder for the cost side
of this same story).

## The pattern: run the blocking call on another thread, bound the wait with `.get(timeout, unit)`

```java
Executor executor = Executors.newVirtualThreadPerTaskExecutor();

CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(
    () -> chatModel.call(prompt),
    executor
);

try {
    return future.get(timeoutSeconds, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    throw new ResearchTimeoutException("Research call exceeded " + timeoutSeconds + "s", e);
} catch (InterruptedException | ExecutionException e) {
    throw new ResearchTimeoutException("Research call failed", e);
}
```

The core idea: you can't put a timeout directly on a plain blocking method call — `chatModel.call(..)`
itself has no timeout parameter, and even if it did, that would depend on the library's own internal
implementation. What you *can* do is: run the blocking call on a separate thread (wrapped in a
`CompletableFuture`), and then bound **your own wait** for that future's result with
`future.get(timeoutSeconds, TimeUnit.SECONDS)`. If the underlying call hasn't finished by the
deadline, `get(...)` throws `TimeoutException` on the calling thread — even though the actual
`chatModel.call(...)` work may *still be running in the background*, unaware that the caller gave up
waiting on it (an important limitation covered in `10-real-world-ai-cost-control.md`: this pattern
stops *you* from waiting forever, but it does not actually cancel the underlying network request).

## Why virtual threads, specifically

`Executors.newVirtualThreadPerTaskExecutor()` is a Java 21+ API. Before virtual threads, a
long-blocking-call-per-request pattern like this one would typically use a bounded thread pool
(`Executors.newFixedThreadPool(n)`), because traditional OS threads are expensive (each one reserves
real OS resources, typically ~1MB of stack), so you can only afford a limited number running at once.

Virtual threads are dramatically cheaper — the JVM can run millions of them, because they're
scheduled by the JVM itself onto a much smaller number of real OS ("carrier") threads, and a virtual
thread that's blocked (like ours, waiting on a network response) doesn't tie up a carrier thread while
idle. `newVirtualThreadPerTaskExecutor()` creates a genuinely new virtual thread for every submitted
task rather than pooling threads — with virtual threads, that's cheap enough to be the *idiomatic*
choice, whereas doing the same thing with platform threads would risk exhausting real OS threads
under load. For a per-request "make one blocking outbound call, wait, respond" pattern like a company
research request, this is close to the textbook use case virtual threads were designed for.

## Wrapping checked exceptions into one unchecked type

`future.get(...)` can throw three different checked exceptions (`TimeoutException`,
`InterruptedException`, `ExecutionException`), each with different meanings (deadline exceeded,
thread interrupted, the async task itself threw). This project's `CompanyResearchAgent` collapses all
three into a single unchecked `ResearchTimeoutException`. This is a deliberate simplification, not
laziness: the *caller* of `research(...)` (ultimately, the MCP tool layer — see
`08-building-an-mcp-tool-with-spring-ai.md`) doesn't need to distinguish "the deadline passed" from
"the async plumbing itself failed" — from the caller's perspective, both mean "the research didn't
complete, handle it as a failure" the same way. Collapsing several checked exceptions into one
unchecked type at a clear boundary (here: the boundary between "internal async machinery" and
"business-level agent API") is a common, deliberate simplification — just be sure, as this project's
own code review explicitly checked, that you're not silently discarding information a caller would
actually need to act differently on.

## A known, accepted limitation: the executor is never explicitly shut down

Code review on this task flagged, as a non-blocking note, that the per-instance virtual-thread
executor is never explicitly shut down. This is fine here specifically because: (a) the production
instance is a Spring singleton that lives for the entire application's lifetime (there's nothing to
shut down until the whole app stops, at which point the JVM exiting cleans everything up anyway), and
(b) test-constructed instances use non-blocking virtual threads that don't prevent JVM exit even if
never explicitly closed. This is a good example of a finding that's *technically true* (the executor
is never shut down) but *not worth fixing* given the actual lifecycle of the object — recognizing
that distinction (real issue vs. theoretically-true-but-inconsequential) is itself a skill worth
building, rather than reflexively "fixing" every finding a reviewer or static analyzer surfaces.

## Takeaways for your own code

1. You can't add a timeout parameter to an API that doesn't expose one — instead, run the blocking
   call asynchronously and bound *your own wait* for its result.
2. For per-request blocking I/O work (network calls, in particular), prefer
   `Executors.newVirtualThreadPerTaskExecutor()` over a manually-sized platform-thread pool on Java
   21+ — it removes an entire class of "how big should this pool be" tuning decisions.
3. `future.get(timeout, unit)` stopping *your* wait is not the same as cancelling the underlying work
   — know this distinction before you rely on a timeout to actually save cost or stop side effects
   (see `10-real-world-ai-cost-control.md`).
4. Collapsing multiple checked exceptions into one unchecked type at a clear architectural boundary is
   a legitimate simplification — just verify the callers genuinely don't need the finer-grained
   distinction before doing it.
5. Not every code-review finding needs a fix — some are true-but-inconsequential given the object's
   real lifecycle; learning to tell the two apart is as important as learning to spot the findings in
   the first place.
