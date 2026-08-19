# Review of commit d8da209c — the UI binding framework

> **Status: applied.** Everything below that was approved has been made, compiled
> and run against the unit suite (5 tests, green). Findings 7 and 11 were declined
> and are left as they were; both say so in place. Two things surfaced during the
> work and were not acted on — see *Left alone* at the end.

Three review passes ran over this commit: one on design, one on contracts and API
shape, one on correctness and efficiency. There are no test files in the commit,
so the test-conformance pass did not run. The plan document notes that seven tests
for the dependency tracker were written and retired to the vault repository, which
is why none appear here.

A note before the list. The framework itself is good work. The three-way split
between a value you can read, a value you can write, and a value you can do both
to genuinely makes the compiler reject a binding that could never fire. The
dependency tracker re-collects its inputs on every run and drops the ones a branch
stopped reading, which is what makes a conditional derivation correct. The
re-entrancy suppression is per edge rather than per dialog, which is the detail
that lets the old hand-written "am I adjusting?" flags go away instead of merely
moving. None of that is in question below.

---

## 1. A production bug: a user's centimetres setting is deleted on upgrade

**Where.** `src/main/java/songscribe/prefs/Prefs.java:76`, the list of preference
keys the application deletes from the user's saved settings at startup.

**What the code does now.** The commit replaces a stored yes/no setting called
`metric` (yes meaning centimetres) with a stored setting called `units` holding
either `INCHES` or `CENTIMETERS`. `metric` is added to the obsolete-keys list,
which deletes it from the user's settings file at startup without reading it
first. `units` then falls back to its default, `INCHES`.

**What's wrong with it.** A user who had chosen centimetres opens the upgraded
application and finds inches selected, with no message. By then the old value has
been erased from their settings file, so there is nothing to recover. The
obsolete-keys list is the mechanism for settings that no longer mean anything, and
this one still means something — it only changed how it is spelled.

The commit message describes this as "Turn the METRIC preference into a Units
enum," which reads as a translation rather than a discard, so this looks
unintended rather than decided.

**What to do instead.** Read the old yes/no value before discarding it, and write
`CENTIMETERS` when it was set. Roughly four lines in the existing migration step
in `Prefs`.

Note that finding 6 may make this moot: nothing in the application currently reads
this setting at all, so if the setting goes away the migration question goes with
it.

Confidence: high.

---

# Design findings

## 2. A derived value can never be released, and two documents promise that it is

**Where.** `src/main/java/songscribe/ui/binding/Computed.java` — the class behind
`computed(...)`, which builds a value derived from other values. The promises are
in `ObservableValue.java` (the `computed` documentation) and
`docs/lifecycle.md:131-136`.

**What the code does now.** A dialog derives values by writing something like "the
preview is built from the title, the number and the wrap width." The derivation
works out what it depends on by running once and noting what it read, then
registers itself as a listener on each of those values. When the dialog closes it
disposes its `Bindings` object, which cancels every listener the dialog *declared*.

It does not cancel the listeners a derivation registered on its own inputs.
`Computed` has no teardown method at all — no `dispose`, no `cancel` — and nothing
outside the package can even name the type in order to add one.

**What's wrong with it.** Two contracts say this cleanup happens. The `computed`
documentation says its listeners "are released when a dependency drops out of the
set on a later run, and when the last consumer of the computed is itself
disposed." The first half is true. The second half is not implemented: nothing
counts consumers and nothing releases anything. `docs/lifecycle.md` makes the same
claim about disposal releasing "the dialog, its controls and everything its
transforms and effects captured."

Nothing leaks today. I checked every place a derivation is created, and every
value they read belongs to the same dialog, so the whole cluster becomes garbage
together when the dialog is dropped. The problem is the first derivation built
over something longer-lived — a preference, a value on the main window. That
derivation will keep the dialog, its controls, and everything its body captured
alive for the rest of the session, re-running on every change, computing something
nobody reads. The contract will say it was released. Nothing at runtime will say
otherwise. That is the failure `docs/lifecycle.md` exists to prevent, and it grows
one dialog-opening at a time, which is the hardest kind to notice.

**What to do instead.** Give the derivation to the object that already has a
lifecycle. Move the factory off `ObservableValue` and onto `Bindings`, so a dialog
writes `bindings.computed(...)`; make `Computed` implement `Subscription` so its
`cancel()` drops its input listeners; and have `Bindings.dispose` cancel the
derivations along with the edges. Removing the static factory means a derivation
with no owner stops being expressible, rather than merely discouraged.

**What it touches.** `ObservableValue.java`, `Computed.java`, `Bindings.java`, and
nine call sites across `BaseDialog`, the two converted Song Settings tabs, the date
row and `OtherValueDialog` — each changing from a static call to a call on the
bindings object it already holds. Both contract paragraphs above become true rather
than needing rewording.

I recommend making this change. It uses machinery that already exists — there is
already a disposal owner and already a subscription type — and it turns two false
promises into true ones.

Confidence: high.

## 3. The normalizer's write-back notifies twice, and one fix for it is wrong

**Where.** `src/main/java/songscribe/ui/binding/Controls.java`, the private
`textProperty` method that both `Controls.text` overloads are built on —
specifically its focus-loss handler.

**What the code does now.** `Controls.text` can take a "normalizer": a function
that tidies what the user typed when they click away — trimming spaces, turning
straight quotes into curly ones. On focus loss the handler runs the normalizer,
writes the result back into the field, and then tells everything watching the
field that it changed.

The write-back is not inert. This commit makes the repository's own text fields
(`MyJTextField`, `MyJTextArea`) override `setText` so a programmatic write is
routed into the property, and that routing announces the change on its own. The
handler then announces it a second time.

**What's wrong with it.** Every value derived from one of these fields is
recomputed twice for one user action. This is live on all five normalized fields:
the title and subtitle fields on the Title tab, and place, composer and lyricist on
the Attribution tab. Typing any title containing an apostrophe — "Don't" — and
clicking away rebuilds the title preview twice. Leaving the composer field runs the
"copy the composer into an empty lyricist field" effect twice.

Nothing visibly breaks today, because the second pass is absorbed downstream before
it reaches a Swing write. What is wrong is that an effect registered on one of
these fields runs twice per edit, and no contract says so. A caller who registers a
non-idempotent effect — pushing an undo step, starting playback, posting a message
— gets it done twice, and only on some fields.

There is a second, quieter part: `Timing.WHILE_TYPING` is documented as "notifies
on every keystroke, as the text changes," but a field with a normalizer also
notifies on focus loss with no keystroke involved.

**What to do instead.** The handler should let the write announce the change
instead of announcing it itself. Write the normalized text through the framework's
own write path rather than through the control, and then announce explicitly only
for the commit-timing case, which is the one the write cannot announce. That gives
exactly one notification in every combination and makes the `WHILE_TYPING`
documentation true again. One method, one file.

**One proposal to avoid.** The obvious-looking fix — skip the trailing
announcement whenever a write happened — is wrong. On a plain Swing text field the
repository does not own, a commit-timing write neither routes nor triggers a
document listener, so that field would end up with *no* notification at all. The
fix has to go through the framework's write path, not around it.

Confidence: high; I traced every combination of timing, normalizer outcome, and
owned-versus-plain control.

## 4. Should the stray-write routing exist at all? — I think yes

The two design passes disagreed about this, so it is worth putting the question
squarely rather than burying it.

**What the mechanism is.** `BoundText` stores a field's property inside the Swing
component under a magic key, and `MyJTextField` / `MyJTextArea` override `setText`
to redirect a programmatic write into that property. It exists because a
commit-timing property listens for focus loss, and code writing a field causes no
focus loss — so the write would otherwise go unnoticed.

**The case for deleting it.** No commit-timing field in the tree is currently
written by anything other than its own property, so the mechanism catches no stray
write today. It costs two general-purpose Swing widgets an import of the binding
framework, adds a second re-entrancy flag in a different place from the one
`Binding` already owns, and forces `Controls`'s contract to carry a paragraph
explaining that it works for exactly two classes.

**Why I think it should stay.** The framework's own route table answers this.
Of the six Swing notification routes it lists, five fire on a programmatic write —
document changes, combo box selection, button selection, spinner and slider values
all announce themselves. Exactly one does not: focus loss. So this is not an
arbitrary special case bolted onto text fields; it is the one hole in the model,
and the routing is aimed at it. Deleting it would leave a silent staleness bug
available in precisely the spot the design already identifies as the dangerous one.

Note also that deleting it does **not** fix finding 3. With the routing gone, a
keystroke-timing field still gets one notification from the document write and a
second from the handler. Finding 3 is a separate defect and needs its own fix
either way.

My recommendation is to keep the routing and fix finding 3. I am flagging it
because one review pass recommended deletion and you should see that argument
rather than only my conclusion.

## 5. Two writers now own the OK button's enabled state

**Where.** `src/main/java/songscribe/ui/dialog/StandardDialog.java:117` and
`src/main/java/songscribe/ui/dialog/KeyChangeDialog.java:71,73,89`.

**What the code does now.** The commit adds a general way for a dialog to say "my
values are not ready to be committed": a tab calls `requireValid(...)` with a
condition, the dialog combines all such conditions, and `StandardDialog` binds the
OK button's enabled state to the result. The Song Settings title tab uses this to
grey out OK while the song title is blank.

`KeyChangeDialog` extends `StandardDialog`, so it now inherits that binding. It
also has exactly the same kind of rule — "OK only once you have picked a key
different from the one in effect" — and implements it by hand: disabling OK in the
constructor, disabling it again after populating, and recomputing it from a
listener on the combo box.

**What's wrong with it.** The same button property has two owners that know nothing
about each other. It works today only by accident: `KeyChangeDialog` contributes no
validity condition, so the combined condition never changes, so the binding never
writes again after its first write and the hand-wiring is left in possession. But
the binding believes it last wrote "enabled" while the button is actually disabled,
so the two disagree from the moment the constructor finishes.

The day anyone adds a validity rule to this dialog — the obvious thing to do, since
it has one — the binding will write "enabled" and switch OK back on regardless of
whether the key actually changed, and the user will be able to commit a key change
that changes nothing.

**What to do instead.** Express the rule with the mechanism the commit just added.
View the combo box as a property, hold the key in effect in a `ValueProperty`, and
call `requireValid` with the condition that the two differ. The item listener and
both `setEnabled` calls go. So does a `@Nullable` field on that class whose comment
says "null only before the first populate" — it is nullable only because the
hand-written listener has to read it at a moment when it may not be filled in yet.

One file, roughly a dozen lines net removed, and the OK button has one owner again.

Confidence: high that both writers exist and that the current behaviour survives by
accident. The user-visible bug is latent, not firing today.

## 6. Two enums for "inches or centimetres", and neither has a consumer

**Where.** The new `src/main/java/songscribe/prefs/Units.java` and the existing
`src/main/java/songscribe/util/LengthUnit.java`.

**What the code does now.** The commit creates `Units { INCHES, CENTIMETERS }` to
hold the preference. The codebase already has `LengthUnit { INCHES, CENTIMETERS }`,
which additionally knows the conversion factor, converts in both directions, and
carries the display label for each unit. `LengthUnit`'s own documentation says it
is "chosen by `PrefsKey.METRIC`" — the preference this commit just removed.

**What's wrong with it.** One idea now has two spellings in two packages, and the
older one refers to a preference key that no longer exists. Any code that later
needs to display a length has to choose, and choosing `Units` means rewriting the
conversion arithmetic `LengthUnit` already has.

Underneath that is a larger thing. Neither enum has a production consumer.
`LengthUnit` has none at all. `Units` is read only by the Preferences dialog, to
decide which of its own two radio buttons to select, and written only by those same
radio buttons. Nothing in the application measures anything in the chosen unit. So
this is a setting the user can change that changes nothing — and the commit spent a
new type on it. A note already in `plans/ui-dialog-interface.md:419-421` records
exactly this: the radio "changes nothing the user can see" since the line-width
field was removed.

**Decision: merge the enums, keep the radio buttons.** Delete `Units` and store the
preference as a `LengthUnit`, fixing that class's stale reference to the removed
`PrefsKey.METRIC` at the same time. The radio buttons stay: they move to page setup
when issue #632 is implemented, so the code should not be lost even though nothing
reads the setting today.

Because the setting is kept, finding 1's upgrade migration matters and is fixed too
— a user's centimetres choice should still be theirs when #632 gives it an effect.

Confidence: high on both the duplication and the consumer counts, which I traced
rather than assumed.

## 7. The framework ships surface nobody calls, carrying its deepest contracts

**Where.** `Controls.number`, `Controls.value`, `Controls.choice`,
`Bindings.bindBidirectional` (both overloads), the three-argument merge overload of
`Bindings.bind`, all of `Transform.java`, and the `SKIP` case of
`Binding.InitialWrite`.

**What the code does now.** Both review passes traced production callers for every
public member the framework adds. These have none — not production code, not tests,
nothing but the framework's own cross-references and the plan document. I
spot-checked several myself and confirmed it: the two-way binding methods are
referenced only by each other and by documentation, and `Transform` and the `SKIP`
case exist only to serve them. Together they are roughly 250 lines, most of it
contract prose.

**What's wrong with it.** These are not stubs; they carry the deepest promises in
the package. The two-way binding promises that propagation *terminates* rather than
oscillating, and explains the mechanism that makes it so. `Transform` promises its
two conversion functions round-trip. The merge overload spends a paragraph
explaining why its target parameter is typed differently from its sibling and that
"the difference is not an oversight." Nothing has ever exercised any of it.

Promises made about code nobody runs are the ones that quietly turn out to be
wrong, and the first person to reach for the two-way binding will trust the
termination promise rather than test it. A reader also counts the surface and
concludes the design has been exercised across all of it; it has been exercised
across five adapters and two wiring methods.

There is a smaller thing inside the radio-group adapter. Its private helper throws
with the message "radio group has no button for X, which the factory should have
refused" — the code naming its own dead branch. The factory does refuse it and the
map is copied so it cannot go partial afterwards, so the branch is unreachable.

**Decision: keep them as future surface.** More dialog conversions are coming and
the code should not be lost. No change.

What remains true, and is worth knowing rather than acting on: the first caller of
the two-way binding will be the one who finds out whether the termination promise
holds, because nothing has exercised it. That is a reason to exercise it when that
caller arrives, not a reason to delete the code now.

Confidence: high on the caller counts.

## 8. A domain rule lives on a Swing component, so a derivation reaches through a text field

**Where.** `NumericTextField.isValidValue(String)`, added by this commit, and its
uses in `src/main/java/songscribe/ui/dialog/SongSettingsDateInputRow.java:122`.

**What the code does now.** The date row derives whether the month and day
dropdowns should be usable. Both are written as
`computed(() -> yearField.isValidValue(year.get()))` — take the year text from the
property, then hand it back to the Swing text field to ask whether it is a valid
year. `isValidValue` was added in this commit precisely so the question could be
asked without reading the control, and its contract says so.

**What's wrong with it.** "Is 1993 a valid year for this row?" is a fact about a
number range. It has nothing to do with Swing. But the range is two private fields
on a text field, so the only way to ask is to hold that text field. A derivation is
reaching through a UI component to get at a domain rule.

The cost compounds. Every future derived value that needs to know whether a numeric
field's contents are acceptable must capture the field — which is the pattern
`Controls`'s own documentation warns about ("do not build a property over a control
that outlives the dialog holding the bindings"), and it is unsafe for the same
reason. It also means the rule cannot be stated anywhere a controller's commit-time
validation could call it, which is what the bindings guide asks for: a rule shared
by a binding and a guard is a named function both call.

**What to do instead.** Give the range its own small record — minimum, maximum,
whether blank is acceptable, character limit — with one method answering whether a
string is acceptable. `NumericTextField` takes one and delegates both
`isValidValue` and its focus-time verifier to it. The date row holds the year range
as a constant and writes `computed(() -> YEAR_RANGE.accepts(year.get()))`, which
captures no Swing object at all.

This also answers finding 13 below: that record is the parameter object the
five-argument constructor needs, so the two are one change rather than two.

**What it touches.** `NumericTextField.java`, one new record, the two call sites
that construct a ranged numeric field, and the three derivation sites in the date
row. No behaviour changes.

I recommend making it. It removes the last Swing reference from the date row's
derivations, gives the rule a name a controller could also call, and collapses a
five-argument constructor into a readable one.

Confidence: high that the rule is misplaced; the exact record shape is worth
confirming.

## 9. The attribution preview fills three fields with a constant named for its own meaninglessness

**Where.** `src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java:85`
and the method around `:499-530`.

This arrived in the follow-up commit `a6df2337` rather than the one under review.
It is in the current state of the file and it is a design fault, so it belongs on
the list.

**What the code does now.** The Attribution tab draws a live preview of the credit
block by calling a formatter that turns a song's metadata into lines of text. The
formatter takes the whole 15-field metadata record but reads only the twelve fields
to do with credits, dates and place. So the preview builds a metadata record and
fills the other three — title, number, subtitle — with a constant declared as
`private static final String UNREAD_BY_FORMATTER = ""`, with an eight-line comment
explaining that the formatter reads none of them.

**What's wrong with it.** The tab has to fabricate a value it does not have, cannot
get, and knows will be ignored, and then explain in a comment why the fabrication
is safe. The comment is load-bearing: nothing in the formatter's signature says
those three fields are ignored, so the next person who adds a field to the credit
lines has to find this call site and decide whether the empty strings are still
harmless. The constant name is the previous author pointing straight at the
mistake — the type being passed does not match the information the operation needs.

**What to do instead.** Give the formatter a parameter shaped like what it reads.
Add an accessor on the metadata record returning a smaller record of the twelve
fields the formatter uses, and have the formatter take that. The constant and its
comment go, and adding a field to the credit lines becomes a compile error at every
call site instead of a silent empty string. Other callers hold a real song's
metadata, so they pass the accessor and are otherwise unaffected.

Confidence: high on the defect.

## 10. The date row speaks combo-box positions where the domain has a month

**Where.** `SongSettingsDateInputRow.java` throughout, and the `Controls.itemIndex`
factory that exists to serve it.

**What the code does now.** The month combo is built from a hand-written list of
thirteen strings — an empty one, then twelve month names — arranged so each name's
position equals its month number. The row then views the combo as an integer
*position* rather than a value, and threads that integer through the properties,
the getters, the enable rule and the setter, with `0` meaning "not chosen."

**What's wrong with it.** Correctness rests on an unstated agreement that a list
position equals a month number. Insert a separator, reorder for a different locale,
or drop the leading empty entry, and every getter, the enable rule, the reset logic
and the stored data all silently mean something else, with nothing failing to
compile. The `0`-means-none convention is the same problem in miniature: "absent"
shares a channel with the data.

The in-memory `SongMetadata` record holds month and day as bare integers with `0`
meaning none, and the dialog matched that shape rather than converting at its own
edge. (The persisted form is a single ISO `YYYY-MM-DD` date string — month is never
stored on its own — so this is a question about the in-memory record and the dialog,
not about the file format.)

**Decision: fix the dialog only.** Populate the month combo from a closed set of
month values and view it with `Controls.item`, so the property answers a month
rather than a position. Convert to the integer the record holds in the row's getter
and back in its setter — one line each, and the only place that representation
appears. The enable rule then reads "a month has been chosen." The `SongMetadata`
record and the ISO persistence are left alone.

## 11. A rendering change landed inside a commit about UI binding

**Where.** `src/main/java/songscribe/util/StringUtils.java`, the method that breaks
a title into lines.

The commit replaces the old greedy wrapping with a search that considers every
possible set of line breaks and picks the one minimising total squared unused
width. It is a better algorithm and its contract is unusually good — five separate
result invariants, each of which the implementation could in principle violate,
which is the mark of a contract written from the domain rather than read off the
code.

The finding is that it changes where the line breaks fall in every multi-line title
in every existing document, and it arrived inside a commit titled "add the UI
binding framework and convert the Song Settings dialog." A reader scanning that
commit for the binding framework has no reason to open a string utility, so the
change was never separately looked at against real titles.

One substantive choice inside it: the evenness measure counts the last line's
unused width along with every other line's, where classic line-breaking excludes
it. That is what makes this a *balance* rather than a fill, and it decides what
most titles look like.

**Decision: counting the last line is intended.** No code change. The contract
already states the choice plainly, so nothing needs documenting either.

---

# Contract findings

## 12. Proposed change to an existing contract: rename `onChange`

This one changes what the API says to its callers, so it needs an explicit decision
rather than being folded in with the tidy-ups.

**What it promises now.** `Bindings.onChange(source, action)` and the parameter
named `onChange` on `ObservableValue.observe` are both named for change. Neither
delivers change semantics. `Bindings.onChange`'s contract has to open by correcting
its own name — "runs `action` whenever `source` **notifies**" — and then spends a
bolded paragraph explaining that whether it fires on a real change depends on the
source, because a derived value notifies whenever any of its inputs notifies
regardless of whether the derived value changed. `ObservableValue.observe` does the
same under its own bolded heading, "Notification is not the same as a change." The
bindings guide then says it a third time.

**What it should promise instead.** The same thing, under a name that does not
contradict it. The name is the part of a contract every caller reads and usually
the only part. Someone writing `bindings.onChange(previewText, this::repackWindow)`
reads a promise that the window repacks when the text changes; what they get is a
repack on every keystroke that leaves the text as it was. That is not hypothetical
— it is the mistake the Title tab already has to work around, routing its
subtitle-emptiness through a separate holder value purely to get change semantics
back.

**Why the domain requires it.** When the same correction has to be written three
times in three documents, the name is what is wrong, not the reader.

**The change.** Rename `Bindings.onChange` to `Bindings.onNotify` and the `observe`
parameter to `onNotify`. Six call sites, updated by the rename refactoring. The
three correcting paragraphs shrink to one clause each. The exact replacement name
is open — what matters is that it stops saying "change."

## 13. The `Bindings` class documentation contradicts one of its own methods

The class documentation says, in bold: "**Every method settles what it registers
immediately.** A binding evaluates its source once at registration and writes its
target, so a dialog is consistent the moment it has finished declaring itself."

`onChange` is a method of that class, and its own contract says the opposite:
"Unlike a binding, `action` is **not** run at registration; it runs on
notifications only."

A bolded class-level invariant is exactly what a reader takes at face value. Someone
who does will register an effect expecting it to settle whatever it manages, and
the symptom is a dialog that opens with something in the wrong state until the user
touches the control that drives it. Whether they ever open `onChange`'s own
documentation is the difference between the bug and no bug.

Narrow the class sentence to what is true: every *binding* settles its target
immediately; an effect registered with `onChange` does not run at registration. One
sentence, one file.

## 14. `Controls.itemIndex` documents an exception on the wrong method

Its contract carries `@throws IllegalArgumentException from set, when the index is
neither -1 nor a position the combo holds`. But `itemIndex(...)` never throws that.
The exception comes from writing a bad position into the object it returns, possibly
much later. Its siblings `Controls.item` and `Controls.number` both get this right,
describing the returned object's failure modes inside their `@return` clause.

The `@throws` tag is what an IDE shows at the call site of `itemIndex`. A caller
sees "this call can throw" and either wraps it in a `try`/`catch` that can never
fire or treats the factory as risky, while the condition that can actually fire is
attached to the wrong method. Move the clause into `@return`, matching its two
siblings. One method, one file.

## 15. A method uses a bare `0` on the line below the documentation naming the constant for `0`

`SongSettingsDateInputRow` introduces `NONE_INDEX = 0`, the position of the blank
leading entry meaning "not chosen." `dayEnabled`'s own documentation cites it —
"the selected month index, `{@value #NONE_INDEX}` for none" — and the body one line
below reads `return yearValid && month != 0;`.

Nothing breaks; the two agree today. The cost is that the constant was introduced
to stop `0` meaning two things, and the very method whose documentation cites it
still writes the literal. Someone changing what "not chosen" means will change the
constant, watch the documentation update itself through `{@value}`, and miss this
line — and the day dropdown will enable itself for a month nobody picked.

Related: `NONE_INDEX` is private, but four package-visible members cite it in their
contracts, and their callers have no name for the value those contracts describe.
Per the project's rule that a constant named by a contract takes that contract's
visibility, it should be package-private.

Replace the `0` with `NONE_INDEX` and widen the constant. One file.

## 16. `NumericTextField.hasValidValue()` has a doc comment and no `@return`

The method carries a two-sentence doc comment, returns a boolean, and has no
`@return` tag. The commit rewrote its body. The project rule is mechanical: any
documented method whose return type is not `void` carries the tag, because the tag
is what the IDE shows at the call site. Its new sibling `isValidValue(String)`,
added by the same commit, has one.

Add the tag.

## 17. `NumericTextField`'s five-argument constructor cannot be read at its call site

The constructor reads
`NumericTextField(int columns, int min, int max, boolean isOptional, int maxChars)`,
and its call sites read
`new NumericTextField(YEAR_FIELD_COLUMNS, YEAR_MIN, YEAR_MAX, true, MAX_YEAR_CHARS)`.

Five parameters, three of them adjacent integers a call site could transpose with
the compiler saying nothing — swap the width and the minimum and you get a field one
character wide that accepts years up to 2007. And a bare `true` that names nothing:
the reader has to open the constructor to learn it means "a blank field is
acceptable."

This predates the commit. I am reporting it because the commit changed this file and
both of its call sites, and because a reader arriving at that `true` today has no way
to know what it selects.

The fix is the record from finding 8 — the range, the character limit, and blank
acceptance as a two-valued enum rather than a boolean — so the constructor takes the
column count and that one value. The two findings are one change.

## 18. `FontSettingRow` — six parameters, and a record whose two halves nothing tells apart

The larger `create` overload takes six parameters — a main frame, two labels, a font
key, a supplier and a consumer — with no parameter object. The two adjacent `JLabel`
parameters make the risk concrete: a transposed pair puts the row's caption where the
font description belongs, silently. The smaller five-parameter overload has a doc
comment that documents none of its parameters.

The commit also adds a record `Row(JPanel panel, Disposable chooseAction, Disposable
resetAction)` so the caller can release the two message-bus subscriptions the row's
buttons make. Its two `Disposable` components are adjacent and same-typed, so they
are transposable at the one construction site. Nothing anywhere calls
`chooseAction()` or `resetAction()` — the only thing that reads them is the record's
own `dispose()`, which calls both. The record names two things where the code only
ever has one: "the disposables this row created."

Give `create` a parameter object for the row's inputs, leaving the main frame as the
one separate argument; collapse the record so the two disposables are one value; and
add the missing `@param` tags. One file plus its four call sites.

## 19. `Modality.java` has no license header

Every other Java file in the repository opens with the GNU GPL block, including
`Units.java`, added by the same commit. `Modality.java` starts at its `package`
statement. Copy the eighteen lines.

## 20. `SongSettingsDateInputRow` advertises testability that does not exist here

Its class documentation says the row "exposes pure predicate methods so callers can
unit-test the enable/reset logic without driving Swing," and `dayEnabled` adds
"Pure: no side effects, safe to call from tests."

Under this project's policy tests are retired to a separate vault repository after
they pass, so I cannot tell from inside this worktree whether such a test exists. The
finding stands either way: a contract that justifies a method's shape by naming a
consumer is stating a rationale rather than a promise. Point the comment at the
actual promise — `dayEnabled` is a total function of two values and consults nothing
else — or drop the testability claim.

---

# Correctness and efficiency

The only finding from this pass is the double notification, reported as finding 3
above because its fix is the same edit.

Everything else checked out. The dependency tracker restores its previous recording
state in a `finally`, so a derivation that throws cannot poison later reads. The
observer list is iterated over a copy, so a derivation re-linking itself mid-pass
cannot corrupt the pass in progress. The per-edge re-entrancy flag and the
unchanged-value stop between them make two-way propagation terminate. The
per-opening dialog lifecycle and its disposal chain match `docs/lifecycle.md`. The
new `requireValid` mechanism is correct against the dependency tracker: a condition
not read because an earlier one already failed is correctly not subscribed, and is
picked up when the earlier one passes.

# Test conformance

Did not run — the commit contains no test files.

---

# Left alone

Three things came up while making the changes above.

## `Prefs`'s seven test-only members — fixed

`Prefs` carried `getRawStored(PrefsKey)`, `getRawStored(String)`, `putRawStored`,
`removeObsoleteKeysForTest`, `removeSystemDefaultKeysFromStoreForTest`,
`writeTypedForTest` and `migrateForTest` — seven openings in production surface whose
only callers live in the vault repository, so renaming one compiled here and broke
there with no signal.

They existed for one reason: the startup transformations were private methods on a
singleton mutating its private store, and nothing could get a `Prefs` whose store it
chose. None of them needed the singleton.

The four transformations now live in `PrefsUpgrade`, a package-private class taking
the store, the defaults and the system-default keys, with one `apply(oldPropsFile)`
that runs them in the order they depend on — the `metric` carry-over before obsolete
keys are dropped, since `metric` is one of the keys being dropped — and reports
whether the store changed. `Prefs` builds one over its own store and saves once if it
says so, where it previously wrote the file up to three times during startup. All
seven members are deleted and nothing replaced them: anything exercising the upgrade
constructs a `PrefsUpgrade` over a map it owns and reads that map.

## The same violation stands across the rest of the codebase

Handed off to `plans/test-only-surface.md` — the path
`ui/dialog/AttachmentDialogController.java:55` already cites as the rule it obeys,
so writing it there makes that reference resolve.

A preliminary sweep hits roughly thirty files, not the dozen a first pass
suggested. The handoff carries the two searches that found them (neither is
sufficient alone), the confirmed declarations with line numbers, the two entries
already settled as stale comments rather than violations, and the `PrefsUpgrade`
extraction as the pattern to follow. Completing the inventory is its first task.

## The four unranged `NumericTextField` constructors are gone

Decided during the work: `NumericTextField()`, `(boolean allowDecimal)`,
`(int columns)` and `(int columns, boolean allowDecimal)` had no callers and no
subclasses, so they were removed rather than given a default range.

Worth recording because their removal fixed something quietly: those constructors
left `min` and `max` both at `0`, so `hasValidValue()` on such a field accepted only
the literal `"0"`. Had anything ever used one, it would have been wrong. If they come
back, they should come back with an explicit unbounded range, and the question of
what `hasValidValue()` means on a decimal-accepting field has to be answered then —
an integer range cannot contain `"1.5"`.
