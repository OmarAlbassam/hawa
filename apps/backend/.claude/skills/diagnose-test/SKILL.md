---
name: diagnose-test
description: Diagnose why a test is failing without modifying the test. Use this skill whenever the user reports a failing test, shares a stack trace or test error, or asks to investigate, debug, or understand a test failure. Triggers on phrases like "why is this test failing", "this test is red", "figure out this test", "debug this test", "this assertion is failing", "tests are broken", or any time the user pastes failing test output and wants a root cause. Enforces a test-is-spec, implementation-is-suspect workflow that forbids editing test files and requires a written root-cause diagnosis before any code change.
---

# Diagnose Test

Find the real reason a test is failing. The test is the specification. The implementation is the suspect. Your job is to produce a written root-cause diagnosis — not to make the test green.

## Prime directive

**Never modify, delete, skip, comment out, weaken, or "adjust" any test file, assertion, fixture, or mock in this task.** If you find yourself wanting to edit a test, that is the signal to stop and escalate to the user. "It should work this way anyway" is not a justification.

Assume the test is correct until proven otherwise with external evidence (a spec, an issue, a docstring, another passing test). Your own opinion about what the behavior "should" be is not evidence.

## Workflow

Do these steps in order. Do not skip. Do not start editing code mid-investigation. Do not speedrun to a fix.

### 1. Reproduce

Run only the failing test in isolation. Paste:

- The exact command used.
- The exact, unedited failure output (copy-paste, not summarized).

If the test can't be reproduced on the first run, run it twice more before moving on. If it only fails intermittently, jump to step 5c.

### 2. Read the contract

Read the failing test end to end. In your own words, state:

- What behavior is this test asserting?
- What are the inputs, expected outputs, and preconditions (fixtures, setup, mocks)?
- Which specific assertion is failing? Quote the exact line.

If the test is unclear or you can't tell what it's asserting, say so and stop. Don't guess at intent.

### 3. Trace the implementation

Follow the code path from the test's entry point to the failing assertion. At each meaningful step, state what the code *actually* does versus what the test *expects*. Identify the exact line(s) where actual behavior diverges from expected.

Include in your report:

- The exact implementation line(s) where divergence occurs (file path + line number + the line itself).
- Any relevant state at that point (variable values, returned objects, side effects) if you can determine them from the trace or logs.

Report what you observe, not what you expect to observe. If something surprises you, say so explicitly.

### 4. Root cause

State the root cause in one or two sentences. Not "the assertion is false" — the underlying logic bug that makes the assertion false.

If you cannot identify a root cause with evidence, say so. Do not guess. "I'm not sure, here are the two possibilities and what I'd need to check to decide" is a valid answer. Inventing a confident-sounding cause is not.

### 5. Verdict

Pick exactly one and justify it:

**a) Implementation bug** — the code is wrong, the test is right.
- Describe the minimal fix in words. Do not apply it.
- Explain why this fix addresses the root cause and not just the symptom.

**b) Test is genuinely wrong** — the test encodes a requirement that contradicts the actual spec.
- You must cite external evidence: a spec document, an issue, a docstring, a comment, another passing test that proves the intended behavior, or explicit user confirmation.
- "The current implementation works differently" is circular and not evidence.
- "It makes more sense this way" is an opinion and not evidence.
- If you pick (b), **stop and wait for the user to confirm before changing anything**.

**c) Environmental / flaky / infrastructure** — the test and code are both correct, but something else is interfering.
- Reproduce the failure at least twice and show the evidence (timing, test ordering, shared state, external dependency, uncleaned fixtures, race condition, env var, clock, network).
- Identify the specific interference, not just "it's flaky."

### 6. Stop

Show the user the full report. Do not edit code. Do not edit tests. Wait for their go-ahead before making any changes.

## Report format

Structure your final output like this so the user can scan it fast:

```
## Reproduction
<command + raw output>

## What the test asserts
<1–3 sentences on the contract + quoted failing assertion>

## Trace
<step-by-step divergence, with file:line references>

## Root cause
<1–2 sentences>

## Verdict
<a / b / c + justification>

## Proposed next step
<what you'd do, NOT applied yet>
```

## Hard rules

- No test files touched. Not assertions, not fixtures, not mocks, not setup/teardown, not skip decorators, not `@pytest.mark.xfail`, not expected values in data files the tests read from.
- No "while I'm here" edits to unrelated code.
- No applying a fix before the user approves the verdict.
- No summarizing observed output — paste it raw.
- No confident guessing. If evidence is thin, say the evidence is thin.
- If the user pushes back on the diagnosis, re-investigate. Do not cave and start editing the test to end the conversation.

## Anti-patterns to avoid

These are the specific failure modes this skill exists to prevent. If you catch yourself doing any of these, stop:

- Loosening an assertion (`assertEqual` → `assertIn`, exact match → regex, specific value → truthy check).
- Changing an expected value in the test to match what the code currently returns.
- Wrapping the failing line in try/except or adding a skip.
- Rewriting a mock to return whatever the code happens to pass it.
- "Fixing" the test's setup to avoid triggering the bug.
- Declaring the test "outdated" without external evidence.
- Jumping to step 5 without doing steps 1–4.
- Reading one file and guessing the rest of the trace.