// NOTE: This workflow NEVER commits. It edits files in place and leaves all
// changes uncommitted for the user to review. There is no commit step, and every
// agent is explicitly instructed not to commit.
export const meta = {
  name: 'format',
  description: 'Apply the formatting rules in java.md and kotlin.md to a resolved set of files (never commits)',
  whenToUse:
    'Invoked by the /format command. Resolves a file set from the argument (uncommitted changes if empty, a file/directory path, a dotted package name, or an explicit array of repo-relative file paths) and applies the project code-style rules to each file. Never commits — leaves all edits uncommitted.',
  phases: [
    { title: 'Resolve', detail: 'Determine the file set from the argument', model: 'sonnet' },
    { title: 'Format', detail: 'One agent per file applies the java.md/kotlin.md style rules', model: 'sonnet' },
    { title: 'Verify', detail: 'Compile if any src/main file changed', model: 'sonnet' },
  ],
}

// These rules are mechanical and well-scoped (braces, blank-line spacing,
// local-variable naming, ternary expansion, accessor-chain dedup), so Sonnet
// handles them reliably — no need for a heavier model.
const FORMAT_MODEL = 'sonnet'

// What the resolve agent reports: the concrete file list plus how it read the target.
const RESOLVE_SCHEMA = {
  type: 'object',
  properties: {
    files: {
      type: 'array',
      items: { type: 'string' },
      description: 'Repo-relative paths of .java/.kt/.kts files to format (empty if nothing resolved)',
    },
    interpretation: {
      type: 'string',
      enum: ['uncommitted', 'file', 'directory', 'package', 'explicit', 'unresolved'],
      description: 'How the target argument was interpreted',
    },
    error: {
      type: 'string',
      description: 'Non-empty human-readable reason when the target could not be resolved; "" otherwise',
    },
  },
  required: ['files', 'interpretation', 'error'],
}

// Each format agent handles a BATCH of files, not just one — this caps the
// number of agents for large directories/packages and means the style guide is
// read once per batch instead of once per file. (Workflow agents are always
// fresh subagents — they can't fork the orchestrator to inherit an already-read
// guide — so batching is how we amortize that read.)
const BATCH_SIZE = 10

// What each batch agent reports back: one entry per file it processed.
const FORMAT_SCHEMA = {
  type: 'object',
  properties: {
    results: {
      type: 'array',
      description: 'One entry per file in the batch',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string', description: 'The file that was processed' },
          changed: { type: 'boolean', description: 'true if the file was edited' },
          summary: { type: 'string', description: 'Short description of what changed, or "no changes needed"' },
        },
        required: ['file', 'changed', 'summary'],
      },
    },
  },
  required: ['results'],
}

function chunk(items, size) {
  const batches = []

  for (let i = 0; i < items.length; i += size) {
    batches.push(items.slice(i, i + size))
  }

  return batches
}

// The caller may pass an explicit array of repo-relative file paths (e.g. the
// check skill, which has already resolved its review scope — from a diff, a
// package, or a commit — and wants exactly those files formatted). When it does,
// we skip the resolve agent entirely and use the list verbatim.
const explicitFiles = Array.isArray(args) ? args.map(file => String(file).trim()).filter(Boolean) : null
const target = !explicitFiles && args != null ? String(args).trim() : ''

// Only .java/.kt/.kts files can be formatted; the resolve agent applies the same
// filter, so we mirror it for the explicit-array path.
const FORMATTABLE = /\.(java|kt|kts)$/

// ---------------------------------------------------------------------------
// Phase 1: Resolve the file set.
// ---------------------------------------------------------------------------
phase('Resolve')

let resolved

if (explicitFiles) {
  const accepted = explicitFiles.filter(file => FORMATTABLE.test(file))
  log(`Received ${explicitFiles.length} explicit path(s); ${accepted.length} formattable.`)
  resolved = { files: accepted, interpretation: 'explicit', error: '' }
} else {
  log(target ? `Resolving target: ${target}` : 'No target — resolving uncommitted changes')

  const resolvePrompt = `MANDATORY: Read .agents/rules/serena.md

Determine the set of Java/Kotlin source files to format. The target argument is:

  ${target ? '`' + target + '`' : '(none)'}

Apply these resolution rules **in order** and stop at the first that matches:

1. **No target given** (the argument is empty): list every uncommitted \`.java\`,
   \`.kt\`, or \`.kts\` file in the working tree. Run \`git status --porcelain\`,
   collect added/modified/renamed paths with those extensions (both staged and
   unstaged), and **exclude deleted files**. Set \`interpretation\` to \`uncommitted\`.

2. **Target is a filesystem path to an existing FILE**: return just that file,
   only if it ends in \`.java\`, \`.kt\`, or \`.kts\`. Set \`interpretation\` to \`file\`.
   (If it is a file with any other extension, that is an error.)

3. **Target is a filesystem path to an existing DIRECTORY**: return every
   \`.java\`/\`.kt\`/\`.kts\` file under it, **recursively**. Set \`interpretation\`
   to \`directory\`.

4. **Target is NOT a valid path**: treat it as a dotted Java/Kotlin package name
   (e.g. \`songscribe.ui.renderer\`). Replace dots with slashes and look for a
   matching directory under the source roots — \`src/main/java\`, \`src/test/java\`
   (and \`src/main/kotlin\`/\`src/test/kotlin\` if they exist). If a matching
   directory is found, return every \`.java\`/\`.kt\`/\`.kts\` file under it,
   **recursively**. Set \`interpretation\` to \`package\`.

If none of the above resolve, set \`files\` to \`[]\`, \`interpretation\` to
\`unresolved\`, and \`error\` to a clear message explaining why.

Return **repo-relative** paths. Do not modify any files in this step.`

  resolved = await agent(resolvePrompt, {
    label: 'resolve',
    phase: 'Resolve',
    schema: RESOLVE_SCHEMA,
    model: FORMAT_MODEL,
  })

  if (!resolved) {
    log('Resolve step was skipped.')
    return { error: 'resolve skipped', files: [] }
  }
}

if (resolved.error) {
  log(`Could not resolve target: ${resolved.error}`)
  return { error: resolved.error, files: [] }
}

const files = (resolved.files || []).filter(Boolean)

if (files.length === 0) {
  log(`Nothing to format (interpreted as: ${resolved.interpretation}).`)
  return { interpretation: resolved.interpretation, files: [] }
}

log(`Formatting ${files.length} file(s) — interpreted as: ${resolved.interpretation}`)

// ---------------------------------------------------------------------------
// Phase 2: Format the files in batches, batches running in parallel. Each agent
// owns a disjoint set of files, so concurrent edits never collide. Batching
// caps the agent count for large targets and reads each style guide once per
// batch rather than once per file.
// ---------------------------------------------------------------------------
phase('Format')

function formatPrompt(batch) {
  const fileList = batch.map(file => `  - \`${file}\``).join('\n')
  return `MANDATORY: Read .agents/rules/serena.md

Apply the project's code-style formatting rules to each file in this batch,
one at a time:

${fileList}

Steps:
1. Determine which guides you need: read \`.agents/rules/java.md\` if any file
   ends in \`.java\`, and \`.agents/rules/kotlin.md\` if any ends in \`.kt\`/\`.kts\`.
   Read each guide **once** up front — it is the complete set of rules to apply.
2. For each file: read it and apply **every** rule from the guide matching its
   extension. Use the \`Edit\` tool for all changes so the user sees a diff.

STRICT CONSTRAINTS:
- This is a **formatting/style pass only**. Do NOT change behavior, logic,
  control flow, or any public API.
- Variable-name rules apply **only to local variables defined within the file
  being edited**. Never rename fields, methods, parameters, or any symbol that
  other files reference.
- Touch **only** the files listed above. Do not edit, create, or delete any
  other file.
- Do NOT compile, run tests, or commit.

Return a \`results\` array with one entry per file: \`file\`, \`changed\` (true only
if you edited it), and a short \`summary\`.`
}

const batches = chunk(files, BATCH_SIZE)
log(`Processing ${files.length} file(s) in ${batches.length} batch(es) of up to ${BATCH_SIZE}.`)

const batchResults = await parallel(
  batches.map((batch, index) => () =>
    agent(formatPrompt(batch), {
      label: `format:batch-${index + 1}`,
      phase: 'Format',
      schema: FORMAT_SCHEMA,
      model: FORMAT_MODEL,
    })
  )
)

const completed = batchResults.filter(Boolean).flatMap(r => r.results || [])
const changed = completed.filter(r => r.changed)
log(`${changed.length} of ${completed.length} file(s) changed.`)

// ---------------------------------------------------------------------------
// Phase 3: Compile to confirm the formatting pass didn't break anything.
//   - main-source edits  -> ./scripts/compile.sh
//   - test-source edits  -> ./scripts/compile.sh --test
// Run whichever apply (both if both kinds changed). Skip entirely if nothing
// under src/ changed (e.g. only build.gradle.kts was formatted).
// ---------------------------------------------------------------------------
const mainChanged = changed.some(r => r.file && r.file.includes('src/main/'))
const testChanged = changed.some(r => r.file && r.file.includes('src/test/'))

const compileCommands = []

if (mainChanged) {
  compileCommands.push({ command: './scripts/compile.sh', label: 'compile', reason: 'main-source files changed' })
}

if (testChanged) {
  compileCommands.push({ command: './scripts/compile.sh --test', label: 'compile-test', reason: 'test-source files changed' })
}

const compileResults = {}

if (compileCommands.length > 0) {
  phase('Verify')

  for (const cmd of compileCommands) {
    log(`${cmd.reason} — running \`${cmd.command}\`.`)
    const verify = await agent(
      `Run \`${cmd.command}\` exactly (no extra flags, no pipes). Report the final
SUCCESS/FAILURE line. If it FAILS, include the compiler errors so the user can
see what broke. Do not attempt to fix anything.`,
      { label: cmd.label, phase: 'Verify', model: FORMAT_MODEL }
    )
    compileResults[cmd.command] = verify || 'compile step skipped'
  }
}

return {
  interpretation: resolved.interpretation,
  formatted: changed.map(r => r.file),
  unchanged: completed.length - changed.length,
  compile: compileCommands.length > 0 ? compileResults : 'skipped (no src/ files changed)',
}
