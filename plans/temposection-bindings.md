# TempoSection Bindings Conversion

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [OtherValueComboBox Write Route](#-phase-1-othervaluecombobox-write-route) | ✅ Complete | — |
| 2 | [TempoSection Property Views](#-phase-2-temposection-property-views) | ✅ Complete | — |
| 3 | [The Two TempoSection Callers](#-phase-3-the-two-temposection-callers) | ✅ Complete | — |
| 4 | [Tests](#-phase-4-tests) | ✅ Complete | — |
| 5 | [Compile and Test Gate](#-phase-5-compile-and-test-gate) | ✅ Complete | — |
| 6 | [Manual UI Verification](#-phase-6-manual-ui-verification) | ⏸️ Blocked by 5 | — |

---

## ✅ Phase 1: OtherValueComboBox Write Route

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/OtherValueComboBox.java, src/main/java/songscribe/ui/dialog/OtherValueController.java, src/main/java/songscribe/ui/dialog/AnnotationDialog.java  <br>
**Recommended model/effort:** Opus, high — decides what a public JDK override promises, and the contract must name the Swing notification route

### Tasks

1. Read these facts before you change anything. They are the whole reason this phase
   exists, and none of them is derivable from the file you are about to edit.
   - `OtherValueComboBox` extends `JComboBox<String>` and never calls `setEditable`, so it
     is always a non-editable combo.
   - `JComboBox.setSelectedItem(Object)` on a non-editable combo scans the model for a row
     equal to the argument. When no row matches, it returns. It changes no selection, it
     fires no `ActionListener`, and it throws nothing.
   - `JComboBox.setSelectedIndex(int)` calls `setSelectedItem`. `setSelectedItem` never
     calls `setSelectedIndex`. The call goes one way only.
   - `OtherValueComboBox.setValue(String)` at line 131 exists solely because
     `setSelectedItem` cannot select a value the model does not hold. It inserts the
     missing row first, then selects it.
   - The `Other…` row is identified by position, and the override of `setSelectedIndex` at
     line 158 is what intercepts it. `BasicComboPopup`, the arrow keys and typeahead all
     reach the combo through `setSelectedIndex`, so that interception is unaffected by
     this phase. Do not move it.

2. Write the Javadoc contract for a new `setSelectedItem` override **before** you write
   its body. State these clauses:
   - It selects the given value, and adds a row for it immediately above `Other…` when the
     list does not already hold one.
   - It never opens the `Other…` prompt, whatever the value. Only `setSelectedIndex`
     does that, and only for the `Other…` position.
   - `null` clears the selection through `super`, which is what `setSelectedIndex(-1)`
     asks for.
   - `@effects` names the row insert.
   - Do not state a `@throws`. The method throws nothing.

3. Add the override to `OtherValueComboBox`. This is the whole body:

   ```java
   @Override
   public void setSelectedItem(@Nullable Object anObject) {
       if (anObject instanceof String text && comboModel.getIndexOf(text) < 0) {
           comboModel.insertElementAt(text, otherIndex());
       }

       super.setSelectedItem(anObject);
   }
   ```

   Do not add a branch for the empty string. It is not a case:
   - In an `EmptyChoice.OFFERED` combo, `""` is the `(none)` row's own stored value, added
     by the constructor at line 102. `getIndexOf("")` answers 0, so nothing is inserted and
     `super` selects that row.
   - In an `EmptyChoice.WITHHELD` combo, `""` cannot arrive. `AnnotationDialog` is the only
     `WITHHELD` caller, and both readers drop a blank annotation before they construct one
     (`src/main/java/songscribe/io/musicxml/MeasureMapper.java:781` and
     `src/main/java/songscribe/io/AnnotationIO.java:180`). A branch there would be a guard
     no caller can reach.

4. Delete `setValue(String)` and its Javadoc from `OtherValueComboBox`.

5. Update the `OtherValueComboBox` class Javadoc at lines 52-54. It currently says
   `{@link #setValue(String)} adds a value the list does not hold, immediately above
   {@code Other…}, and selects it`. Restate that sentence for `setSelectedItem`. Keep the
   rest of the paragraph, which explains why a value entered before it was offered still
   has to show.

6. Keep `getValue()`. It is the typed reader over `(String) getSelectedItem()`, and
   `AnnotationDialog` line 155 still calls it. Its `@return` clause stays accurate.

7. In `OtherValueController`, change `combo.setValue(text)` at line 95 to
   `combo.setSelectedItem(text)`, and change the `{@link OtherValueComboBox#setValue}`
   reference in the `@effects` clause at line 91 to `{@link
   OtherValueComboBox#setSelectedItem}`.

8. In `AnnotationDialog`, change `annotationCombo.setValue(annotation.getAnnotation())` at
   line 134 to `annotationCombo.setSelectedItem(annotation.getAnnotation())`. Change
   nothing else in that class. It is not converted to bindings in this plan.

9. Check every read of the `emptyChoice` field (line 80). After task 4 the only remaining
   read should be the constructor at line 102. If that holds, delete the field and use the
   constructor parameter directly. If some other read remains, leave the field and say
   which read kept it.

---

## ✅ Phase 2: TempoSection Property Views

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/TempoSection.java  <br>
**Recommended model/effort:** Opus, high — decides the section's API and its contract, and the property types are not interchangeable

### Tasks

1. Assume this, because Phase 1 makes it true and you may run before it does:
   `OtherValueComboBox.setSelectedItem` adds a row for a value the list does not hold, then
   selects it. `OtherValueComboBox.setValue` no longer exists. Write no call to `setValue`.

2. Replace the three unnamed numbers in
   `private final SpinnerModel tempoSpinnerModel = new SpinnerNumberModel(120, 40, 220, 1);`
   at `src/main/java/songscribe/ui/dialog/TempoSection.java:54`.
   - Add `private static final int BPM_MIN = 40;` and `private static final int BPM_MAX = 220;`
     to `TempoSection`. They are the range this spinner offers. Do not put them on `Tempo`:
     `Tempo.setVisibleTempo` enforces no range, and a MusicXML file may carry a BPM outside
     it, so `Tempo` would claim a range it does not keep.
   - Read the initial value from the existing `songscribe.dom.Tempo.DEFAULT_BPM` rather than
     the literal `120`.
   - Leave the step argument as the literal `1`.

3. Add a property field for each of the four controls, using
   `songscribe.ui.binding.Controls`:
   - `Controls.item(tempoTypeCombo)` answers `Property<Duration>`.
   - `Controls.number(tempoSpinnerModel)` answers `Property<Number>`.
   - `Controls.item(tempoDescriptionCombo)` answers `Property<String>`, because
     `OtherValueComboBox` extends `JComboBox<String>`.
   - `Controls.selected(showOnlyDescriptionCheckBox)` answers `Property<Boolean>`.

   Type each field as `Property<T>`, which is what `Controls` returns. Assign the two
   fields whose controls are built in the constructor inside the constructor, after the
   control exists.

4. Do **not** give `TempoSection` a `Bindings` parameter. `SongSettingsDateInputRow` takes
   one because it declares a `computed`. This section declares no binding, no `computed`
   and no validity condition, so it has nothing to put in one. A `Bindings` field nothing
   uses is state with no reader.

5. Do **not** add any `Widgets.enabled` rule that disables the note-type combo or the
   spinner while the "show only description" checkbox is selected. Both stay live under
   that checkbox. `Tempo.getTempoType()` is the beat that beams group against and that
   tuplets are measured in — see `Tempo.haveSameBeat` and `docs/song-tempo.md` — and
   `Tempo.getRealTempo()` drives MIDI playback. Neither depends on the mark being drawn.

6. Write the contract for `setTempo(Tempo)` and a new `getTempo()` **before** you write
   either body. `TempoSection` has two callers, so this is the shallow end: an accurate
   name plus the one clause a reader would not predict. That clause is the inversion —
   the checkbox asks whether to show the description alone, and `Tempo.shouldShowTempo()`
   stores the opposite question. `getTempo()` needs a `@return`.

7. Add `Tempo getTempo()`. It builds a `Tempo` from the four properties, and it is the only
   place that inverts the checkbox back into `showTempo`. Move the explanatory comment now
   at `src/main/java/songscribe/ui/dialog/TempoChangeDialog.java:61-62` here, since this
   becomes the one place the inversion is written on the way out.

8. Rewrite `setTempo(Tempo)` to write the four properties instead of the four controls.
   Keep the method name and signature.

9. Delete `getTempoType()`, `getVisibleTempo()`, `getTempoDescription()` and
   `isShowOnlyDescription()`. `getTempo()` replaces all four, and Phase 3 removes their
   only callers.

10. Delete the hand-written `IllegalStateException` in `getTempoType()` along with the
    method. Do not reproduce it anywhere. `Controls.item`'s own `get` already throws
    `IllegalStateException` when a combo has no selection, and its contract states that.

---

## ✅ Phase 3: The Two TempoSection Callers

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java  <br>
**Recommended model/effort:** Sonnet, low — two method bodies and one deleted Javadoc sentence, with the target API named exactly

### Tasks

1. Assume this, because Phase 2 makes it true and you may run before it does:
   `TempoSection` has a method `Tempo getTempo()` that answers the tempo its four controls
   describe, including the inversion of the "show only description" checkbox into
   `Tempo`'s `showTempo`. Its methods `getTempoType()`, `getVisibleTempo()`,
   `getTempoDescription()` and `isShowOnlyDescription()` no longer exist.
   `setTempo(Tempo)` is unchanged.

2. In `src/main/java/songscribe/ui/dialog/SongSettingsMusicTab.java`, replace the body of
   `gather()` (lines 85-92) with `return tempoSection.getTempo();`. Keep the method's
   Javadoc. Change nothing in `populate`, which already calls `tempoSection.setTempo`.

3. In `src/main/java/songscribe/ui/dialog/TempoChangeDialog.java`, replace the body of
   `gather()` (lines 57-67) with `return tempoSection.getTempo();`. The comment inside it
   about the checkbox and `Tempo` asking opposite questions moves to `TempoSection` in
   Phase 2 — delete it here rather than copying it. Change nothing in `populateControls`.

4. Delete the second half of the sentence in the `TempoChangeDialog` class Javadoc at lines
   35-36: `every control, and every rule about which of them is enabled, belongs to
   {@code TempoSection}`. No enablement rule exists in any of these files, and Phase 2
   states why none should be added. Keep the first half — that the dialog owns nothing but
   the section, which the section shares with the song settings.

5. Leave the `SongSettingsMusicTab` class Javadoc alone. Its claim that nothing on the tab
   can be wrong stays true, and so does the list of four controls.

---

## ✅ Phase 4: Tests

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/test/java/songscribe/ui/dialog/OtherValueComboBoxTest.java, src/test/java/songscribe/ui/dialog/TempoSectionTest.java  <br>
**Recommended model/effort:** Sonnet, medium — the promises are stated in Phase 1 and Phase 2; the work is writing them as assertions

### Tasks

1. Before you write each test method, check whether it will sit beside a sibling of the
   same shape. If it will, write both as rows of one parameterized table rather than as two
   methods. `OtherValueComboBoxTest` already uses `@ParameterizedTest` with
   `@EnumSource(OtherValueComboBox.EmptyChoice.class)` for exactly this.

2. Rewrite `testUnknownValueIsAddedAboveOtherAndSelected` in
   `src/test/java/songscribe/ui/dialog/OtherValueComboBoxTest.java`. It calls
   `combo.setValue(unknownValue)` at line 51, and that method no longer exists. Change the
   call to `combo.setSelectedItem(unknownValue)`. The promise it checks is unchanged: a
   value the list did not hold is inserted immediately above `Other…` and selected, and
   `getValue()` answers it. Keep every assertion.

3. Add one test to `OtherValueComboBoxTest` for the case Phase 1 relies on and no existing
   test covers: `setSelectedItem("")` on an `EmptyChoice.OFFERED` combo selects the
   `(none)` row and inserts no row. Assert that `getValue()` answers `""`, and that the
   item count is what it was before the call. This is the promise that keeps add-if-absent
   from growing a second empty row.

4. Add `src/test/java/songscribe/ui/dialog/TempoSectionTest.java`, extending
   `songscribe.UnitTest` as `OtherValueComboBoxTest` does. `UnitTest` installs FlatLaf in a
   `@BeforeAll`, so a `TempoSection` is constructible there. Write one round-trip test:
   build a `TempoSection` with `Duration.values()`, a checkbox label and
   `List.of("tempos")`; call `setTempo` with a `Tempo` whose description is a string the
   `/conf/tempos` resource does not hold and whose `showTempo` is `false`; then assert that
   `getTempo()` answers all four values unchanged. This is the invariant spanning several
   calls that neither the type system nor the four `Controls` contracts can carry on their
   own — the assembly and the checkbox inversion.

5. Do not write a test per control, and do not write a test for the
   `IllegalStateException` that `Controls.item` throws for an empty selection. That is a
   guard, and its promise belongs to `Controls`, not to `TempoSection`.

---

## ✅ Phase 5: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** —  <br>
**Recommended model/effort:** Haiku, low — runs two scripts and reports what they print

### Tasks

1. Run `./scripts/compile.sh --test` from the repository root. It builds the main tree and
   the test tree. Never use `./gradlew`, `gradle`, `javac`, or `java -cp`. Report SUCCESS
   or FAILURE.

2. Fix every compile error before you go on. A test class that still names
   `OtherValueComboBox.setValue` or one of the four deleted `TempoSection` getters is the
   expected shape of a failure here, and Phase 4 should already have removed both.

3. Run `./scripts/test.sh OtherValueComboBoxTest TempoSectionTest`. Never use
   `./gradlew test`. Report the result.

4. Read the output of any failure for its error and location. Do not rerun with extra
   flags. Do not stash to check whether a failure pre-existed. Fix it here.

5. Report a green run as evidence of integration only. It is not evidence that the design
   is right.

---

## ⏸️ Phase 6: Manual UI Verification

**Status:** Pending  <br>
**BlockedBy:** 5  <br>
**Files:** —  <br>
**Recommended model/effort:** Haiku, low — presents the checklist; the user performs the checks

### Tasks

1. Ask the user for permission before you launch the application. Then run
   `./scripts/run.sh`. Never run it without that permission.

2. Ask the user to confirm each of the following, and report which ones failed:
   - Song Settings → Music shows the song's tempo type, BPM, description and checkbox
     exactly as the song carries them.
   - The description combo shows a description the `/conf/tempos` list does not hold. Enter
     one through `Other…`, press OK, reopen Song Settings, and confirm the same description
     is selected rather than `(none)`.
   - The BPM spinner refuses to step below 40 or above 220.
   - The note-type combo and the BPM spinner stay enabled while "Show only the tempo
     description" is checked.
   - The Tempo Change dialog on an element shows and commits the same four values.
   - The annotation combo in the Annotation dialog still offers its list, still opens the
     `Other…` prompt, and still commits a value the list did not hold.
