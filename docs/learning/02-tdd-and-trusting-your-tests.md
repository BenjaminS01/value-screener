# TDD and Trusting Your Tests

**Where this comes from:** Task 3 of the Company Research Agent build — `ResearchPromptBuilder`, and
a real, small bug that made it all the way from the implementation plan into a passing test suite.

**Level:** junior → mid-level. Assumes you've heard of TDD; this is about a subtlety that basic TDD
tutorials rarely cover.

## The RED → GREEN discipline, briefly

Test-Driven Development's core loop: write a failing test first (RED), confirm it fails for the
*right reason* (not a typo — the feature genuinely doesn't exist yet), then write the minimum code
to make it pass (GREEN). In this project, "RED for the right reason" was checked explicitly: when
`ResearchPromptBuilderTest` was written before `ResearchPromptBuilder` existed, running the suite
gave a **compilation error** (`cannot find symbol: class ResearchPromptBuilder`) — not a runtime
assertion failure. That's the correct RED: it proves the test is exercising code that genuinely
doesn't exist, not failing because of some unrelated typo in the test itself.

## What actually happened here — a bug in the *plan*, caught by the test

The implementation task came with a written brief specifying exact prompt text the model should
receive, including this guardrail sentence:

> Write in a descriptive, analytical tone. never phrase findings as a recommendation.

Note the lowercase "never" — sentence-initial, but not capitalized. This was a genuine typo in the
brief, not something introduced during implementation. The corresponding test asserted:

```java
assertThat(prompt).contains("never phrase findings as a recommendation");
```

— matching the (wrong) lowercase text exactly.

The first implementation pass did what a lot of developers instinctively do under time pressure:
**it changed the code to match the test**, rather than questioning whether the test's expectation was
itself correct. Lowercasing "never" in the production prompt made the test pass — but it introduced a
real grammar bug into text that gets sent to Claude as an actual system instruction: a sentence
literally starting with a lowercase letter.

This was caught during code review, not by the test suite — the test suite was green the whole time.
**That's the point of this story.** A passing test tells you "the code does what the test expects." It
tells you nothing about whether the test's expectation was the right one to encode in the first
place.

## The fix, and the principle behind it

The correct fix went the other direction: keep the production prompt grammatically correct
(`"Never phrase findings..."`, capitalized), and **fix the test's assertion** to match the correct
text, not the buggy one:

```java
// before (wrong — encodes the typo as if it were a requirement)
assertThat(prompt).contains("never phrase findings as a recommendation");

// after (right — encodes the actual intended behavior)
assertThat(prompt).contains("Never phrase findings as a recommendation");
```

Same number of lines changed either way. The difference is entirely about **which artifact you treat
as the source of truth**. In TDD, the slogan "the test is the contract" is usually good advice — it
stops you from second-guessing a well-designed test just because the implementation is inconvenient.
But it has a hidden assumption: that the test itself was written correctly. When a test's expectation
literally contradicts an external fact (here: English grammar — sentences start with a capital
letter, full stop, no exceptions), that assumption breaks, and "make the test pass" is no longer
automatically the right instinct.

## How to tell the difference in your own work

Ask, whenever you're about to change *code* to satisfy a *test* that feels slightly off:

1. **Is the test encoding a genuine requirement, or an accident?** A requirement is something a
   product owner, a spec, or an external fact (grammar, a regulation, a documented API contract)
   actually demands. An accident is a typo, a copy-paste slip, or an assumption nobody actually
   verified.
2. **If I "fix" the code instead of the test, does it introduce something a domain expert (or, here,
   an English speaker) would immediately flag as wrong?** If yes, that's a strong signal the test is
   the thing that needs fixing, not the code.
3. **When genuinely unsure, that uncertainty itself is worth surfacing** — in this project, that
   meant flagging the discrepancy explicitly during code review rather than silently picking a
   direction and moving on, precisely because a wrong guess here would have shipped a grammatically
   broken system prompt to a paid AI API call, silently, forever.

## Takeaways for your own code

1. A green test suite proves internal consistency (code matches test), not correctness of intent —
   those are different claims, and conflating them is one of the most common false-confidence traps
   in software.
2. When a test and an obviously-true external fact disagree, the test is probably wrong — don't
   default to "the test is the contract" so hard that you ship a bug to satisfy it.
3. Code review exists partly to catch exactly this class of bug — one where every automated signal
   (compiler, test runner) says "fine," and only a human (or a second, skeptical pass) notices
   something is semantically off.
4. When you do find this kind of bug, fix it **and** update whatever source document produced the
   error (here, the implementation plan) so the same wrong wording doesn't get copied into future
   work.
