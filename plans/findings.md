# Review: commit 71233310

`feat: replace editable annotation/tempo combos with a non-editable Other picker`

Four axes ran over the commit's 17 production files and its one new test file:
Design, Contract & API, Correctness & Efficiency, Test Conformance. Every
mechanical claim below was re-verified against the source before being written
here; where an axis was wrong, it is listed at the end under *Rejected*.

Two findings need your decision before any code changes: **D1** and **D2**. D2
reverses a decision Phase 1 of the plan made deliberately.

---

## Production defects

### P1. A user who types `Other…` as their own value gets the command row selected instead

**Where:** `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java:118-168`
(the overridden selection method) and `:171-176` (the value accessor).

**What the code does now.** The new combo box is a drop-down list of choices —
annotation texts, or tempo descriptions — whose last row reads `Other…`. Picking
that row opens a small dialog where the user types a value the list does not
offer. The list holds plain text, and `Other…` is just another piece of text in
it. To tell "the user picked the `Other…` command" apart from "the user picked a
value that happens to read `Other…`", the class keeps the one text object it put
in the list and compares incoming selections against it by object identity
rather than by matching text.

**What goes wrong.** Java's combo box does not select the object you hand it.
For a non-editable combo it scans the list for an element with matching text and
selects **that** object instead. Verified directly from the JDK 25 source on this
machine (`JComboBox.setSelectedItem`: `objectToSelect = element`).

So when a user types the exact text `Other…` into the prompt and commits it:

- the identity comparison correctly says "this is not the command", so the prompt
  does not reopen;
- but the value is **not** added to the list, because a matching entry already
  exists — the `Other…` row itself;
- and the combo then selects that row.

The user's annotation text is saved correctly, so nothing is lost or corrupted.
What is wrong is the state the combo is left in: the selected row is the command
row. The user cannot re-select their own value from the list afterwards, because
clicking that row opens the prompt instead. And the class documents, twice, that
this state is impossible.

**Confidence:** high — the mechanism is verified from the JDK source.

**What to do instead.** This is one of the symptoms of **D1** below, and D1's fix
removes it outright. If D1 is declined, the two false clauses must at least be
corrected to describe what actually happens (see **C1**).

### P2. An empty or missing choice file leaves the command row as the value

**Where:** the same file, constructor at `:103-117`.

**What the code does now.** The constructor adds the optional "no value" row,
then the values read from the bundled choice files, then the `Other…` row.

**What goes wrong.** Java's combo box model automatically selects the first
element ever added to it, and it does so on the model directly — so the class's
own interception of the command row cannot see it. Verified from the JDK 25
source (`DefaultComboBoxModel.addElement`).

If no real values load, `Other…` is the first element added and becomes the
selection. Two routes get there, and only one of them warns anybody:

- the choice file is missing or unreadable — this raises the "damaged
  installation, please reinstall" alert;
- the choice file exists and is **empty** — no exception, no alert, nothing.

Both need the mode that omits the "no value" row, so the annotation dialog is the
only one exposed; the tempo section always adds its empty row first and is safe.

All three shipped choice files (`annotations`, `tempos`, `tempochanges`) exist and
are populated, so this is not reachable in a correct installation. It is also
masked today because both callers write a real selection in before anything reads
it. Nothing is broken for a user right now.

**Confidence:** high on the mechanism, and it is a documented-invariant violation
rather than a live user-facing bug.

**What to do instead.** Also a symptom of **D1**, whose fix makes the empty-list
case a decision the compiler forces rather than a wrong string at runtime.

---

## Design findings — these need your approval

### D1. The `Other…` command and the real values are carried in one channel, so three of the class's promises cannot be enforced

Both opus axes reached this independently, with the same proposed shape.

**Where:** `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java`, the whole
class; and `src/test/java/songscribe/ui/dialog/OtherValueComboBoxTest.java:44-57`.

**The flaw.** Three different kinds of row are all represented as plain text: a
real value, the "no value" row (the empty string, painted as `(none)`), and the
`Other…` command. Because a command and a value are indistinguishable by type,
telling them apart falls to object identity — a comparison the language cannot
check, guarded only by a source comment reading *"Identity, not equality … Do not
'fix' this to equals()."*

**The symptoms it accounts for.** Every one of these disappears if the flaw is
fixed, and each needs its own separate patch if it is not:

- **P1** — a user-typed `Other…` selects the command row.
- **P2** — an empty choice file leaves the command row as the value.
- The identity comparison, and the comment defending it against a future reader.
- The renderer comparing against the empty string to decide whether to paint
  `(none)`.
- The value accessor's unchecked cast, with no stated guarantee that a selection
  exists (**CE1**).
- The one test that looks like it covers P1 but cannot detect it. It asserts that
  the returned **text** matches, and matching text is exactly what is true in the
  broken case — so it passes either way. Its stated claim ("text equal to the
  `Other…` label is an ordinary value, not the sentinel") is the opposite of what
  happens.
- Two contract clauses that are simply false (**C1**).

**The corrected design.** Give the rows a type instead of overloading text. A
sealed row type — a value case carrying its text, a "no value" case, and a
command case — over a combo box of that type. Then:

- a user-typed `Other…` is a value case and **cannot** be the command case, so P1
  is unrepresentable rather than documented-against;
- the value accessor must handle every case, so the empty-list state of P2 is a
  compile-time decision instead of a wrong string at runtime;
- the identity comparison, its defending comment, and the test guarding it are
  all deleted rather than rewritten.

While doing it, split the override's two unrelated jobs. One method currently
does three different things depending on what it is handed: open a dialog,
add-then-select, or plain select. A separate `setValue` method for programmatic
writes leaves the override doing only the thing that must happen there —
intercepting the command row before any listener sees it.

The codebase already has this pattern: `DurationListCellRenderer.createCombo`
drives a typed combo box with a renderer.

**What the change touches.** One new file for the row type; `OtherValueComboBox`
rewritten (251 lines today, and it gets shorter); two value-accessor call sites,
in `AnnotationDialog.gather` and `TempoSection.getTempoDescription`; one line in
`OtherValueController.commit`; one of the two test cases deleted. The mode enum
that selects whether the "no value" row appears survives unchanged.

**The argument on the other side,** which the class documentation makes: keeping
the rows as plain text means a future binding helper could read the combo with no
translation step. Nothing binds either combo today, and a typed model gives that
helper a typed property the caller converts once.

**What it costs to leave alone.** The class's correctness is not checkable by
reading it. It depends on which internal Swing paths happen to pass the list's own
objects through, on a comment surviving future edits, and on two clauses that are
already wrong. The next person adding a third combo of this shape copies it
without reading the JDK, and inherits P1 and P2 along with it.

**Recommendation: make the change.** It puts all three promises in front of the
compiler.

### D2. The rule "an annotation's text is never blank" was taken out of the type and restated as prose in five files — this reverses a Phase 1 decision

Both opus axes reached this independently. **This one undoes a choice the plan
made deliberately**, so it is squarely yours.

**Where:** `src/main/java/songscribe/dom/Annotation.java:29-87`, with the claim
repeated in `AnnotationController.java:39-41` and `:77-79`,
`AnnotationDialog.java:44`, `AnnotationIO.java:172-177`, and
`MeasureMapper.java:761-763`.

**What the code does now.** An annotation is a short text drawn above or below a
note — `dolce`, `Fine`. Before this commit its constructors refused blank text by
throwing, and the text accessor promised "never blank". Now the constructor writes
one line to the log and stores the blank text anyway, and the accessor promises
only "whatever this annotation was given". The non-blank rule is stated in prose
as something the user interface guarantees.

**What's wrong with it.** Six places in production read that text, and none of
them checks it: the layout width calculation, the on-screen renderer, the
MusicXML writer, the legacy-format writer, the old-format migrator, and a dead
accessor (**C13**). All six were written against the promise this commit deleted.
A blank annotation would measure zero wide but still occupy a layout slot, draw
nothing, and be written to MusicXML as an empty element that the reader then
discards — so the attachment would vanish between save and reload with no
message. The only trace is a log line reading `Annotation text must not be blank`,
naming no note and no location.

The guarantee did not move somewhere stronger. `Annotation` lives in `dom/`, a
package forbidden to know about the user interface — the build has a test that
fails if a `dom` class so much as imports one. So a document-model class's
contract is now written in terms of a dialog it may not reference, and nothing can
ever check that the two agree. The justification is circular: the model points at
the combo, and the combo's "no empty row" guarantee holds only if nothing writes
an empty value into it, and the only thing that could is an annotation carrying
blank text.

Three further consequences:

- **The check was already unreachable, which is what made it free.** Tracing every
  construction site: both file readers drop blank text before constructing, and
  the dialog's two sites use the hard-coded default and the combo's value. No
  caller can produce blank text. So the throw cost nothing and bought a promise
  six readers relied on.
- **The commit adds a warning for a corruption it is itself what enables.**
  `MeasureMapper` now logs `Corrupt document: annotation with no text`. Only
  SongScribe's own files are ever read, and SongScribe could not write a blank
  annotation before this commit.
- **It makes a promise elsewhere in the same commit falsifiable.** The new combo's
  no-empty-row mode promises its value is never empty. The annotation dialog
  populates that combo straight from the annotation's text.
- `dom/` does the opposite elsewhere: `StaffElement.setLyricForVerse` throws for
  the analogous rule. `Annotation` is now the outlier in its own package.

**The corrected design.** Restore the blank check as a thrown exception, delete
the warning helper and the logger field, restore "never blank" on the accessor,
and reduce the four cross-referencing prose paragraphs to a reference to the type
that carries the rule. Keep both readers' blank-text drops — a damaged file is
input a caller genuinely produces — and keep the new warning, which then
describes a genuinely damaged file.

**What the change touches.** One production file with logic changes; four files
with documentation-only changes; zero call sites; zero tests. Nothing in the test
tree pins the current behavior or wording.

**The plan's stated reason** was that a caller breaking the rule should be visible
in the log rather than crashing the app. After the commit's other phases there is
no caller that can break it, so there is nothing left to crash.

**Recommendation: restore the check.** The type is the only place this rule can
live without every future reader of annotation text having to decide what a blank
one means.

### D3. Historical and rejected-alternative narration in the new documentation

This is the question you raised mid-review, and the Design axis reached the same
diagnosis independently — it found the annotation rule "restated as prose in five
files" without any prompting about verbosity.

Per the rule now added to `~/.claude/rules/development.md`, the following come out:

- `OtherValueController` — two paragraphs defending the design against an
  alternative nobody is proposing.
- `OtherValueDialog` — a paragraph narrating how `BaseDialog` counts blocking
  dialogs internally. If that counting changes, this subclass's comment is
  silently wrong. The contractual part is only that this dialog opens while
  another modal dialog is up, which is supported.
- `OtherValueComboBox`'s private renderer — a three-sentence rationale on a
  private nested class with no caller outside the file.
- `setSelectedItem`'s deferral paragraph — it explains the mechanism at length
  but never states the consequence a caller needs: **the call returns before the
  user has answered, with the selection unchanged.** The mechanism is in; the
  promise is missing.
- `OtherValueDialog.populate`'s comment describes a situation that cannot arise —
  the input is always empty by contract, and the remember call ignores blank text,
  so the call does nothing and the comment's scenario is unreachable. The call
  itself should stay, because the field's contract requires whoever populates it
  to make the call.

What stays: the one-line `Do not "fix" this to equals()` warning, which sits
exactly where a reader would otherwise break it — and which is load-bearing only
for as long as **D1** is declined.

**Recommendation: strip them.** No behavior change, five files.

### D4. Two class names now promise a guarantee they no longer deliver

**Where:** `src/main/java/songscribe/ui/component/NonBlankGuard.java` and
`NonBlankTextField.java`.

**What the code does now.** The guard watches a text field; when focus leaves a
blank field it alerts and puts back the last non-blank text it remembers. This
commit removed the constructor argument that seeded that memory, so a guard that
has never seen a non-blank value alerts and puts back **nothing**.

That state is reachable by the most ordinary route there is: a new song has an
empty title, the title field is populated blank, and the remember call ignores
blank text — so clicking into the still-empty title field and tabbing away alerts
and leaves it empty. The plan confirms this was intended.

**What's wrong with it.** The names, and the summary sentences, still promise the
unconditional rule. The guard's first line reads "for a field that may not be
left blank", and the paragraph directly below it concedes the field **can** be
left blank. What actually prevents a blank commit is a separate mechanism: the
dialog's validity condition disabling OK.

**Proposed names:** `RestoreOnBlankGuard` and `RestoreOnBlankTextField`, with
summaries stating what is promised — a blank entry is refused with an alert and
the last remembered value put back; the field is left empty when there is nothing
to put back. Two users of the field class, one direct user of the guard;
`jet_brains_rename` updates them.

**This is a naming judgment, so I am asking rather than asserting it.**

---

## Contract findings

### C1. Two clauses that are false — proposed change to an existing contract

**Where:** `OtherValueComboBox.java` — the invariant on the overridden selection
method ("the selection afterwards is never the `Other…` sentinel") and the value
accessor's promise ("never the `Other…` sentinel").

Both are false, by the two mechanisms in **P1** and **P2**. A third clause is also
false for the P1 input: "a value the prompt commits is added to the list
immediately above `Other…` and selected" — it is not added, because a matching
entry already exists.

**If D1 is approved,** all three become true and no wording changes.
**If D1 is declined,** they must be weakened to state the exceptions — which
means writing down that the command row can be the value. That is a promise worth
seeing before agreeing to it, which is the argument for D1.

### C2. The new `@log` tag on the combo box documents a log line the method never writes

**Where:** `OtherValueComboBox.java:100` (constructor) and `:169-200` (the private
file-reading helper).

The clause reads *"an unreadable resource is reported to the user as a damaged
installation and leaves the combo with the values it read before the failure."*
The class has **no logger at all**, and the caught exception is discarded without
being read. `logging.md` fixes the form as `@log <level> <condition>`; this clause
has no level word and names no log call — it describes a modal alert and a
postcondition, which is `@effects` material.

There is a line in the log indirectly, because the alert helper logs every alert
it shows — but what it logs is the resolved alert text ("please reinstall"), with
no file name and no exception. A user reporting that alert leaves the log unable
to say which of several named resources failed, or why.

**Fix:** add the standard logger and log where the failure happens —
`LOG.error("Could not read combo values from '{}'", fileName, e);` — then the
clause becomes `@log error when a named resource cannot be read`, and the alert
plus the partial-list outcome move to `@effects`.

### C3. Four new `@log` clauses spell the level differently from the three that already existed

The commit's clauses (both `Annotation` constructors, `AnnotationIO.build`,
`MeasureMapper.annotationOf`) write `warning`. The three pre-existing clauses in
the codebase write `warn` and `error` — the actual SLF4J level names, which is
what `logging.md` specifies. Verified by searching both. The disagreement was
introduced by the same commit that documents the tag.

**Fix:** `warning` → `warn` in four places.

### C4. One `@log` clause names half its condition

`MeasureMapper.annotationOf` says *"warning if the direction's words are blank"*.
The code also logs when the words element carries no value at all
(`text == null || text.isBlank()`). Anyone deriving a test from that clause misses
half the condition.

**Fix:** "warn when the direction's words carry no value or a blank one."

### C5. The annotation dialog's class documentation states two things that are false

**Where:** `AnnotationDialog.java:40-47`.

Its first sentence still reads "An **editable** text combo plus alignment and
placement radios" — making that combo non-editable is the entire point of this
commit, and it cannot be typed into. Two lines later: "**The text is never
blank.** `Annotation` does not permit it" — Phase 1 of this same commit is what
made `Annotation` permit it.

The class comment is the first thing a reader opens, and the second sentence is
the more damaging: a reader who believes the type refuses blank text does not
think to check, which is how the six unchecked readers in **D2** came about.

The plan's Phase 6 said to "keep the first paragraph and the reference to
`Annotation`" — written before Phase 1 landed, which is how both survived.

**Fix:** first sentence → "A fixed-list annotation combo plus alignment and
placement radios". Second paragraph: if **D2** is approved the appeal to
`Annotation` becomes true and stays; if declined, drop it and state what this
dialog is responsible for.

### C6. A method's documentation still describes a parameter this commit deleted

**Where:** `NonBlankGuard.rememberCurrentText`.

It still says a user emptying a freshly opened field "would get **the fallback**
instead of what they were looking at" — the fallback was deleted by this commit —
and that remembering blank text "would break the class promise", which the new
promise explicitly allows.

**Fix:** delete both clauses; the `@effects` line already states the real promise.

### C7. `NonBlankGuard` still advertises a use that was deliberately closed

**Where:** `NonBlankGuard.java:28-32`.

It says "the field may be a combo box's editor as easily as a plain text field."
This commit deleted the helper that obtained a combo box's editor and made both
editable combos non-editable. There is now no combo box editor in the tree and no
supported way to get one — and reopening that path is what the commit exists to
prevent.

**Fix:** delete the clause.

### C8. A four-paragraph contract on a private one-line helper, stating a rule that belongs one tier up

**Where:** `StandardDialog.java:136-158` (`dismissWithoutVerifying`), and the same
rule again in `NonBlankGuard.shouldYieldFocus`.

The fix itself is right and the explanation is worth having: a field's alert goes
up inside the mouse press that moves focus to the button, swallows the release,
and the button's action never runs. But that rule binds every dialog and every
field verifier in the application, not this one private helper wrapping a single
setter — and it is already written out in full a second time, in the guard.

**Fix:** state it once on `StandardDialog`'s class comment, which already
describes the button lifecycle; leave the helper an accurate name and a one-line
`@effects`; have the guard link rather than restate.

### C9. The same rule is written in four places, and two copies already contradict each other

The pairing — the validity condition makes a blank field uncommittable, the guard
restores a value once focus leaves — is stated in `NonBlankGuard`'s class comment,
`NonBlankTextField`'s class comment, `OtherValueDialog`'s class comment, and
`.claude/guides/dialogs.md`. All four were written or rewritten in this one
commit, and they already disagree: the guide still opens "A field that must never
be *left* blank is a `NonBlankTextField`", which is the promise the classes gave
up (**D4**).

**Fix:** state it once in `dialogs.md` under Validity, corrected; have the classes
link to it. Each class comment then keeps only its own promise.

### C10. A documented sentence that teaches something untrue

**Where:** `OtherValueComboBox.java:88`, the field holding the command text.

Its Javadoc justifies resolving the label once with "a second equal instance would
not be the sentinel." Verified: the string lookup delegates to the resource
bundle, which returns the **same** instance every call — so resolving twice cannot
produce a second instance. Resolving once is still correct, because it does not
depend on that; the stated reason is false and teaches a future reader something
wrong about how string lookup behaves.

**Fix:** delete the justification, keep the field.

### C11. Missing mandatory tags

`contracts.md` makes `@return` mandatory on any documented method that returns a
value, and `@param` on documented parameters. Missing on:

- `StandardDialog.verifyFocusedField` — returns the boolean that decides whether
  OK proceeds, explains all three cases in prose, no tag.
- `AnnotationAttachment.getAnnotation` and `getText` — no `@return`.
- `AnnotationAttachment.setAnnotation` — no `@param`.
- `TempoSection.setTempo` — no `@param`.

All predate this commit; all are in files it changed.

### C12. Two same-typed parameters a call site can silently transpose

**Where:** `TempoSection.java:62` —
`TempoSection(Duration[] types, String checkboxLabel, String... fileNames)`.

At a call site reading `new TempoSection(types, "Show only the tempo
description", "tempochanges", "tempos")`, nothing but argument order separates the
checkbox label from the first resource name. Swapping them compiles: the label
would be looked up as a file, and a file name painted beside a checkbox. This
commit routes those names into the new combo box, so it is the live path.
`java.md` requires a parameter object for exactly this shape.

**Fix:** take the resource names as one typed value so the label cannot slide into
their position.

### C13. A public method on a document-model class with no callers

**Where:** `AnnotationAttachment.getText()`. Verified: zero references across
production and tests. This commit deleted its counterpart setter and two unused
constructors but left the getter. It is a second public route to data the class
already exposes.

**Fix:** delete it.

### C14. A constant wider than it needs to be

`AnnotationDialog.DEFAULT_ANNOTATION` is package-private with exactly one
reference, inside its own class, and no contract names it. Should be `private`.
Predates this commit.

---

## Correctness & Efficiency

### CE1. The value accessor's cast depends on a guarantee nothing states

**Where:** `OtherValueComboBox.java:171-176` — `return (String) getSelectedItem();`,
no null check.

This is safe only because nothing currently empties the combo or clears its
selection, and because construction always leaves *something* selected — which
**P2** shows is not always the thing the contract promises. Nothing documents "a
selection always exists" as an invariant, so a future caller has no way to know
that clearing the selection sends a null into the annotation constructor, where
the blank check calls a method on it and throws.

Reported as a symptom of **D1**, whose typed accessor must handle every case
explicitly. If D1 is declined, state the invariant on the class.

---

## Test Conformance

### T1. A line this commit made redundant, with a comment that is now misleading

**Where:** `SongSettingsDialog.java:93-95`.

This commit made the shared dialog base class exempt Cancel and Remove from field
verification, for every subclass. Song Settings already had that exact line for
its own Cancel button, so the call now happens twice. Setting a flag twice is
harmless; the comment is not. It reads "Let Cancel bypass the title field's
`NonBlankGuard`", naming one field as the reason — which the base class's own
documentation explicitly argues against ("nothing a field says about its value is
relevant to a path that never reads the value"). The next author reading Song
Settings learns the wrong rule and copies the line into their dialog.

**Fix:** delete the three lines. No behavior change.

### T2. The class's central promise has no test — and cannot get one in its current shape

**Where:** `OtherValueComboBoxTest.java` versus `setSelectedItem`'s contract.

The promise the whole class exists for is that choosing `Other…` never changes the
selection, so no listener or caller can ever observe the command row as a value.
Neither test ever passes the real command object, so that promise is asserted
nowhere.

**The Test axis proposed simply adding that test. That does not work, and the
reason is the finding.** Passing the command object queues the prompt through
`invokeLater`, and the prompt-opening method fetches the main window from a
process-global singleton and constructs the dialog itself. The unit test task does
**not** set `java.awt.headless` — I checked; that flag is set only in the mutation
testing block, despite a comment claiming unit tests run headlessly — so the queued
task would try to open a real modal dialog on the event thread, asynchronously,
after the test method has returned. That is a hang or a cross-test failure
attributed to whichever test happens to be running.

The honest finding is about the production design, not the missing test: the
class's central promise is unobservable because the class builds its own dialog
from a singleton. Injecting the prompt-opener would make it observable — the test
asserts the opener was asked and the selection did not change, with no dialog and
no singleton. **I am not proposing a mock**, which would be a workaround around
the same flaw.

This interacts with **D1**: D1 splits the override's jobs, which is the natural
point to also give it the collaborator rather than have it reach for one.

### T3. A two-value choice where only one value is ever exercised

The mode enum deciding whether the "no value" row appears has a real production
caller for each value — the annotation dialog uses one, the tempo section the
other. Every test uses only the annotation dialog's. So nothing checks that the
other mode puts the empty row first, that the value accessor can answer empty, or
that the renderer paints it as `(none)` — the half of the class the tempo
description picker depends on.

`testing-common.md` calls out exactly this: a finite domain sampled rather than
enumerated, where picking one is what leaves the other broken.

**Fix:** one parameterized test over both values, checking what differs.

### T4. The tests read shipped data and re-declare a production constant's value

Both tests build their combo from the real shipped `conf/annotations` file. One
then asserts a particular phrase is *not* already in that list, and the other
relies on the command label not colliding with any entry — neither assumption is
pinned, so an unrelated edit to the shipped annotations file could break a test
that has nothing to do with it.

Separately, the test re-types the literal `"annotations"` that already exists as a
constant in `AnnotationDialog`. `contracts.md` forbids redeclaring a production
constant's literal in test code.

**Fix:** neither test needs a real file — the constructor takes zero or more file
names, so passing none gives a combo whose only content is the command row, with
no shipped-data dependency and no duplicated literal. **Note:** that setup is
exactly the state **P2** describes, so writing it while D1 is unfixed would make
the tests sit on top of the defect. Worth doing after D1, not before.

### T5. The only test class in the suite using `@DisplayName`, on methods that skip the naming convention

`testing-common.md:280` requires a `test*` prefix naming the contract case;
`@DisplayName` without that prefix belongs on a `@Nested` class. Verified: this is
the only test class in `src/test/java/songscribe/` using the annotation — the two
other matches are test infrastructure.

**Fix:** fold the display text into `test*`-prefixed method names and drop the
annotation.

---

## Rejected — axis claims that did not survive checking

- **"`NonBlankGuard` needs a unit test because changing non-UI code requires
  one."** The rule quoted says the opposite: *"UI is excluded because a window is
  verified by opening it."* A Swing input verifier whose observable behavior is a
  modal alert and a focus yield is UI. The plan also designates manual
  verification for this, deliberately.
- **"`NonBlankGuard`'s 'unless it has never held a non-blank value' hedge describes
  an unreachable case."** It is reachable by the most ordinary route there is —
  see **D4**. The hedge is accurate and load-bearing.
- **"The dialog framework should grow a 'reads nothing' input case."** One dialog
  would use it. Both axes and I agree: leave it.
- **The Design axis read the sentinel test as worthless.** It is narrower than
  that. The test does still catch the regression it was written for — changing the
  identity comparison to a text comparison would fail it. What is wrong is that
  its stated claim is the opposite of what happens, and asserting on text rather
  than identity is what lets **P1** pass unnoticed.

---

## Not a finding, but outstanding

The plan's **Phase 11, manual UI verification, is still pending.** It is the only
verification for the dialog wiring and the whole `Other…` flow — no automated test
covers any of it (**T2**). It needs your permission to run the app.
