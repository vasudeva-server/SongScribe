# MusicXML Reader — Parse Structure

Reference diagrams for the SAX read path (`songscribe.io.musicxml`). The classes
themselves carry prose summaries and point here.

MusicXML is the current storage format; `SongIO` and the other legacy-format classes in
`songscribe.io` are read-only migration paths and are not covered here.

---

## The `Where` state-transition graph

`MusicXmlReader` owns the two SAX dispatch switches and the shared parse spine (`where`,
`value`, `song`, `currentLine`, plus the key-carry state). Transitions are driven from
several files via `reader.setWhere(...)`. Ownership is annotated per group; states not yet
extracted remain inline in the orchestrator.

```
  NONE ──<score-partwise>──▶ SCORE_PARTWISE            [orchestrator: lifecycle]

  SCORE_PARTWISE (hub) ──┬─▶ MOVEMENT_TITLE / MOVEMENT_NUMBER   ┐
                         ├─▶ IDENTIFICATION ─▶ CREATOR          │
                         │                   ├▶ RIGHTS          │
                         │                   ├▶ ENCODING ─▶ SOFTWARE / ENCODING_DATE
                         │                   └▶ MISCELLANEOUS ─▶ MISCELLANEOUS_FIELD
                         ├─▶ DEFAULTS ─▶ DEFAULTS_SCALING       │  MusicXmlHeaderReader
                         │            ├▶ DEFAULTS_PAGE_LAYOUT ─▶ DEFAULTS_PAGE_WIDTH
                         │            └▶ DEFAULTS_STAFF_LAYOUT  │
                         ├─▶ CREDIT ─▶ CREDIT_TYPE / CREDIT_WORDS┘
                         ├─▶ PART_LIST ─▶ SCORE_PART            ┐
                         └─▶ PART ─▶ MEASURE (hub)              │  MusicXmlMeasureReader
                                      ├▶ ATTRIBUTES ─▶ KEY ─▶ FIFTHS  (measure leaf states;
                                      ├▶ BARLINE ─▶ BAR_STYLE   │   the MEASURE dispatch
                                      ├▶ <print new-system>     ┘   hub stays inline)
                                      ├▶ NOTE ...               (MusicXmlNoteReader)
                                      └▶ DIRECTION ...          (MusicXmlDirectionReader)
```

---

## `<note>` subtree → `NoteAccumulator` fields

Each child of a `<note>` sets one field on `NoteAccumulator` (reset at every `<note>`
start); `appendStaffElement` assembles them into a `StaffElement` at `</note>`.

```
  MEASURE
    └─ NOTE ── relative-x (attr)        ─► xOffset
         ├─ GRACE  (marker)             ─► isGrace
         ├─ REST   (marker)             ─► isRest
         ├─ PITCH
         │    ├─ STEP   (text)          ─► step
         │    ├─ ALTER  (text)          ─► ignored (pitch from step/octave)
         │    └─ OCTAVE (text)          ─► octave
         ├─ DURATION (text)             ─► ignored (recomputed from type)
         ├─ NOTE_TYPE (text)            ─► typeToken
         ├─ DOT (marker, repeats)       ─► dotCount++
         ├─ ACCIDENTAL (text+attr)      ─► accidental glyph + parentheses
         ├─ STEM (text)                 ─► upper / stemDirectionAuto=false
         ├─ TIE (sound, @type)          ─► ignored (write-forward only)
         ├─ TIME_MODIFICATION
         │    ├─ ACTUAL_NOTES (text)    ─► actualNotes (tuplet grade)
         │    ├─ NORMAL_NOTES (text)    ─► normalNotes
         │    ├─ NORMAL_TYPE  (text)    ─► normalTypeToken
         │    └─ NORMAL_DOT (marker, repeats) ─► normalDotCount++
         ├─ BEAM (@number, text)        ─► beam1Type (number=1 only)
         ├─ NOTATIONS
         │    ├─ ARTICULATIONS
         │    │    ├─ ACCENT      ─► ACCENT articulation
         │    │    ├─ STACCATO    ─► STACCATO articulation
         │    │    ├─ FALLOFF     ─► setFall()
         │    │    └─ BREATH_MARK ─► append BREATH_MARK element after note
         │    ├─ FERMATA            ─► FermataAttachment
         │    ├─ DYNAMICS
         │    │    └─ DYNAMIC_MARK ─► DynamicAttachment
         │    ├─ SLIDE (@type)      ─► slideType (glissando pairing done by
         │    │                        MusicXmlReader.resolveSlide)
         │    ├─ TIED (@type)       ─► tiedStart / tiedStop
         │    ├─ TUPLET (@type,@rel-y) ─► tupletStart / tupletStop
         │    └─ ORNAMENTS
         │         ├─ TRILL_MARK        ─► (decorative; pairing via WAVY_LINE)
         │         └─ WAVY_LINE (@type) ─► trillStart / trillStop
         └─ LYRIC (@number)             ─► one pending Lyric per verse
              ├─ SYLLABIC (text)        ─► syllabic token
              ├─ LYRIC_TEXT (text)      ─► syllable text (compound marker stripped)
              └─ EXTEND (@type)         ─► melisma extender state
```

---

## Pending annotation and the deferred `REPEAT_RIGHT` hold

`AnnotationResolver` holds **exactly one** pending slot, built at `</direction>` and
normally bound to the next-appended element. `BarlineParser`'s deferred `REPEAT_RIGHT`
(held while it might turn out to be the backward half of a straddling
`REPEAT_LEFT_RIGHT` pair) complicates this: the barline is not appended at parse time,
so the pending annotation must ride along with the hold instead of waiting in the
resolver's single slot, or a later element's `<direction>` would overwrite it.

```
                 <direction placement=…> ──▶ AnnotationResolver.pendingAnnotation
                                             │
 ┌───────────────────────────┬───────────────┴────────────────┐
 │                           │                                │
 <barline> = REPEAT_RIGHT    <barline> = other                </note>
 │                           │                                │
 appendOrHold: HOLD          appendOrHold: APPEND             finishNote
 ├ heldRepeatRight != null   ├ append element                 ├ flushHeldRepeatRight
 ├ take endings              └ annotations                    │   ├ append REPEAT_RIGHT
 └ take annotation               .resolveAnnotation(element)  │   └ attachHeldRepeatRight
 │                                                            ├ append note
 │                                                            └ annotations.resolveAnnotation(note)
 next <barline> = REPEAT_LEFT@left
 │
 MERGE (processBarline): append REPEAT_LEFT_RIGHT
 ├ attachHeldRepeatRight (backward half: held endings + held annotation)
 └ endings.attachBarlineEndings (forward half: current barline's endings)
```

Two invariants fall out of this shape:

- **One pending slot.** Only one `<direction>` is ever in flight, and what guarantees
  that is the *writer*: it never emits two annotation directions back to back without
  an intervening element to bind the first to. The reader does not enforce it —
  `endDirection` writes into the single slot unconditionally, so a second annotation
  direction arriving before the first is bound replaces it. That cannot happen in a
  file this program wrote; a file from another program can contain it, and
  `endDirection` logs the dropped annotation rather than losing it silently.
  (`takePendingAnnotation()` clearing the slot on read is what keeps a *bound*
  annotation from binding twice — a different guarantee.)
- **Flush and merge attach only the *held* annotation, never the pending one.**
  `attachHeldRepeatRight` (used by both `flushHeldRepeatRight` and the
  `REPEAT_LEFT_RIGHT` merge branch of `processBarline`) reads the annotation out of the
  `HeldRepeatRight` record, not `AnnotationResolver`'s slot. A resolver-pending
  annotation at flush time belongs to an element that has not been appended yet — the
  `REPEAT_LEFT` whose forward-left barline is still ahead — and falling back to it
  would steal that element's annotation instead of leaving it pending for its own
  append.

---

## Credit routing (`MusicXmlHeaderReader.dispatchCredit`)

The writer emits the same value (composer, dates, place) in **both** the head
(canonical) and a `<credit>` (display-only). The reader must take the head value and
ignore the credit, or a hand-edited credit corrupts the model. Every credit routes into
exactly one of three classes:

| Class | Reader behavior | Members |
| ----- | --------------- | ------- |
| **Canonical** | read into the model | subtitle credit; the four score-below credits; attribution relative-y |
| **Display-only** | ignored, re-derived on write | title credit; composer, lyricist, arranger, date, rights, place credits |
| **Write-forward** | ignored, recomputed or constant | rights, software, encoding-date, supports, scaling, music-font, `default-x`/`default-y` (for an external renderer) |
