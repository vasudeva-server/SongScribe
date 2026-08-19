# Non-editable Choice Combos (issue 783)
Replace the two editable `JComboBox`es with a non-editable combo that offers an `Other…` row, which opens a one-field modal dialog for a value the list does not contain.
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Annotation Blank Policy](#-phase-1-annotation-blank-policy) | ✅ Complete | —   |
| 2   | [Strings](#-phase-2-strings) | ✅ Complete | —   |
| 3   | [NonBlank Fallback Removal](#-phase-3-nonblank-fallback-removal) | ✅ Complete | —   |
| 4   | [OtherValueComboBox](#-phase-4-othervaluecombobox) | ✅ Complete | —   |
| 5   | [Prompt Dialog](#-phase-5-prompt-dialog) | ✅ Complete | —   |
| 6   | [AnnotationDialog](#-phase-6-annotationdialog) | ✅ Complete | —   |
| 7   | [TempoSection](#-phase-7-temposection) | ✅ Complete | —   |
| 8   | [UIUtils Deletions](#-phase-8-uiutils-deletions) | ✅ Complete | —   |
| 9   | [Dialogs Guide](#-phase-9-dialogs-guide) | ✅ Complete | —   |
| 10  | [Compile Gate](#-phase-10-compile-gate) | ✅ Complete | —   |
| 11  | [Manual UI Verification](#-phase-11-manual-ui-verification) | ⏳ Pending | —   |

* * *
## ✅ Phase 1: Annotation Blank Policy
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/Annotation.java, src/main/java/songscribe/io/AnnotationIO.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/ui/dialog/AnnotationController.java  
**Recommended model/effort:** Sonnet 4.6, medium effort — no logic to design; the work is restating four contracts accurately against a policy that is already decided

`Annotation.requireText` used to throw `IllegalArgumentException` on blank text. It now logs a warning and stores what it was given. The policy that replaces the throw:

- **The UI never admits blank annotation text.** After this plan's other phases, the annotation combo's items all come from `src/main/resources/conf/annotations` and are non-blank, and the `Other…` prompt disables its OK button while its field is blank. That is where the rule lives and the only place it is enforced.
  
- `Annotation` **itself does not enforce it.** It logs a warning and keeps the value, so a caller that breaks the rule is visible in the log rather than crashing the app.
  
- **A blank annotation read from a file is not attached to a note.** An annotation with no text has nothing to draw, so both readers drop it. This is already the behavior; only the stated reason changes, because it currently cites a rule `Annotation` no longer enforces.
  

Read `.claude/guides/contracts.md` and `.claude/guides/logging.md` before writing the Javadoc. `@log` is a custom Javadoc tag documented in both.
### Tasks
1. Rewrite the `Annotation` class Javadoc (lines 26–35). It currently claims "**Invariant: the text is never blank** … the constructors and `setAnnotation` refuse it, and both readers drop such an annotation rather than building one", which is no longer true of this class. State instead: annotation text is expected to be non-blank; the UI is what guarantees it (the combo offers only non-blank items and the `Other…` prompt refuses a blank entry); this class does not enforce it, and logs a warning if it sees blank text rather than rejecting it; a blank annotation read from a file is dropped by the reader rather than attached.
  
2. Rewrite `Annotation.getAnnotation()`'s `@return` (line 73), which currently promises "never blank". It answers whatever text the annotation was given.
  
3. Delete `Annotation.setAnnotation(String)` (lines 88–91) and its Javadoc. It has no callers anywhere in `src/`: `AnnotationDialog.gather()` and both file readers construct a new `Annotation` rather than mutating an existing one, and `AnnotationController.commit()` does the same. An unexercised public mutator on a value class is debt nothing is checking; delete it while this class's contract is already being rewritten.
  
4. Keep the `@log warning if {@code annotation} is blank` clauses on the two constructors, and extend each to say what happens next — the blank text is stored. `.claude/guides/logging.md` requires this: a warning that leaves the caller holding the bad value promises something different from one that substitutes a default, and the caller cannot tell which by looking.
  
5. In `src/main/java/songscribe/io/AnnotationIO.java`, rewrite the `@return` on the private `build()` method (lines 172–176). It currently justifies dropping a blank annotation with "since {@link Annotation} does not permit one". The behavior stays; the reason becomes that an annotation with no text has nothing to draw, so it is not attached to the note. Add a `@log warning if the accumulated text is blank` clause for the existing `LOG.warn("Corrupt document: annotation with no text, dropping it")` at line 180.
  
6. In `src/main/java/songscribe/io/musicxml/MeasureMapper.java`, method `annotationOf` (line 761): the blank-text branch at lines 772–776 drops the direction silently, where `AnnotationIO` logs it. Add a `LOG.warn` on that branch naming the document as corrupt, matching `AnnotationIO`'s wording — the class already has a `LOG` field and uses it at line 749. Rewrite the comment at lines 772–773, which justifies the drop with "an annotation may not carry blank text", to give the same reason as task 7.
  
7. In the same method, add the missing `@return` tag to `annotationOf`'s Javadoc (lines 753–759) — the method is `@Nullable` and its summary carries the answer in prose, but `.claude/guides/contracts.md` requires the tag, which is what the IDE shows at the call site — and a `@log` clause for the warning added in task 6.
  
8. In `src/main/java/songscribe/ui/dialog/AnnotationController.java`, rewrite the class Javadoc's third paragraph (lines 39–41), which justifies `validate`'s default with "no {@link Annotation} can carry [blank text] — the type refuses it at construction". State the policy as task 1 states it on `Annotation` itself: nothing is refused here because the UI — the non-editable combo plus the `Other…` prompt — guarantees non-blank text before it ever reaches this controller, not because the type enforces it. Rewrite `commit`'s Javadoc (lines 76–77) the same way, replacing "because {@link Annotation} has no blank state to be in" with the same UI-guarantees-it reasoning.
  

* * *
## ✅ Phase 2: Strings
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/resources/songscribe/strings.properties  
**Recommended model/effort:** Haiku 4.5, low effort — five keys inserted in sorted position

Read `.claude/guides/strings.md` first. Keys are alphabetized within their group; insert each in sorted position rather than at the end of its group.

The generated constants are referenced by other phases of this plan (`OtherValueComboBox`, `AnnotationDialog`, `TempoSection`), which run in parallel with this one. The build's dead-key audit runs at the compile gate, by which point those references exist.
### Tasks
1. Add to the `label` group:
  
  - `label.annotation.other.prompt = Enter a custom annotation:`
    
  - `label.none = (none)`
    
  - `label.other = Other…`
    
  - `label.tempo.other.prompt = Enter a tempo description:`
    
2. Add to the `dialog` group: `dialog.tempo.title = Tempo`. Do not add an annotation equivalent — `dialog.annotation.title = Annotation` already exists at line 312 and is reused as the annotation prompt's window title.
  
3. `label.other`'s value ends in a horizontal ellipsis (U+2026, `…`), matching `action.annotation = Annotation…` and its neighbours. If the `Edit` tool cannot emit that character, add the line with the targeted `python3` method shown under _Writing curly quotes/apostrophes_ in `.claude/guides/strings.md`. Never retype the whole file.
  

* * *
## ✅ Phase 3: NonBlank Fallback Removal
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/component/NonBlankGuard.java, src/main/java/songscribe/ui/component/NonBlankTextField.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java  
**Recommended model/effort:** Sonnet 4.6, medium effort — the code change is small; the Javadoc states a weakened class promise and has to state it exactly

`NonBlankGuard`'s `fallback` constructor parameter exists for two reasons, and both are gone. It seeded `previousText` so a restore always had something to put back, and it made `text()` total for a commit path reading the field without a focus change. `text()`'s only caller is `AnnotationDialog.gather()`, which another phase of this plan removes, so `text()` has no callers left.

The class promise weakens from "the field is never blank once focus has left it" to "the field is never blank once focus has left it, unless it has never held a non-blank value". What holds the line instead is `requireValid`: both remaining users of `NonBlankTextField` — `SongSettingsTitleTab` and this plan's `OtherValueDialog` — bind a validity condition over the field's property, so a blank field cannot be committed.
### Tasks
1. In `NonBlankGuard`, change the constructor to `NonBlankGuard(JTextComponent field)`. Delete the `fallback` parameter, its `isBlank()` check and the `IllegalArgumentException` it threw. Initialize `previousText` to `""` — not `@Nullable`; an empty restore needs no extra code and leaves the field empty with the commit already disabled.
  
2. Delete `NonBlankGuard.text()` (lines 98–119) along with its Javadoc.
  
3. Rewrite the `NonBlankGuard` class Javadoc (lines 29–51). Remove the installation example's `fallback` argument, and restate the promise as above, naming `requireValid` as what makes a blank field uncommittable while the guard is what restores a value once the user moves on. Keep the paragraphs on blank-rather-than- empty, on stripping, and on alerting rather than silently restoring.
  
4. Update `shouldYieldFocus`'s `@effects` tag to match: on a blank field it restores the previous value, which is empty until a non-blank value has been remembered.
  
5. In `NonBlankTextField`, change the constructor to `NonBlankTextField(int columns)` and delete the `fallback` parameter, its `@param` and its `@throws`. Rewrite the class Javadoc's promise (lines 22–43) to match task 3.
  
6. In `SongSettingsTitleTab` line 85, change `new NonBlankTextField(TITLE_FIELD_COLUMNS, Strings.get(Strings.DOCUMENT_UNTITLED))` to `new NonBlankTextField(TITLE_FIELD_COLUMNS)`. This is a deliberate behavior change: emptying the title field and moving on now alerts and leaves the field empty instead of inserting "Untitled", and the dialog stops inventing a title the user never typed. OK is already unavailable while the field is blank — `requireValid(computed(() -> !title.get().isBlank()))` at line 159. Leave that line alone. `Strings.DOCUMENT_UNTITLED` has other callers (`Song`, `SongIO`, `MainFrame`), so removing this one does not orphan the key.
  

* * *
## ✅ Phase 4: OtherValueComboBox
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/dialog/OtherValueComboBox.java, src/test/java/songscribe/ui/dialog/OtherValueComboBoxTest.java  
**Recommended model/effort:** Opus 4.8, high effort — the whole design rests on one override's semantics, and the contract has to state promises the JDK's own behavior contradicts

Create `src/main/java/songscribe/ui/dialog/OtherValueComboBox.java`, package `songscribe.ui.dialog`, **package-private** (`final class OtherValueComboBox extends JComboBox<String>`). Both users — `AnnotationDialog` and `TempoSection` — are in that package, so nothing here is public.

The classes this phase calls into are created by another phase running in parallel; their signatures are fixed and given below. Write against them without waiting.

```java
record OtherValuePrompt(String title, String label) {}                    // resolved text, not keys
record OtherValue(String text) implements Copyable<OtherValue> {}
final class OtherValueDialog extends StandardDialog<OtherValue, String> {
    OtherValueDialog(MainFrame mainFrame, OtherValuePrompt prompt, DialogOps<OtherValue, String> ops)
}
final class OtherValueController extends DialogController<OtherValue, String> {
    OtherValueController(MainFrame mainFrame, OtherValueComboBox combo)
    public final DialogOps<OtherValue, String> ops()   // inherited, final on DialogController
}
```
### The design
The model, top to bottom: an optional empty-value row, the items read from the configuration files, any values entered through the prompt this session, then the `Other…` row. `Other…` is always last.

The empty row **is the value** `""`, not a label with a mapping behind it. A `ListCellRenderer` paints `""` as `Strings.get(Strings.LABEL_NONE)` — `(none)` — and everything else verbatim. `getSelectedItem()` therefore answers the real value with no translation layer, which is what lets `Controls.item` be bound over this combo later, and the renderer is also what stops an empty row painting at zero height.

Everything else is one override:

```java
@Override
public void setSelectedItem(Object item) {
    if (item == otherItem) {
        SwingUtilities.invokeLater(this::promptForOther);
        return;
    }

    if (item instanceof String text && model.getIndexOf(text) < 0) {
        model.insertElementAt(text, model.getSize() - 1);
    }

    super.setSelectedItem(item);
}
```

Three things it carries, each of which must appear in the contract as the reason it is written this way:

- **The sentinel is never observable.** An `ActionListener` reacting to `Other…` runs after the selection already is `Other…`, so every other listener — including the one `Controls.item` registers — would see the sentinel as the value, and correctness would depend on listener registration order. Intercepting the write means no observer can ever see it.
  
- **The comparison is by identity, and must stay that way.** `BasicComboPopup` and keyboard selection both route through `JComboBox.setSelectedIndex`, which calls `setSelectedItem(dataModel.getElementAt(i))` — the model's own instance. Identity is also what lets a user enter the literal text `Other…` as a value: it arrives as a different instance and the two never collide.
  
- **A value the model does not contain is inserted, then selected.** A non-editable `JComboBox.setSelectedItem` silently ignores an unknown value. Without the insert, a song carrying an annotation typed before it was ever in the list would open the dialog showing the wrong text, and OK would overwrite the user's annotation.
  

`invokeLater` is required, not stylistic: `setSelectedItem` runs inside the popup's `mouseReleased`, before `setPopupVisible(false)`, so showing a modal dialog there re-enters the event loop with the popup still up. Deferred, the popup closes first, and because the selection never changed, no `ActionEvent` fires — a bound property sees one notification carrying the committed value and never a transient one.

There is no blank handling and no guard. Blank text cannot reach a combo that offers no empty row: `AnnotationDialog` populates from an `Annotation` whose text a blank-refusing UI produced, and the prompt cannot commit a blank value.

Construction cannot re-enter the override. `JComboBox()` selects nothing (its model is empty), `JComboBox.setModel` does not call `setSelectedItem`, and `DefaultComboBoxModel.addElement` selects the first element **on the model**, not through the combo. Do not add a defensive null check for partially-initialized fields.
### Tasks
1. Read `.claude/guides/contracts.md` and `.claude/guides/bindings.md` before writing anything. The class contract is what this phase delivers; the code is short.
  
2. Write the class Javadoc first. It states: what the control is; that `Other…` is always the last row and is never a value; that a value not in the list is added to it rather than ignored; that the empty row is the value `""`, rendered as `(none)`, and exists only when the caller asks for it; that values entered through the prompt live as long as the combo does and are not persisted; and that the class satisfies `Controls.item`'s preconditions — uneditable, always holding a selection — which an editable combo cannot, its value living in its editor rather than its selection.
  
3. Declare the nested enum `EmptyChoice { OFFERED, WITHHELD }`. It exists because a boolean parameter selecting a mode is forbidden by `.claude/rules/java.md`. Document on the enum that `OFFERED` puts the value `""` in the list as its first row, and that a combo whose value may not be empty asks for `WITHHELD`.
  
4. Write the constructor `OtherValueComboBox(OtherValuePrompt prompt, EmptyChoice emptyChoice, String... fileNames)`, with its contract. Store `prompt` in a field — `promptForOther` (task 10) needs it. In order: install a `DefaultComboBoxModel<String>` held in a field; add `""` when `emptyChoice == EmptyChoice.OFFERED`; read each file in `fileNames`; add the `Other…` item last; install the renderer.
  
5. Move the file-reading loop in from `UIUtils.readComboValuesFromFile` (`src/main/java/songscribe/util/UIUtils.java:668-699`) as a private static method of this class, adapted to add to the model rather than to a `JComboBox`. It reads `/conf/<file>` from the classpath, adds one item per line, and reports an I/O failure with `OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_REINSTALL)`. Another phase of this plan deletes the `UIUtils` original, so do not leave the logic in two places.
  
6. Hold the `Other…` item in a `private final String otherItem = Strings.get(Strings.LABEL_OTHER);` field. The identity of that instance is what the override tests, so it must be read once into a field and never re-resolved.
  
7. Install a `ListCellRenderer<? super String>` that renders `""` as `Strings.get(Strings.LABEL_NONE)` and every other value verbatim. Install it whatever the `EmptyChoice` — a `WITHHELD` combo has no `""` item, so the branch is simply never taken, and a conditional install would be a second thing to keep in step.
  
8. Write the `setSelectedItem` override exactly as given above, with a contract stating the three promises and the reason for `invokeLater`. Comment the identity comparison at the line itself — `==` on a `String` is the kind of thing a later reader "fixes".
  
9. Write `String getValue()`, answering the selected item. Its contract states that the result is never the `Other…` sentinel, and is `""` exactly when the empty row is selected.
  
10. Write `private void promptForOther()`: `var mainFrame = MainFrame.getInstance();` then `new OtherValueDialog(mainFrame, prompt, new OtherValueController(mainFrame, this).ops()).setVisible(true);MainFrame` is a singleton and `TupletPopupButton` already reaches it this way. The dialog is modal, so the call returns once the user has answered; a committed value arrives through the controller writing this combo, and a cancel writes nothing.
  
11. Write `src/test/java/songscribe/ui/dialog/OtherValueComboBoxTest.java`, extending `UnitTest`. Two cases, neither of which reaches `promptForOther()` — no `MainFrame` mock is needed:
  
  - Construct a combo (`EmptyChoice.WITHHELD`, a file backing at least one item). Build a `String` equal to but not the same instance as the combo's `Other…` sentinel — `new String(Strings.get(Strings.LABEL_OTHER))` forces a distinct instance despite string interning. Call `setSelectedItem` with it and assert `getValue()` returns that text: the `==` comparison in the override must treat it as an ordinary value, not the sentinel, or this collides with the prompt.
  - Call `setSelectedItem` with a value present in neither the model nor the backing file, and assert `getValue()` returns it and it now appears in the model immediately before the `Other…` row.
  

* * *
## ✅ Phase 5: Prompt Dialog
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/dialog/OtherValue.java, src/main/java/songscribe/ui/dialog/OtherValuePrompt.java, src/main/java/songscribe/ui/dialog/OtherValueDialog.java, src/main/java/songscribe/ui/dialog/OtherValueController.java  
**Recommended model/effort:** Opus 4.8, high effort — four new types crossing the dialog interface, each earning a contract that says why it exists at all

Four package-private types in `songscribe.ui.dialog`, implementing the modal prompt that the `Other…` row opens. Read `.claude/guides/dialogs.md`, `.claude/guides/bindings.md` and `.claude/guides/contracts.md` before writing any of them.

The combo that opens this dialog is created by another phase running in parallel. Its signature, which this phase's controller calls:

```java
final class OtherValueComboBox extends JComboBox<String> {
    String getValue()                              // the selected value, never the Other… sentinel
    @Override public void setSelectedItem(Object)  // inserts a value the list lacks, then selects it
}
```
### Tasks
1. `OtherValuePrompt` — `record OtherValuePrompt(String title, String label) {}`. It carries **resolved** text, not `Strings` keys, matching how every other dialog in this package takes its title. It is a record rather than two parameters because two adjacent transposable `String`s at a call site are what `.claude/rules/java.md` requires a parameter object for. State that in its Javadoc.
  
2. `OtherValue` — `record OtherValue(String text) implements Copyable<OtherValue>`, whose `copy()` returns `this`. Its Javadoc states why it exists: `String` is a JDK type and cannot implement `Copyable`, which `DialogController`'s input bound requires, so wrapping is the only way it crosses the dialog interface. `FontChoice` (`src/main/java/songscribe/ui/dialog/FontChoice.java`) is the same case, written out — mirror its shape and the `@return` on `copy()` explaining that the wrapped type is immutable.
  
3. `OtherValueController extends DialogController<OtherValue, String>`, holding the `OtherValueComboBox` it was constructed with:
  
  - `OtherValueController(MainFrame mainFrame, OtherValueComboBox combo)`
    
  - `read()` → `new OtherValue("")` — the prompt opens on an empty field. It is reached by choosing `Other…`, which asks for a value the list does not offer, so the current selection is by definition not the value being asked for and seeding it would make every use begin by clearing text.
    
  - `commit(String text)` → `combo.setSelectedItem(text)`
    
  - override neither `validate` nor `removal()`; the inherited defaults accept everything and offer no Remove button.
    
  
  Its Javadoc states the thing a reader will ask: this controller touches no document, and exists because `StandardDialog` takes a `DialogOps` and `DialogController.ops()` is the only place one is assembled. Constructing a `DialogOps` directly at the call site would open a second route around the one place `read()`'s answer is copied.
  
4. `OtherValueDialog extends StandardDialog<OtherValue, String>`:
  
  ```java
  private static final int FIELD_COLUMNS = 24;
  private final NonBlankTextField field = new NonBlankTextField(FIELD_COLUMNS);
  private final Property<String> text = Controls.text(field, Timing.WHILE_TYPING);
  
  OtherValueDialog(MainFrame mainFrame, OtherValuePrompt prompt, DialogOps<OtherValue, String> ops) {
      super(mainFrame, prompt.title(), ops);
      addLabeledField(contentPanel, prompt.label(), field, LabelPosition.TOP);
      requireValid(computed(() -> !text.get().isBlank()));
  }
  ```
  
  `NonBlankTextField`'s single-argument constructor is created by another phase of this plan; write against it. `computed` is a static import of `songscribe.ui.binding.ObservableValue.computed`. `KeyChangeDialog:69` is the existing dialog that puts a prompt label above a control this way — follow it rather than hand-building a label and a strut.
  
5. `populate(OtherValue value)` writes through the property — `text.set(value.text())` — then calls `field.rememberCurrentText()` so the guard restores what the user was shown rather than an empty field. Write the value through the property rather than `field.setText`, per `.claude/guides/bindings.md`.
  
6. `gather()` returns `text.get()`, **without stripping**. `StandardDialog` runs the focused field's `InputVerifier` before committing (`StandardDialog.verifyFocusedField`, lines 143–162), and `NonBlankGuard.shouldYieldFocus` strips there. Stripping again here would be a second copy of the rule. State that in `gather()`'s contract so the next reader does not add one.
  
7. Write the class Javadoc for `OtherValueDialog`: one field, no Remove, OK unavailable while the field is blank, and the reason the blank rule is a validity condition rather than a check at OK — the user sees the commit become unavailable at the moment they make it unavailable. Note that the dialog is `OPERATIONAL` (the default category) and opens while another modal dialog is up: `BaseDialog`'s blocking-dialog count is reference-counted and only posts at its 0↔1 transitions (`BaseDialog:315-329`), so nesting is safe and no category override is needed.
  

* * *
## ✅ Phase 6: AnnotationDialog
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/dialog/AnnotationDialog.java  
**Recommended model/effort:** Sonnet 4.6, low effort — a field swap and the deletion of a guard, plus one Javadoc paragraph

`OtherValueComboBox` and `OtherValuePrompt` are created by other phases running in parallel. Their signatures:

```java
final class OtherValueComboBox extends JComboBox<String> {
    OtherValueComboBox(OtherValuePrompt prompt, EmptyChoice emptyChoice, String... fileNames)
    enum EmptyChoice { OFFERED, WITHHELD }
    String getValue()
}
record OtherValuePrompt(String title, String label) {}
```
### Tasks
1. Replace the `annotationCombo` field (line 62) with
  
  ```java
  final OtherValueComboBox annotationCombo = new OtherValueComboBox(
      new OtherValuePrompt(
          Strings.get(Strings.DIALOG_ANNOTATION_TITLE),
          Strings.get(Strings.LABEL_ANNOTATION_OTHER_PROMPT)
      ),
      OtherValueComboBox.EmptyChoice.WITHHELD,
      ANNOTATION_FILE
  );
  ```
  
  `Strings.LABEL_ANNOTATION_OTHER_PROMPT` is added by another phase of this plan, with the value `Enter a custom annotation:`. `DIALOG_ANNOTATION_TITLE` already exists and is this dialog's own window title; the prompt deliberately reuses it rather than adding a second key with the same value. `WITHHELD` because `Annotation` has no meaning without text, so the list offers no empty row.
  
2. Delete from the constructor: the `setEditable(true)` call (line 78), the `UIUtils.readComboValuesFromFile` call (line 79) — the combo now reads the file itself — and the three lines building and installing the `NonBlankGuard` (lines 81–83). Delete the `blankGuard` field (line 73) and the now-unused imports of `JComboBox`, `NonBlankGuard` and `UIUtils`.
  
3. In `populateControls`, delete the `blankGuard.rememberCurrentText()` call (line 143). Leave the `setSelectedItem` call unchanged — the combo now inserts a value the list does not contain and selects it, which is what makes an annotation typed before it was ever in the list open correctly.
  
4. In `gather()`, replace `blankGuard.text()` with `annotationCombo.getValue()`.
  
5. Rewrite the class Javadoc's second and third paragraphs (lines 47–55). Both describe an editable combo: the `NonBlankGuard` on its editor, and reading the editor rather than the selected item because the two can disagree. Replace them with the rule as it now stands — the combo offers a fixed list of non-blank annotations plus an `Other…` row whose prompt cannot commit a blank value, so blank text cannot be entered, and emptying a field is not a way to delete an annotation; the Remove button is. Keep the first paragraph and the reference to `Annotation`.
  

* * *
## ✅ Phase 7: TempoSection
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/dialog/TempoSection.java  
**Recommended model/effort:** Sonnet 4.6, low effort — one field swap and one accessor

`TempoSection` is used by `TempoChangeDialog` (files `"tempochanges", "tempos"`) and `SongSettingsMusicTab` (file `"tempos"`), both of which pass the file names through `TempoSection`'s existing `String... fileNames` parameter. Neither call site changes.

`OtherValueComboBox` and `OtherValuePrompt` are created by other phases running in parallel:

```java
final class OtherValueComboBox extends JComboBox<String> {
    OtherValueComboBox(OtherValuePrompt prompt, EmptyChoice emptyChoice, String... fileNames)
    enum EmptyChoice { OFFERED, WITHHELD }
    String getValue()
}
record OtherValuePrompt(String title, String label) {}
```
### Tasks
1. Change the `tempoDescriptionCombo` field declaration (line 54) from `private final JComboBox<String> tempoDescriptionCombo = new JComboBox<>();` to `private final OtherValueComboBox tempoDescriptionCombo;` — task 3's `.getValue()` call does not exist on `JComboBox<String>`, so the declared type must change. Build it in the constructor, so it can take the `fileNames` parameter:
  
  ```java
  tempoDescriptionCombo = new OtherValueComboBox(
      new OtherValuePrompt(
          Strings.get(Strings.DIALOG_TEMPO_TITLE),
          Strings.get(Strings.LABEL_TEMPO_OTHER_PROMPT)
      ),
      OtherValueComboBox.EmptyChoice.OFFERED,
      fileNames
  );
  ```
  
  `Strings.DIALOG_TEMPO_TITLE` (`Tempo`) and `Strings.LABEL_TEMPO_OTHER_PROMPT` (`Enter a tempo description:`) are added by another phase of this plan. `OFFERED` because a tempo with no description is a real state — `MetronomeContent.forTempo` renders it as the note and BPM alone — and a non-editable combo can only express it as a row, shown as `(none)`.
  
2. Delete the `setEditable(true)` call (line 67) and the `UIUtils.readComboValuesFromFile` loop (lines 69–71); the combo reads the files itself. Delete the now-unused imports of `JComboBox` and `UIUtils`.
  
3. Change `getTempoDescription()` (line 130) to return `tempoDescriptionCombo.getValue()` and rewrite its `@return`: the description as chosen, empty when the user chose `(none)`.
  
4. Leave `setTempo` unchanged. `tempoDescriptionCombo.setSelectedItem(...)` now selects the `(none)` row for a tempo with no description, because that row **is** the value `""`, and inserts-then-selects a description the list does not contain.
  

* * *
## ✅ Phase 8: UIUtils Deletions
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/util/UIUtils.java  
**Recommended model/effort:** Haiku 4.5, low effort — two method deletions

Both methods exist only for the two editable combos that other phases of this plan are removing. `readComboValuesFromFile`'s logic moves into `OtherValueComboBox`; that phase carries the task, so do not leave a copy here.
### Tasks
1. Delete `UIUtils.comboEditor` (lines 645–666) and its Javadoc, which documents why an editable combo's editor and selected item disagree.
  
2. Delete `UIUtils.readComboValuesFromFile` (lines 668–699).
  
3. Delete imports left with no other user in the file — `JComboBox`, `JTextComponent`, and any of `BufferedReader`, `InputStreamReader`, `FileNotFoundException`, `StandardCharsets`, `IOException` no longer referenced. Do not go hunting beyond this file; per `.claude/rules/development.md` unused imports elsewhere are left to the IDE.
  

* * *
## ✅ Phase 9: Dialogs Guide
**Status:** Complete  
**BlockedBy:** —  
**Files:** .claude/guides/dialogs.md  
**Recommended model/effort:** Sonnet 4.6, low effort — one sentence replaced, stated in the present tense
### Tasks
1. Line 237 ends: "`AnnotationDialog` guards a combo box's editor, which no field subclass can carry, so it installs a `NonBlankGuard` directly." That is no longer true — no combo box in the tree is editable and `AnnotationDialog` installs no guard. Replace it with the rule as it now stands: a combo box offering a fixed list plus an `Other…` row states its non-blank rule in the prompt that row opens, which is an ordinary `StandardDialog` with a `NonBlankTextField` and a `requireValid` condition, so the rule is a validity condition like any other rather than a guard on an editor. Keep the rest of the paragraph — `SongSettingsTitleTab`, `requireValid`, and the complementarity of the condition and the field's own guard — intact.
  
2. Do not narrate the change. Per `~/.claude/guides/documents.md`, the guide states what is true now; no "this used to say" note and no dated marker.
  

* * *
## ✅ Phase 10: Compile Gate
**Status:** Complete  
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 9  
**Files:** —  
**Recommended model/effort:** Sonnet 4.6, low effort — run one script, read the output, fix what it reports

This is the plan's only gate. Every other phase leaves an incomplete tree by design, and a mid-flight compile would report the absence of phases that have not run yet.

**Run the unit suite, not e2e.** `PackageDependencyTest` greps import lines for `dom → layout`, `dom → ui` and `layout → ui`; nothing in this plan can affect any of the three. But Phase 4 adds `OtherValueComboBoxTest`, and the unit suite is what runs it.
### Tasks
1. Run `./scripts/compile.sh`. Never `./gradlew`, `gradle`, `javac` or `java -cp`. Fix every error before going on, and report SUCCESS or the errors verbatim.
  
2. Run `./scripts/test.sh` (unit suite; do not pass `e2e`). Fix any failure in `OtherValueComboBoxTest` before going on, and report SUCCESS or the failures verbatim.
  
3. The build's dead-key audit fails if a key added to `strings.properties` is not referenced as the literal text `Strings.<CONSTANT>` under `src/`, and if a key is left with no reference after code using it was deleted. Both directions are exercised by this plan; the audit's output names the keys.
  

* * *
## ⏳ Phase 11: Manual UI Verification
**Status:** Pending  
**BlockedBy:** 10  
**Files:** —  
**Recommended model/effort:** Sonnet 4.6, low effort — drive the app and report; every judgement here is the user's

**This is the whole of the verification for everything but `OtherValueComboBox`'s override logic**, which `OtherValueComboBoxTest` (Phase 4) already covers — do not write a test class for anything else here: the dialog wiring and the UI flow are proved only by running them. `./scripts/run.sh` must never be executed without the user's permission — ask first.
### Tasks
1. Ask the user to run the app, or for permission to run `./scripts/run.sh`.
  
2. Annotation dialog (select a note, open the annotation dialog): the combo cannot be typed into; the list ends with `Other…`; choosing `Other…` opens a one-field dialog titled _Annotation_ labelled _Enter a custom annotation:_, with an empty field whatever the combo was showing; OK is disabled while the field is empty; OK adds the value above `Other…` and selects it; Cancel leaves the previous selection; reopening the dialog on that annotation shows the custom text.
  
3. Tempo: in Song settings → Music and in the tempo change dialog, the description combo offers `(none)` as its first row, and choosing it and committing renders the tempo as the note and BPM with no words. `Other…` opens a dialog titled _Tempo_ labelled _Enter a tempo description:_.
  
4. Song settings → Title: empty the title field and tab away — the alert appears and the field stays empty, OK stays disabled, and "Untitled" is not inserted.
  
5. Report what each step did, and stop for the user's judgement on anything that looks wrong rather than adjusting the design to match what happened.
