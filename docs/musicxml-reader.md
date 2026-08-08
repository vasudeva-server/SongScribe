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
