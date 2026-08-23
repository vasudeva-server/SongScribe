# Check Findings — Tempo Section Bindings

Review target: the working-tree diff, plus two untracked test files.

**This file records what the review found, before any of it was acted on.** It is not a
description of the code as it now stands. Every finding below is either fixed, or noted
here as declined:

- Finding 3 was fixed differently from the proposal. The `(none)` row is refused by a
  selection model on the drop-down list rather than by moving the rule to `requireValid`,
  so the two bindings stayed.
- Finding 4 was accepted in its type half and declined in its guard half. The tempo
  marking is now a sealed type, and `Tempo` keeps no runtime guard.
- Finding 14 became moot: with the sealed type there is one repair, in one place.
- Finding 17 was declined. The plan file is to be deleted.
- Finding 21 was declined for the fixture. The broken script was fixed.

Some background, because every finding below depends on it. A song's tempo marking
has two parts. The first part is the metronome glyph and the beats-per-minute
number. The second part is a free-text description, such as "Allegro". A checkbox
named "Show only description" hides the first part. The description combo box
offers a first row named `(none)`, which means "no description".

One pair of values draws nothing at all: the checkbox checked, and `(none)`
chosen. A tempo in that state still sets the beat and the playback speed. It is
invisible on the page. When it sits on a note, the user cannot click it, so the
user cannot correct it. This change exists to make that pair unreachable.

---

## 1. Production bug: the keyboard defeats the new bar, and creates the exact tempo this change forbids

**Where:** `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java:168-180`, the
method that intercepts a row selection, together with
`src/main/java/songscribe/ui/dialog/TempoSection.java:145-154`, the two rules the
tempo panel declares.

**What the code does now.** The panel forbids the pair by two rules. The checkbox
is disabled while the description is empty. The `(none)` row is barred while the
checkbox is checked. A barred row stays in the list, painted grey. The refusal
lives in one method: the combo overrides `setSelectedIndex(int)` and returns
without action when it is asked for row 0 while barred. The class states its
reason in its own comment: every route by which the user picks a row arrives at
that method. Its sibling `setSelectedItem` stays unbarred on purpose, so that the
panel can still write an empty description as a caller.

**What is wrong with it.** That claim about "every route" is false. I read the
Swing source that ships with the JDK on this machine, at
`javax/swing/plaf/basic/BasicComboBoxUI.java`. I did not work from memory.

1. The Up and Down keys, while the drop-down list is open, run
   `selectPreviousPossibleValue()` at line 1217. That method moves the drop-down
   list's own highlight first, without any condition. It asks the combo to follow
   second. The combo refuses, and the highlight stays on the barred row.
2. The Enter key, while the list is open, does not reach `setSelectedIndex` at
   all. Line 1688 reads the list's highlighted value and commits it through
   `comboBox.setSelectedItem(listItem)`. That is the one route the bar does not
   govern.
3. The mouse route is safe. `BasicComboPopup` line 946 commits through
   `setSelectedIndex`, which the bar does govern. So the bar works for the mouse
   and fails for the keyboard.

**What the user sees.** Open Song Settings, then the Music tab. Choose any
description. Check "Show only description". Open the description combo. Press Up
until the grey `(none)` row is highlighted. Press Enter. The description is now
empty and the checkbox stays checked.

The dialog commits that state. The Music tab's own comment says it contributes
nothing to validation, because this pair was supposed to be unreachable. So OK
stays enabled. The tempo marking then disappears from the score.

The user is also locked in. The other rule now disables the checkbox, because the
description is empty. The checkbox is stuck in the checked state, greyed. The only
way out is to choose a description first.

**Three written promises are false as a result:**

- `OtherValueComboBox.setSelectedIndex` promises that the selection afterwards is
  never the `(none)` row while that row is barred. That is true of the method. It
  is written as a promise about the control.
- `TempoSection.getTempo` promises that the tempo is always visible, because no
  state the two controls can reach describes one that is not.
- `docs/song-tempo.md` states that nothing a song holds answers false to the
  visibility rule.

**Confidence: high on the mechanism.** I read the JDK source and confirmed each
line number above. I did not run the application.

**The fix is finding 2**, because a patch in place makes the design worse. See
there.

---

## 2. Production bug, same cause: the `Other…` row can commit its own label as a value

**Where:** the same method, `OtherValueComboBox.java:168-180`.

**What the code does now.** The last row of the combo is a command row named
`Other…`. It opens a prompt for a value the list does not hold. The combo
identifies it by position and intercepts it in `setSelectedIndex`, exactly as it
intercepts the barred row.

**What is wrong with it.** The model stores that row's value as the literal label
text. So the Enter route described in finding 1 calls
`setSelectedItem("Other…")`. The model holds a row equal to that text, so the
combo selects it. `getValue()` then answers the literal text `Other…`, and the
dialog writes it into the song as a real value. In the Annotation dialog the
annotation becomes the word `Other…`. In the tempo dialogs the tempo description
becomes it.

**Confidence: high on the mechanism, medium on how easily a user reaches it.**
When the user arrows onto the `Other…` row, the intercept schedules the prompt,
and the prompt opens on the next event cycle. That steals the Enter key in the
ordinary case. The path stays open when the user cancels the prompt while the
drop-down is still shown.

**This one predates the change.** It is the same false claim about routes, in the
same class, in the code this change edits. Finding 3 removes it along with
finding 1.

---

## 3. Design finding: the pair rule is enforced by disabling controls, and that is what produced both bugs

**Approval required before any code changes.**

**The tempting patch is the wrong one.** The obvious repair for finding 1 is to
bar the `(none)` row inside `setSelectedItem` as well. That breaks
`TempoSection.setTempo`, which must write an empty description into a barred combo
when it populates the controls. To make it work again needs a flag that says
whether a write came from the user or from a caller. That is another boolean and
another special case on a shared widget. The project rules forbid it, and it
leaves a second copy of the same hole for the next rule someone adds.

**What the correct structure is.** The rule is one statement about a pair of
values. The dialog framework already holds the construct for a live rule over
dialog values. `BaseDialog.requireValid(ObservableValue<Boolean>)` conjoins
conditions and disables the OK button while any one of them fails. Its own
contract states the intent: a rule stated there is one the user sees the moment
they break it, rather than one they are told about after they press OK.
`BaseDialog.Tab.requireValid` contributes a tab's condition to its dialog. Both
tempo dialogs extend `StandardDialog`, so both get the disabled OK button.

The change has two halves, and they work only as a set.

**Half one — move the rule out of the widget.**

1. `TempoSection` replaces its two `bindings.bind(...)` calls with one derivation
   over both properties, through `bindings.computed(...)`, and exposes it as an
   `ObservableValue<Boolean>`.
2. `SongSettingsMusicTab` and `TempoChangeDialog` each pass that to
   `requireValid(...)`. One line each.
3. Both controls stay free. No row is barred. No checkbox is disabled.

**Half two — put the command interception where every route arrives.**

Once no row is barred, nothing needs the caller-versus-user split, because no
caller ever writes the `Other…` label as a value. So `setSelectedItem` can carry
the `Other…` interception without any flag. `JComboBox.setSelectedIndex` delegates
to `setSelectedItem`, so the override of `setSelectedIndex` is deleted outright.
One interception point governs every route: mouse, arrow key, typeahead, Enter,
and a programmatic write. The interception still tests position rather than text,
through `comboModel.getIndexOf(text) == otherIndex()`, so the class keeps its
stated rule that no text comparison identifies a command.

**Why half two cannot stand alone.** While barring exists, a check inside
`setSelectedItem` blocks the panel's own legitimate write of an empty
description. Half one is what removes that conflict.

**Why half one cannot stand alone.** It fixes finding 1 and leaves finding 2 in
place.

**The symptoms this one change accounts for.** Each item below exists only to
support barring, or only because the interception point is wrong:

- Finding 1 and finding 2, both user-visible today.
- `setEmptyChoiceSelectable(boolean)`, `isEmptyChoiceBarred()`, `EMPTY_INDEX`, the
  refusal branch, and the second `@invariant` — all deleted.
- The renderer's grey painting, and its change from a static nested class to an
  inner class that reaches back into the combo — reverted.
- The `repaint()` call that repaints the wrong surface (finding 9) — deleted.
- The missing clause about a `(none)` row that is already selected (finding 10) —
  moot.
- The two flags that carry three states (finding 11) — moot.
- The two calls to the shared rule that each pin one input to a constant.
  `Tempo.isVisible(description, false)` reduces to "the description is not
  empty". `Tempo.isVisible("", !hidden)` reduces to "the box is not checked".
  Neither call evaluates the rule. Each evaluates a one-term collapse of it. The
  class comment and `docs/song-tempo.md` both claim the two bindings read one rule
  rather than two copies of it. They do not. A single `computed` over both
  properties is the first place the rule reads as a rule.
- Three tests stop being needed:
  `TempoSectionTest.testCheckBoxIsDisabledWhileTheDescriptionIsEmpty`,
  `TempoSectionTest.testNoneRowIsBarredWhileTheCheckBoxIsChecked`, and
  `OtherValueComboBoxTest.testBarredEmptyRowRefusesTheUserButNotACaller`.
- The `childOfType` helper in `TempoSectionTest` goes with them. That helper walks
  the panel's Swing children to reach a private control. Its own comment names the
  problem: it reaches a control by type rather than through an accessor the
  production code has no use for.

**What the change touches.** Four production files: `OtherValueComboBox`,
`TempoSection`, `SongSettingsMusicTab`, `TempoChangeDialog`. Two call sites added.
Two test files, with three tests and one helper deleted. One paragraph rewritten
in `docs/song-tempo.md`. Most of the diff is deletion.

**The argument for the current approach, addressed.** Barring gives local
feedback. The user sees the row go grey the moment it becomes illegal.
`requireValid` gives no message. OK simply greys out. In the multi-tab Song
Settings dialog, an OK button that greys because of the Music tab is opaque to a
user who looks at the Title tab. That cost is real. It is smaller here than it
looks, because the two controls sit side by side in one small panel, and the
user's next action is the one that greys OK. The dialogs guide already takes this
trade for every other live rule in the application.

**I recommend the change.** It closes two defects a user can hit today. It deletes
more code than it adds. It puts the pair rule in the one place where it reads as
the rule it is.

**What it costs to leave alone.** The illegal pair stays reachable by keyboard, so
a song can still carry a tempo that governs beaming and playback while it shows
nothing, and the user cannot correct it inside the dialog. The `Other…` row can
still commit its own label as an annotation or a tempo description.
`OtherValueComboBox` keeps a mutable mode for which rows the user may pick, so the
next caller that needs a different row barred adds a second flag, a second
position constant, and a second refusal branch. That caller inherits the same
keyboard hole, because the hole is in the interception point they copy. And the
class keeps a written promise about routes that is false, which is what caused
both defects.

---

## 4. Design finding: `Tempo` permits the state the rest of the system says cannot exist

**Approval required before any code changes.**

**Where:** `src/main/java/songscribe/dom/Tempo.java`, and the four places that call
`Tempo.isVisible`.

**What the code does now.** `Tempo` holds four values with a public setter for
each: the beat unit, the beats per minute, the description text, and the
show-the-glyph flag. Nothing in it prevents the forbidden pair. Instead a new
static helper states the rule, and four separate places call it.

**What is wrong with it.** The type still permits the state the change declares
impossible. Every awkward shape in this diff follows from that:

- `Tempo.isVisible` had to be a static two-argument function over the very fields
  a `Tempo` already holds. The MusicXML reader asks the question before it may
  build a `Tempo`. The dialog rules ask it about a tempo that does not exist yet.
- Both file readers build first and repair second. `MeasureMapper.buildTempo`
  flips a local flag before it constructs. `TempoIO.TempoReader.finishTempo`
  constructs, then reaches back in and flips the flag. Repair after the fact
  exists only because construction cannot refuse.
- The layout code still defends against the state. See finding 5.
- The message that carries a tempo edit takes the tempo apart into four
  independently optional fields. See finding 6.

**What the correct structure is.** The marking a tempo carries is one of two
things, not two independent switches:

1. **A metronome marking** — the note glyph, the number, and optionally some text
   after it. The text may be empty here, and that is correct.
2. **Text only** — a description, which cannot be empty, because then nothing
   would be left to draw.

Replace the description field and the flag with one sealed type that holds those
two cases. The text-only case refuses empty text in its constructor. Then:

- `Tempo.isVisible` disappears. There is no question left to ask.
- Both readers stop repairing and start converting. A file that says "hide the
  glyph" with no text produces the metronome case with empty text. That is the
  same outcome they already produce, written as construction rather than as
  check-then-fix.
- The drawing code switches on the two cases instead of reading a flag, and the
  guard in finding 5 is deleted.
- The two dialog rules stay, and finding 3 still applies to how they are enforced.

**What the change touches.** Eleven production files: `Tempo`, `Song`,
`TempoDidChangeNotification`, `SongSettingsController`, `TempoIO` for both read
and write, `MeasureMapper`, `DirectionBuilder`, `MetronomeContent`,
`SystemStacker`, `TempoSection`. Four test files, all four already in this diff.
The corpus generator. The one part that breaks if it is done carelessly is
`Song.applyTempoUpdate`, which writes the four fields one at a time and relies on
writing the flag on its own. Finding 6 removes that reliance.

**One fact this design must answer.** `TempoIO.TempoReader` builds a tempo
incrementally, one setter per XML tag. So the illegal pair is a legitimate
intermediate state during a read. A validating constructor requires that reader to
collect its fields and construct once at the closing tag.

**I recommend the change.** The rule this diff introduces is correct. What is
missing is the one place it could live where it costs nothing to keep true. Left
as it is, the next person who adds a producer of tempos — an import path, a
template, a scripting hook — cannot learn the rule from the type. The only thing
that catches the mistake is a marking that silently fails to draw. A type with two
cases retires the rule, both repairs, the dead layout guard, and three of the four
new test classes.

**This finding and finding 3 compose.** They are separate decisions. Finding 3
fixes two defects a user can hit today, and it is the smaller change. Finding 4
removes the reason the rule has to be restated at every boundary.

---

## 5. The layout code defends against the state, and the design note cites that defence as its reason

**Where:** `src/main/java/songscribe/layout/stacking/SystemStacker.java`, in
`stackTempoMark`. That method places the song's tempo marking beside the first
staff.

**What the code does now.** It builds the marking's contents. It then checks
whether the result has zero width, and places nothing when it does. A comment
beside the check says that a false flag with an empty description leaves nothing
to draw.

**What is wrong with it.** The codebase asserts the state cannot occur and
defends against it occurring. The new paragraph in `docs/song-tempo.md` uses that
defence as the reason not to enforce the rule inside `Tempo`. It says the layout
path stays a total function over every tempo it can be handed. The layout path is
total because of this guard. The justification and the guard point in opposite
directions.

Nothing breaks today. A reader of `Tempo` and a reader of `SystemStacker` come
away with opposite beliefs about whether the state can exist.

**What to do instead.** Choose one and write it down. Under finding 4 the guard is
provably unreachable, and it goes with its comment. Without finding 4, the guard
stays, and the paragraph in `docs/song-tempo.md` needs the honest reason, which is
the incremental reader named in finding 4.

---

## 6. Design finding: the tempo-edit message takes a tempo apart and puts it back, with four checks that cannot fire

**Approval required before any code changes.**

**Where:** `src/main/java/songscribe/message/notification/TempoDidChangeNotification.java`.
The sender is `SongSettingsController.applyTempo`. The receiver is
`Song.tempoDidChange` and its helper `Song.applyTempoUpdate` at
`src/main/java/songscribe/dom/Song.java:1704`.

**What the code does now.** When the user confirms Song Settings, the controller
takes the tempo the dialog produced. It pulls the four values out and posts them
as four separate fields, each one allowed to be absent. `Song` copies across each
field that is present and leaves the rest alone, in four conditional writes.

**What is wrong with it.** I traced every reference to the message type. There is
exactly one sender, and it always fills all four fields, because a complete tempo
is handed to it. So all four presence checks are dead. No caller can produce the
absence they test for. That shape costs three things.

1. It is the one production route left that writes the show-the-glyph flag
   independently of the description text. The rule is enforced wherever a tempo is
   created. This path modifies a tempo in place, one field at a time. A future
   sender that wanted to change only the flag would break the invariant, with
   nothing to stop it and no warning logged.
2. A whole value is dismantled into four parts and reassembled for no reason. The
   type says "a partial tempo update" while every actual use says "a tempo".
3. The receiving code is longer than the work it does.

**What to do instead.** Have the message carry one `Tempo`, as a detached copy,
which the surrounding undo code already requires. Have `Song` apply it as a
whole-value copy. The four optional fields, their four accessors, and the four
conditional writes all go.

**What the change touches.** Three files: the message class, `Song`, and
`SongSettingsController`. No behavior changes, because the only sender already
sends all four values.

**I recommend the change.** Confidence: high.

---

## 7. Defect: the visibility rule asks whether the text is empty, when the domain asks whether it is blank

**Where:** `src/main/java/songscribe/dom/Tempo.java`, in `isVisible`.

**What the code does now.** The rule says the marking draws something when the
flag is on, or when the description is not the empty string.

**What is wrong with it.** A description of spaces alone is not the empty string.
So the rule calls it visible. It draws nothing a reader can see. On a tempo change
attached to a note, it gives nothing a user can click. That is the same
unreachable, uncorrectable marking this change exists to remove, and it walks
straight through the rule. The dialog cannot produce one, because the `Other…`
prompt refuses a blank entry. An old file or a hand-edited file can carry one, and
neither reader repairs it, because both defer to this rule.

**What to do instead.** Ask whether the text is blank rather than whether it is
empty, and say so in the rule's contract. One word in one file. Under finding 4
the same condition moves into the text-only case's constructor.

**Confidence: high on the logic. Medium on how often a real file carries a
whitespace-only description.** This one carries a domain judgment, so I ask about
it below rather than decide it.

---

## 8. Defect: both new test classes skip the base class that keeps the message bus from leaking

**Where:** `src/test/java/songscribe/io/TempoIOTest.java` and
`src/test/java/songscribe/io/musicxml/MusicXmlTempoReadTest.java`. Both are new and
untracked. Neither extends `songscribe.UnitTest`.

**What the tests do now.** Each is a plain class with no base class. Every other
unit test in the project extends `UnitTest`.

**What is wrong with it.** This is not a style point. I read `UnitTest`. Its setup
and teardown do three things that matter here:

1. It opens a message bus for each test and discards it afterwards.
   `MusicXmlTempoReadTest` parses a MusicXML document, which builds a `Song`, and a
   `Song` subscribes itself to the message bus in its constructor. Without the
   per-test bus, that subscription lands on the application-wide bus and is never
   removed. A later test in the same run that posts a message the leftover song
   handles will reach it. The application bus terminates the process when a handler
   throws. So the failure mode is the whole test run dying partway through, with no
   sign of which test caused it.
2. It installs a handler that stops a fatal-resource error from exiting the
   process, so a real failure inside the reader shows up as a failed test rather
   than a vanished run.
3. It asserts afterwards that no message handler threw in silence, and that no
   background thread died unnoticed.

`TempoIOTest` builds no `Song`, so it leaks no subscription. It still lacks the
safety net.

**What to do instead.** Extend `songscribe.UnitTest` in both classes. Two lines.

---

## 9. `setEmptyChoiceSelectable` repaints the one surface barring never changes

**Where:** `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java:204-207`.

**What the code does now.** The method sets the flag and calls `repaint()`. Its
contract promises that the repaint makes a row already painted take the new state.

**What is wrong with it.** The combo does not paint the rows. The drop-down list
paints them, and it lives in a separate pop-up component. So a repaint of the
combo does not reach them. The only surface the repaint does reach is the combo's
own closed display. The renderer's own comment says that display is never greyed,
because it renders with an index of -1, which no row position equals. So the call
repaints the one surface barring cannot affect, and skips the one it can.

Nothing looks wrong to the user today. The flag only ever changes in answer to the
checkbox, which cannot be clicked while the drop-down is open, and the drop-down
repaints itself each time it is shown.

**What to do instead.** Finding 3 deletes this method. Without finding 3, delete
the `repaint()` call and the clause that promises it does something.

---

## 10. `setEmptyChoiceSelectable` does not say what happens to a `(none)` row already selected

**Where:** `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java:189-207`.

**What the code does now.** Barring refuses future selections only. A combo that
already shows `(none)` keeps showing it, greyed, and `getValue()` keeps answering
the empty string. The contract does not mention that.

**What is wrong with it.** A caller who reads this contract would reasonably
assume that barring makes the value legal. That is why one bars a row. It does
not. No sequence in this application reaches that state today. The whole point of
barring here is to keep an invariant, and the contract omits the one case where
barring does not keep it.

**What to do instead.** Finding 3 deletes this method. Without finding 3, add the
clause: barring refuses new selections and never changes the current one, so a
caller who needs a legal value must write one.

---

## 11. The combo carries three states in two flags, and documents the meaningless fourth

**Where:** `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java:83-89`.

**What the code does now.** The class used to store its mode as a two-valued named
constant: it offers a `(none)` row, or it does not. This change replaces that with
a plain boolean, and adds a second boolean for whether the user may pick the row.
A private helper combines them. The new contract for the setter includes a
paragraph that explains that the setter does nothing on a combo with no `(none)`
row.

**What is wrong with it.** There are three meaningful states: no `(none)` row at
all; a `(none)` row the user may pick; a `(none)` row the user may not pick. Two
booleans express four. The fourth means nothing, and the class documents it rather
than making it unwritable. A paragraph whose job is to explain a meaningless state
is the clearest available sign that the shape is wrong.

The combining helper also tests something no caller reaches. This class is visible
only inside its own package. It has two construction sites, and the one built
without a `(none)` row is never asked to bar anything.

**For context:** the written plan for this work, at
`plans/temposection-bindings.md` Phase 1 task 9, told the implementer to check
whether the mode field was still read and, if not, to delete it and use the
constructor parameter directly. It was kept as a derived boolean instead.

**What to do instead.** Finding 3 deletes this machinery outright. Without finding
3, carry the three states in one named constant with three values, set at
construction and moved by the setter.

---

## 12. `Tempo` carries no class contract, and three traps live only in a prose document

**Where:** `src/main/java/songscribe/dom/Tempo.java:27`.

**What the code does now.** `Tempo` is the document model's tempo. `Song` holds
it. Both readers build it. The layout code draws it. The MIDI builder plays it. It
has four mutable fields with setters, no `equals` and no `hashCode`, a static
`haveSameValue` in their place, and now a stated but unenforced rule. The file
goes straight from the licence header to the class declaration. There is no class
Javadoc at all.

**What is wrong with it.** All three facts a caller needs are recorded where a
caller will not look:

1. **It is mutable and deliberately has no value equality.** Put one in a hash-based
   collection and it gets identity semantics with no warning. Mutate it afterwards
   and the collection is corrupt. `docs/song-tempo.md` explains it. Nothing on the
   class says to call `haveSameValue` instead of `equals`.
2. **Every tempo the application holds is visible.** Three methods now assert that
   as an invariant. The class does not state it.
3. **The type does not enforce it.** `docs/song-tempo.md` says so and gives a reason
   that does not hold up. Finding 5 covers that.

**What to do instead.** Write a class contract holding the three facts, with a link
to `docs/song-tempo.md` for the full reasoning rather than a paraphrase of it.
Correct the reason in the design note to name the incremental reader.

**So what:** nothing breaks today. A future caller who reaches for `equals`, or who
assumes the type enforces the visibility rule, gets no warning from the one place
they certainly read.

---

## 13. `MeasureMapper.buildTempo` gained two clauses and still documents neither its result nor either parameter

**Where:** `src/main/java/songscribe/io/musicxml/MeasureMapper.java:919-933`.

**What the code does now.** The method turns one MusicXML metronome element into a
`Tempo`, or into null when the element is incomplete or names a beat unit the
reader does not know. This change added an invariant clause and a log clause. The
method still has no `@return`, and no `@param` for either of its two parameters.
One of those parameters is nullable.

**What is wrong with it.** Once a method carries a doc comment, `@return` is
mandatory. The tag is what an editor shows at the call site, so a promise stated
only in the summary is one a caller scrolls past. The nullable parameter matters
more. At the single call site, null is passed when the direction does carry text,
but that text belongs to an annotation rather than to the tempo. Nothing in the
contract says that null means "the words belong to something else" rather than
"there were no words". A second caller would need to tell those apart.

**What to do instead.** Add the `@return` clause, and a `@param` for each
parameter, with the null case spelled out.

---

## 14. The two readers write the same repair twice

**Where:** `src/main/java/songscribe/io/TempoIO.java:150-157` and
`src/main/java/songscribe/io/musicxml/MeasureMapper.java:949-959`.

**What the code does now.** Both methods do the same thing in the same order. Each
asks the visibility rule. Each forces the flag on when the answer is no. Each logs
a warning. `TempoIO` mutates a `Tempo` that exists. `MeasureMapper` flips a local
variable just before it constructs one.

**What is wrong with it.** `Tempo` already owns the predicate for exactly this
reason. The repair — the part that decides to force the flag — is written
independently twice. If the rule ever gains a second condition, or the repair ever
gains a second step, both places have to change, and nothing fails loudly when only
one does.

**What to do instead.** Give `Tempo` the repair, the way it already owns the
predicate: a method that turns the flag on when it must, and answers whether it
did, so each reader logs its own message. Two call sites change and one method is
added.

**Finding 4 removes this entirely**, because there is then nothing to repair. Take
this one only if finding 4 is declined.

---

## 15. Two constructor parameters no longer vary, and two string keys hold identical text

**Where:** `src/main/java/songscribe/ui/dialog/TempoSection.java:92`, with call
sites at `SongSettingsMusicTab.java:51-56` and `TempoChangeDialog.java:45-50`.

**What the code does now.** The constructor takes four things: the dialog's
bindings, the note values for the beat-unit combo, the checkbox label, and the
resource files the description list is read from. Both call sites pass
`Duration.values()`. And after this change's edit to `strings.properties`, both
call sites pass the same label. I checked both keys:
`dialog.song.settings.show.only.description` at line 336 and
`dialog.tempo.change.show.only.description` at line 378 now hold identical text.

**What is wrong with it.** A parameter exists to let something vary. Two of these
four vary at zero call sites. Each is a decision pushed onto every caller with only
one correct answer. A reader who compares the two call sites must check character
by character to learn they are the same. The two string keys are the sharper form
of the same problem: two keys with identical text, for one checkbox, in one shared
panel.

**What to do instead.** Drop the note values and the label from the constructor.
`TempoSection` uses `Duration.values()` and reads one string constant itself. That
leaves the bindings and the file list. Delete whichever key becomes unreferenced.
Four files. If the two dialogs are meant to word the checkbox differently one day,
write that in the panel's contract and keep the parameter — but they are identical
today.

---

## 16. The change quotes a label it deleted, in the design note and in a test

**Where:** `docs/song-tempo.md` line 53 and the new paragraph near line 68;
`src/test/java/songscribe/ui/dialog/TempoSectionTest.java:63`.

**What the code does now.** This change renamed the checkbox in
`strings.properties`. The design note quotes the old wording twice. The second
quotation was added by this change. The new test declares the old wording as a
constant and passes it to the panel.

**What is wrong with it.** Two documents quote a label the application does not
show, and one of them was written in the same change that renamed it. The test
also hard-codes a user-facing string rather than a generated constant, which the
strings guide forbids — and the string it hard-codes is the stale one.

**So what:** nothing breaks. The test does not read the label. Two documents assert
a fact about the interface that is no longer true.

**What to do instead.** Update both quotations. Have the test pass the generated
constant, or pass no label at all under finding 15.

---

## 17. The plan checked in with this change contradicts the code it describes

**Where:** `plans/temposection-bindings.md`, Phase 2 tasks 4 and 5, and Phase 6.

**What the file says now.** Task 4 says not to give `TempoSection` a `Bindings`
parameter, because the section declares no binding. Task 5 says not to add any
enablement rule. The shipped code does both. Every phase is marked complete.
Phase 6's manual checklist quotes the old checkbox label, and it lists no check for
the pair-barring behavior that is the change's whole point.

**What is wrong with it.** The file is staged as a new file in this change. A
reader who follows it is told not to do the thing the code does.

**What to do instead.** Correct tasks 4 and 5 to describe the shipped design.
Update the Phase 6 label. Add the missing manual check.

---

## 18. One fact about the combo's layout is written in three files

**Where:** `OtherValueComboBox.java:83-84` (a private constant),
`OtherValueComboBoxTest.java:38-39`, and `TempoSectionTest.java:60-61`. Each test
declares its own name for the value 0, each with a comment that restates the class
contract.

**What is wrong with it.** "The `(none)` row is the first row" is one promise, and
three files now assert it independently. If the class ever put a row ahead of it,
the class constant would change and both tests would keep passing while they check
the wrong row.

**What to do instead.** Finding 3 deletes the constant with the rest of the barring
machinery, and both test declarations go with the tests that use them. Without
finding 3, widen the constant to package-private, cite it from the class contract,
and have both tests name it.

---

## 19. `Tempo.isVisible` has no direct test

**Where:** there is no `TempoTest.java` anywhere in the test tree.

**What is missing.** `isVisible` is a small pure function over a finite domain:
the description is empty or not, and the flag is on or off. Its contract states an
exact rule. Nothing asserts that table directly. It is reached only through Swing
controls, a legacy XML reader, and a MusicXML reader.

**What to do instead.** Add a parameterized test over all four pairs. It needs no
fixture, no Swing, and no XML. Under finding 4 the same four cases move to the
sealed type's construction.

---

## 20. The MusicXML reader test omits the case its sibling covers

**Where:** `src/test/java/songscribe/io/musicxml/MusicXmlTempoReadTest.java`, which
holds one test.

**What the test does now.** It checks that a hidden metronome with no description
is repaired to visible. `TempoIOTest` holds that case and a second one, which
proves the legacy reader leaves a hidden tempo alone when it does carry a
description.

**What is wrong with it.** `MeasureMapper.buildTempo` makes the same promise.
Nothing checks that a hidden metronome with a description is left alone. If a
future change made the repair unconditional, or inverted the condition, this suite
would not notice — the one existing test already expects the flag forced on.

**What to do instead.** Add the mirror case.

---

## 21. The corpus fixture no longer tests what it is named for, and nothing reads the corpus at all

**Where:** `src/test/java/songscribe/io/musicxml/MusicXmlCorpusGenerator.java:472`,
and the file it writes at `src/test/resources/corpus/synthetic/key-tempo.mssw`.

**What the code does now.** The generator builds a deliberately hidden tempo with
no description — the forbidden pair — attaches it to a note, and writes it into a
checked-in fixture. That fixture is the project's only recorded example of a hidden
metronome.

**What is wrong with it.** After this change, that file loads as a shown tempo and
logs a warning. The fixture named for the hidden case no longer exercises it.

**A separate defect surfaced while I checked this.** Nothing reads the synthetic
corpus. I searched the whole `src` and `scripts` trees: the only references are the
generator itself and `scripts/generate-corpus.sh`. That script still ends by
running `./scripts/test.sh MusicXmlCorpusLosslessnessTest`, and no such class
exists. **So `scripts/generate-corpus.sh` cannot complete today.** This predates
the change.

**What to do instead.** Give the generator's hidden tempo a description, so it stays
a genuine hidden-metronome case, and regenerate the fixture. Separately, decide the
corpus's fate: restore a test that reads it, or remove the corpus, the generator,
and the script. A set of fixtures nothing verifies is upkeep with no return, and the
script that regenerates them fails at its own last step.

---

## 22. Minor: the panel freezes its size before its rules run

**Where:** the end of `TempoSection`'s constructor.

The panel caps its maximum size to its preferred size, and then declares the two
rules. Each rule writes its control at once on declaration. Today neither write
changes a control's size, so nothing is wrong. A third rule that hid or resized a
control would leave the cap computed from a layout that no longer exists, and the
panel would size itself wrongly with nothing to point at the cause. Move the cap
after the rule declarations.

---

## Judged clean

- **Test-only surface.** Every member this change adds, changes, or widens has at
  least one production caller. No test reaches into production internals by
  reflection.
- **Signature rules.** No method exceeds four parameters. No two parameters share a
  type in a way a call site could transpose. The `FontSettingRow.Spec` record
  shrank from four components to three. Swapping the supplier and the consumer for
  the single property they were both views of removes a real transposition hazard.
  That part of the change is a straight improvement.
- **`Tempo.isVisible`'s placement.** The rule is a function of one type's values and
  it lives on that type. Finding 3 objects to how the two dialog call sites use it,
  not to where it sits.
- **The write order in `TempoSection.setTempo`.** Traced against both rules. No
  intermediate state can block the next control's write.
- **The repair reaches every file path.** `buildTempo` has one caller, which feeds
  both the song tempo and every tempo change. `finishTempo` is called from both
  closing-tag handlers.
- **`testSetTempoThenGetTempoRoundTrips`** asserts a real promise through the public
  API. Its four separate assertions are more diagnostic than one `haveSameValue`
  call, which would report only "expected true".
- **The other mechanical rewiring** — `AnnotationDialog`, `FontSettingRow`, the
  three settings tabs, and the two `gather()` bodies — changes no behavior.
- **No efficiency finding.** Nothing here adds blocking or repeated work.
