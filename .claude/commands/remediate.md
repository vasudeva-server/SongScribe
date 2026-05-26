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

Then perform these steps once:

1. **Select the chunk and claim it.**

   a. Scan `docs/testing/.claims/` (create the directory if absent). Each `.lock`
      file is named after the class(es) it reserves — skip any class that already
      has a claim file. This prevents two parallel sessions from picking the same
      chunk.

   b. Interpret `$ARGUMENTS` as follows, then pick the next unclaimed class
      (or group of tiny same-source-dir classes) sized to the caps: **7 rows
      ordinary, 4 rows heavy pure-logic** (geometry/MIDI/serialization math),
      130K-token worker budget. **Skip e2e-level rows** — they are deferred to
      the final batch; leave them ⬜.

      - **Bare integer (e.g. `3`):** treat as a package number. Read
        `docs/testing/remediation-ledger.md`, find all sections whose row
        begins with `| N ·` or has a blank first cell (continuation rows of
        that package), where N matches the argument. Restrict chunk selection
        to those sections: pick the first section that is "in progress" or
        "not started" (in ledger order), open its section file, and find the
        first ⬜ row not already claimed.
      - **Class name (e.g. `LineIO`):** use that class directly (verify it
        has no claim file first).
      - **No argument:** pick the next package with ⬜ rows in risk-order
        (`dom → io → layout → util → action → selection → component → message
        → renderer → dialog → menu → lifecycle → e2e`), and within it the
        next unclaimed class.

   c. **Immediately write the claim file** before doing any other work:
      `docs/testing/.claims/<ClassName>.lock` (or `<ClassA>+<ClassB>.lock` for a
      batch). Content: section file path, row numbers, and ISO-8601 timestamp.
      Writing this file is the reservation — do it before spawning the worker.

   d. State which section file + rows the chunk covers.

2. **Delegate to ONE fresh worker** (model: Sonnet by default; **Opus** for heavy
   pure-logic and for `inadequate` weak-but-green rewrites). Its prompt MUST begin
   with `MANDATORY: Read .agents/rules/serena.md`.
   Give it the exact ⬜ rows, the
   target test file, and instruct it to: read the production class + existing test
   + the testing guides; implement each row's `action` (write `missing`,
   strengthen `inadequate`, relocate `wrong-level`, delete `redundant`); run
   `./scripts/compile.sh` then `./scripts/test.sh <ClassName>` (unit only, never
   e2e); fix failures; return per-row dispositions (`Class.method` →
   keep/modify/add/relocate/remove) and which rows are complete.
   For an oversized single class, continue the SAME worker via SendMessage rather
   than re-spawning.

3. **Integrate** (main session): The dispositions files and ledger are shared
   across parallel sessions — acquire the integration lock before writing to them.

   a. **Acquire the integration lock**: check whether
      `docs/testing/.claims/.integration.lock` exists.
      - If it exists and its timestamp is **less than 5 minutes old**: another
        session is writing — STOP and tell the user to retry step 3 in a moment.
      - If it exists and its timestamp is **5 minutes old or older**: treat as
        stale and overwrite it.
      - Otherwise: create the file with your claim file name and an ISO-8601
        timestamp.

   b. With the lock held: flip ⬜→✅ on completed rows in the section file;
      append dispositions to `docs/testing/dispositions/*.txt`; run
      `python3 docs/testing/gen_ledger.py`.

   c. **Release the integration lock** immediately after `gen_ledger.py`
      completes: delete `docs/testing/.claims/.integration.lock`.

4. **Commit & stop.** Verify the worker's tests compiled and passed; if anything
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
