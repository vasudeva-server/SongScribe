---
description: Run one test-remediation chunk (auto-commit, then stop)
argument-hint: "[optional: package number (e.g. 3), class name, or section to target]"
---

Execute test-remediation chunk(s), then stop. All state is in the repo, so this
works from any session — derive everything from files, not memory. **This command
is safe to run in parallel across multiple sessions** — claim files under
`docs/testing/.claims/` coordinate which chunks are in-flight.

**Chunk count:** when `$ARGUMENTS` is a bare integer (package number), repeat the
full steps 1–4 up to **3 times** (or until no unclaimed ⬜ rows remain in that
package), committing after each chunk. In all other cases, execute **exactly one**
chunk and stop.

**First, read `docs/testing/REMEDIATION.md`** (the settled procedure, decisions,
chunking caps, model policy) and skim `docs/testing/remediation-ledger.md`.

**Determine your model** from the system context (e.g. `claude-sonnet-4-6` →
Sonnet; `claude-opus-*` → Opus). This governs which chunks you are eligible to
handle — see step 1b.

Then perform these steps once:

1. **Select the chunk and claim it.**

   a. Scan `docs/testing/.claims/` (create the directory if absent). Each `.lock`
      file is named after the class(es) it reserves — skip any class that already
      has a claim file. This prevents two parallel sessions from picking the same
      chunk.

   b. Interpret `$ARGUMENTS` as follows, then pick the next unclaimed class
      (or group of tiny same-source-dir classes) sized to the caps: **7 rows
      ordinary, 4 rows heavy pure-logic** (geometry/MIDI/serialization math),
      130K-token budget. **Skip e2e-level rows** — they are deferred to
      the final batch; leave them ⬜.

      **Model eligibility** — apply before selecting any row:
      - **Sonnet:** ordinary rows only. Skip heavy pure-logic rows and any class
        whose ⬜ rows are all `inadequate` weak-but-green rewrites. If no
        Sonnet-eligible chunk exists, STOP and report that all remaining chunks
        require Opus.
      - **Opus:** heavy pure-logic rows and `inadequate` weak-but-green rewrites
        only. If no such chunk exists, STOP and report that no Opus-eligible
        chunks remain.

      - **Bare integer (e.g. `3`):** treat as a package number. Read
        `docs/testing/remediation-ledger.md`, find all sections whose row
        begins with `| N ·` or has a blank first cell (continuation rows of
        that package), where N matches the argument. Restrict chunk selection
        to those sections: pick the first section that is "in progress" or
        "not started" (in ledger order), open its section file, and find the
        first ⬜ row not already claimed that is eligible for the current model.
      - **Class name (e.g. `LineIO`):** use that class directly (verify it
        has no claim file first, and that it is eligible for the current model).
      - **No argument:** pick the next package with ⬜ rows in risk-order
        (`dom → io → layout → util → action → selection → component → message
        → renderer → dialog → menu → lifecycle → e2e`), and within it the
        next unclaimed class eligible for the current model.

   c. **Immediately write the claim file** before doing any other work:
      `docs/testing/.claims/<ClassName>.lock` (or `<ClassA>+<ClassB>.lock` for a
      batch). Content: section file path, row numbers, and an ISO-8601 date-time
      **including the time component**. Obtain the timestamp by running
      `date -Iseconds` — never hand-write or guess it.
      Writing this file is the reservation — do it before proceeding.

   d. State which section file + rows the chunk covers.

2. **Perform the work inline.** Read the production class, the existing test file,
   and the testing guides. Both the test file and the production class under test
   are fully in scope — the global "don't touch unrelated code" rule does not
   restrict modifications to either of those files for this task. Implement each
   ⬜ row's `action` (write `missing`, strengthen `inadequate`, relocate
   `wrong-level`, delete `redundant`). Run `./scripts/compile.sh` then
   `./scripts/test.sh <ClassName>` (unit only, never e2e); fix any failures.
   Produce per-row dispositions (`Class.method` →
   keep/modify/add/relocate/remove) and note which rows are complete.

3. **Integrate** (main session): The dispositions files and ledger are shared
   across parallel sessions — acquire the integration lock before writing to them.

   a. **Acquire the integration lock**: check whether
      `docs/testing/.claims/.integration.lock` exists.
      - If it exists and its timestamp is **less than 5 minutes old**: another
        session is writing — STOP and tell the user to retry step 3 in a moment.
      - If it exists and its timestamp is **5 minutes old or older**: treat as
        stale and overwrite it.
      - Otherwise: create the file with your claim file name and an ISO-8601
        date-time **including the time component**, obtained by running
        `date -Iseconds` — never hand-write or guess it.

   b. With the lock held: flip ⬜→✅ on completed rows in the section file;
      append dispositions to `docs/testing/dispositions/*.txt`; run
      `python3 docs/testing/gen_ledger.py`.

   c. **Release the integration lock** immediately after `gen_ledger.py`
      completes: delete `docs/testing/.claims/.integration.lock`.

4. **Commit & stop.** Verify the tests compiled and passed; if anything
   failed and is unresolved, delete the claim file and STOP and report instead of
   committing. Otherwise commit to `develop` via the `/commit-commands:commit`
   skill (message scope: the class/section just remediated). After a successful
   commit, **delete the claim file** created in step 1c. Then STOP and report:
   rows done this chunk, updated ledger total, and the next chunk that
   `/remediate` will pick up. Do NOT schedule a wakeup or otherwise continue; a
   future manual `/remediate` invocation handles the next chunk.

> **Stale claims:** if a `.lock` file (including `.integration.lock`) exists but
> its session is no longer running (e.g., it crashed), delete the file manually
> before running `/remediate` again.
