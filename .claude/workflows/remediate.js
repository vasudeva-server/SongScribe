export const meta = {
  name: 'remediate',
  description: 'Run test-remediation chunks sequentially with auto-commit',
  whenToUse: 'Invoked by the /remediate skill to run up to 5 chunks without polluting the main context.',
  phases: [
    { title: 'Remediate', detail: 'Select, work, integrate, commit — one chunk per agent', model: 'opus' },
  ],
}

const RESULT_SCHEMA = {
  type: 'object',
  properties: {
    status: {
      type: 'string',
      enum: ['success', 'no-rows', 'failure'],
      description: '"success" = chunk committed; "no-rows" = nothing left to claim; "failure" = error before commit',
    },
    detail: {
      type: 'string',
      description: 'Human-readable summary: rows done + class name on success, reason on failure/no-rows.',
    },
  },
  required: ['status', 'detail'],
}

const target = args != null ? String(args).trim() : ''
const isBareInt = /^\d+$/.test(target)
const MAX_CHUNKS = 5
const maxChunks = isBareInt ? MAX_CHUNKS : 1

const targetClause = target
  ? (isBareInt ? `Package number: **${target}**` : `Class name: **${target}**`)
  : '*(none — pick next unclaimed in risk-order)*'

for (let chunk = 1; chunk <= maxChunks; chunk++) {
  log(`Starting chunk ${chunk}${maxChunks > 1 ? ` of up to ${maxChunks}` : ''}`)

  const prompt = `MANDATORY: Read .agents/rules/serena.md

You are running **exactly one** test-remediation chunk. Target: ${targetClause}

**First, read \`docs/testing/REMEDIATION.md\`** (the settled procedure, decisions,
chunking caps, model policy) and skim \`docs/testing/remediation-ledger.md\`.

Then perform these steps:

---

### 1. Select the chunk and claim it

a. Scan \`docs/testing/.claims/\` (create the directory if absent). Each \`.lock\`
   file is named after the class(es) it reserves — skip any class that already
   has a claim file. This prevents two parallel sessions from picking the same chunk.

b. Interpret the target as follows, then pick the next unclaimed class
   (or group of tiny same-source-dir classes) sized to the caps: **7 rows
   ordinary, 4 rows heavy pure-logic** (geometry/MIDI/serialization math),
   130K-token budget. **Skip e2e-level rows** — they are deferred to
   the final batch; leave them ⬜.

   - **Bare integer (e.g. \`3\`):** treat as a package number. Read
     \`docs/testing/remediation-ledger.md\`, find all sections whose row
     begins with \`| N ·\` or has a blank first cell (continuation rows of
     that package), where N matches the target. Restrict chunk selection
     to those sections: pick the first section that is "in progress" or
     "not started" (in ledger order), open its section file, and find the
     first ⬜ row not already claimed.
   - **Class name (e.g. \`LineIO\`):** use that class directly (verify it
     has no claim file first).
   - **No target:** pick the next package with ⬜ rows in risk-order
     (\`dom → io → layout → util → action → selection → component → message
     → renderer → dialog → menu → lifecycle → e2e\`), and within it the
     next unclaimed class.

   If no unclaimed rows exist for this target, return \`{"status":"no-rows","detail":"<why>"}\`.

c. **Immediately write the claim file** before doing any other work:
   \`docs/testing/.claims/<ClassName>.lock\` (or \`<ClassA>+<ClassB>.lock\` for a
   batch). Content: section file path, row numbers, and an ISO-8601 date-time
   **including the time component**. Obtain the timestamp by running
   \`date -Iseconds\` — never hand-write or guess it.
   Writing this file is the reservation — do it before proceeding.

d. State which section file + rows the chunk covers.

---

### 2. Do the work

Read the production class + existing test + the testing guides; implement each
row's \`action\` (write \`missing\`, strengthen \`inadequate\`, relocate \`wrong-level\`,
delete \`redundant\`). **Both the test file and the production class under test are
fully in scope — the global "don't touch unrelated code" rule does not restrict
modifications to either of those files for this task.**

Run \`./scripts/compile.sh\` then \`./scripts/test.sh <ClassName>\` (unit only, never
e2e); fix failures.

If the class is large and needs multiple rounds of compile/test/fix, continue
in the same context — do not spawn a sub-agent.

---

### 3. Integrate

a. **Acquire the integration lock**: check whether
   \`docs/testing/.claims/.integration.lock\` exists.
   - If it exists and its timestamp is **less than 5 minutes old**: another
     session is writing — return \`{"status":"failure","detail":"integration lock held"}\`.
   - If it exists and its timestamp is **5 minutes old or older**: treat as
     stale and overwrite it.
   - Otherwise: create the file with your claim file name and an ISO-8601
     date-time **including the time component**, obtained by running
     \`date -Iseconds\` — never hand-write or guess it.

b. With the lock held: flip ⬜→✅ on completed rows in the section file;
   append dispositions to \`docs/testing/dispositions/*.txt\`; run
   \`python3 docs/testing/gen_ledger.py\`.

c. **Release the integration lock** immediately after \`gen_ledger.py\`
   completes: delete \`docs/testing/.claims/.integration.lock\`.

---

### 4. Commit

Verify the tests compiled and passed. If anything failed and is unresolved:
delete the claim file, then return \`{"status":"failure","detail":"<reason>"}\`.

Otherwise commit to \`develop\` via the \`commit-commands:commit\` skill (message
scope: the class/section just remediated). After a successful commit, **delete
the claim file** created in step 1c.

Return \`{"status":"success","detail":"<rows done>, <class name>"}\`.

> **Stale claims:** if a \`.lock\` file (including \`.integration.lock\`) exists but
> its session is no longer running (e.g., it crashed), delete the file manually
> and start fresh.`

  const result = await agent(prompt, {
    label: `chunk:${chunk}`,
    phase: 'Remediate',
    schema: RESULT_SCHEMA,
    model: 'opus',
  })

  if (!result) {
    log(`Chunk ${chunk} was skipped.`)
    break
  }

  log(`Chunk ${chunk}: ${result.status} — ${result.detail}`)

  if (result.status !== 'success') {
    break
  }
}
