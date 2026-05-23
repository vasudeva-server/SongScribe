---
description: Run one test-remediation chunk (auto-commit, then stop)
argument-hint: "[optional: class name or section to target instead of auto-select]"
---

Execute **exactly one** test-remediation chunk, then stop. All state is in the
repo, so this works from any session — derive everything from files, not memory.

**First, read `docs/testing/REMEDIATION.md`** (the settled procedure, decisions,
chunking caps, model policy) and skim `docs/testing/remediation-ledger.md`.

Then run the loop once:

1. **Select the chunk.** If `$ARGUMENTS` names a class/section, use it. Otherwise
   pick the next package with ⬜ rows in risk-order (`dom → io → layout → util →
   action → selection → component → message → renderer → dialog → menu →
   lifecycle → e2e`), and within it the next class (or a group of tiny
   same-source-dir classes) sized to the caps: **7 rows ordinary, 4 rows heavy
   pure-logic** (geometry/MIDI/serialization math), 130K-token worker budget.
   **Skip e2e-level rows** — they are deferred to the final batch; leave them ⬜.
   State which section file + rows the chunk covers.

2. **Delegate to ONE fresh worker** (model: Sonnet by default; **Opus** for heavy
   pure-logic and for `inadequate` weak-but-green rewrites). Its prompt MUST begin
   with `MANDATORY: Read .agents/rules/serena.md`. Give it the exact ⬜ rows, the
   target test file, and instruct it to: read the production class + existing test
   + the testing guides; implement each row's `action` (write `missing`,
   strengthen `inadequate`, relocate `wrong-level`, delete `redundant`); run
   `./scripts/compile.sh` then `./scripts/test.sh <ClassName>` (unit only, never
   e2e); fix failures; return per-row dispositions (`Class.method` →
   keep/modify/add/relocate/remove) and which rows are complete.
   For an oversized single class, continue the SAME worker via SendMessage rather
   than re-spawning.

3. **Integrate** (main session): flip ⬜→✅ on completed rows in the section file;
   append dispositions to `docs/testing/dispositions/*.txt`; run
   `python3 docs/testing/gen_ledger.py`.

4. **Commit & stop.** Verify the worker's tests compiled and passed; if anything
   failed and is unresolved, STOP and report instead of committing. Otherwise
   commit to `develop` via the `/commit-commands:commit` skill (message scope:
   the class/section just remediated). Then STOP and report: rows done this chunk,
   updated ledger total, and the next chunk that `/remediate` will pick up.
