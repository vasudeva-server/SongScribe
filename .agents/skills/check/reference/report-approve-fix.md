# Report, Approve, Fix

Wait for every launched agent and phase to complete, then choose the path based
on whether `--fix` was in `$ARGUMENTS`.

**If any test is going to be written or edited**, read
`.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` in full
before the first edit — once, not once per test — even if the Test Quality
Principles section was already skimmed for the audit. Writing tests requires the
whole guide's conventions: naming, structure, fixtures, assertion style. **If
any test being written or edited is an e2e test** (extends `E2ETest`, lives
under `src/test/java/songscribe/e2e/`), also read
`.agents/guides/testing-e2e.md` in full before touching it — on top of, not
instead of, the two guides above.

## Path A: `--fix` mode

1. **Design findings are the one exception to `--fix`.** Before changing any
   code, take every design-level finding — from the Architecture agent, the
   Testability and Design agent, or any other agent that handed a symptom up —
   and present it for approval via AskUserQuestion, per *Approval* in
   `design-flaws.md`: the flaw, the corrected design, what the change touches,
   what it costs to leave alone, and which tests it simplifies or removes. `--fix` means
   "don't pester me about ordinary fixes"; it does not authorize restructuring
   the user's architecture unattended. If there are no design findings, skip
   straight to step 2.
2. **Fix every remaining finding immediately** — every finding from every agent
   and every phase that ran, including minor and low-confidence ones, **and
   including findings in code outside the review target** (see *Findings Outside
   the Review Target* in `findings.md`). Do not ask any questions about these.
   Apply approved design fixes here too, along with the test changes they
   enable.
3. **Do not paper over a declined design flaw.** If the user declined the
   structural fix, apply only the fixes that stand on their own. Where the only
   available fix would have been a workaround around the declined flaw — another
   parameter or cache in production code, another mock or test-only accessor in
   a test — leave it and say so in the summary rather than adding debt.
4. **Compile and test.** Any change under `src/main/` or `src/test/` requires
   `./scripts/compile.sh`; then re-run the relevant `./scripts/test.sh <target>`
   (unit only) to confirm green.
5. **Summarize** what was fixed, leading with the design change if one was
   approved, then grouped by axis. Write the summary in plain language per
   `findings.md` — for each fix, say what the code or test did before, what it
   does now, and what that means in practice.

## Path B: Interactive mode (default)

1. **Write findings** to `plans/findings.md`, overwriting any existing content,
   in a single organized document, in this order:

   - any concrete **production bug**, first;
   - **design findings**, each followed by the symptoms and tests it explains,
     so the reader sees one decision rather than a list of unrelated patches;
   - the remaining findings, grouped by axis — Reuse, Quality, Efficiency, and
     (when the test axes ran) Correctness → Usefulness → Coverage, each test
     finding with file:line, the bug it misses, and confidence. Within those,
     put first the findings where a test passes while the code could be broken.

   Where a symptom's only standalone fix would be a workaround around a design
   flaw you reported, say so where you list it. The reader needs to know which
   fixes become pointless if they approve the structural change, and which of
   them add debt if they don't.

   Rewrite every agent finding in your own words before writing it. Do not pass
   an agent's text through untouched — the agents write for other agents; you
   write for a person who has not read the code or the tests. Apply
   `findings.md` to each one: where it is, what the code does now, what's wrong
   in plain terms, and what you would change. If you cannot explain a finding
   plainly, you do not understand it well enough to report it — either dig in
   until you can, or drop it.

   Once the file is written, tell the user it's ready at `plans/findings.md`
   and move straight into clarifying questions below — do not also paste the
   findings into chat.

2. **Clarifying questions.** If any finding needs clarification — ambiguous code
   intent, unclear whether something is intentional, a test that never really
   checks anything but might be a deliberate "does it blow up?" smoke test — ask
   via AskUserQuestion. Give the background in the question text itself: what
   the code or test does, what looked off, and what each answer would cause you
   to do. The options must be understandable without looking at the code.

   **Check every question against *Never offer a menu of workarounds* in
   `design-flaws.md` before you send it.** If you cannot point to one option
   that leaves the code in a state you would defend, do not ask — go work out
   the design and come back with a finding. A question of the form "which of
   these two places should own X" is the shape this fails in most often: answer
   "does X belong in either, in this form?" first.

3. **Questionable findings.** For any finding you believe is a false positive or
   not worth addressing, present it via AskUserQuestion and ask whether the user
   wants it fixed anyway, saying plainly why you think it is not worth acting
   on. Do not silently skip findings. **"It's outside the review target" is not
   a reason to put a finding here** — see *Findings Outside the Review Target* —
   and neither is **"the fix would be a big change"** or **"the fix would mean
   changing production code"**; see `design-flaws.md`. This step is for findings
   you believe are **wrong**, not for real defects you'd rather not touch.

4. **Approval.** Once all questions are resolved, use AskUserQuestion to present
   the final list of issues to fix and ask for approval to proceed (or for
   further discussion). Describe each item in one plain sentence naming the
   actual change, not a category label. Findings in code outside the review
   target belong in this list on equal footing with the rest.

   **Put each design finding as its own decision**, not as one line item among
   the small fixes — it has a different cost and a different payoff, and burying
   it in a list of tidy-ups is a way of discouraging it. State the corrected
   design, what the change touches, and which tests it removes in the question,
   so the choice can be made without reopening the files.

5. **Fix.** After approval, fix the approved issues, following the testing
   guides read at the start of this phase for any test work. If a design fix was
   declined, apply only the fixes that stand on their own and say which ones you
   left undone because their only fix would have been a workaround. Any change
   under `src/main/` or `src/test/` requires `./scripts/compile.sh`; then re-run
   the relevant `./scripts/test.sh <target>` (unit only) to confirm green.
   Briefly summarize what changed, in the same plain language.
