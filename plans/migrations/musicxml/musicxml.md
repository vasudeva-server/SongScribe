# SongScribe → MusicXML Format Mapping

**Type:** Reference (format mapping)  <br>
**Created:** 2026-05-19  <br>
**Status:** Draft  <br>
**Issue:** [#288](https://github.com/vasudeva-server/SongScribe/issues/288)  <br>
**Schema:** [docs/musicxml-4.0-schema/](../../../docs/musicxml-4.0-schema/) — MusicXML 4.0 (`score-partwise`)

---

## Purpose

Inventory of every field SongScribe persists and where it maps in MusicXML 4.0,
to support converting the document format to MusicXML for interoperability and
archival (issue #288). The goal is a **lossless** mapping: MusicXML becomes the
canonical on-disk format with zero information loss.

Each row is tagged with a bucket:

- **Native** — direct MusicXML element, read by any MusicXML consumer.
- **Native+conv** — native element, but requires a unit or data-shape
  conversion (staff-space ↔ tenths, line-range ↔ per-note marker, etc.). Bijective.
- **Ext** — extension point (`<miscellaneous-field>` or `<other-*>`). Stores the
  value verbatim; lossless round-trip; not semantically understood by other apps.

Source of the inventory: `io/SongIO`, `io/LineIO`, `io/StaffElementIO`,
`io/TempoIO`, `io/AnnotationIO`, `io/ViewIO`, and the `model` enums
(`ElementType`, `Duration`, `KeyType`, `BeatChange`, `Lyric`).

---

## Structural model

SongScribe is **line-centric** with **inline barline/repeat elements**; MusicXML
requires `<measure>` containers and is **measure-centric**. The reversible mapping:

- One `<part>` (`<part-list>` with a single `<score-part>`), root `score-partwise`.
- **Insert a `<measure>` boundary at (a) every SongScribe barline element and
  (b) every line break.** Each SongScribe line becomes one or more measures.
  - A real barline element → `<barline>` with the matching `<bar-style>`
    (+ `<repeat>` for repeats).
  - A forced boundary at a line break with no barline →
    `<barline><bar-style>none</bar-style></barline>` (invisible).
- Line (system) breaks → `<print new-system="yes"/>` on the measure that starts
  the line. Because every line break is forced to coincide with a measure
  boundary, this round-trips: on read, an invisible barline coinciding with a
  system break = line break; a visible one = a real barline element.
- Meterless flow → `<attributes><time print-object="no"><senza-misura/></time>`.
  Choose `<divisions>` to express the dot/duration set.
- No clef concept → emit treble `<clef><sign>G</sign><line>2</line></clef>`;
  ignore on read (treble is the only clef).

This corrects two earlier mistaken assumptions: SongScribe **does** model barlines
and repeats (as inline `ElementType` values), and note pitch **is** fully derivable
(`StaffElement.getPitch()` yields a concrete pitch from staff position + accidental
+ key), so `<step>/<octave>/<alter>` is a clean equivalent, not a lossy guess.

---

## Song header → `<score-partwise>` head

Attribution is being reworked from a single text blob into discrete fields
(composer, lyricist, date, place). The mapping below reflects the **post-rework**
shape: composer/lyricist become native `<creator>` entries.

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| `title` (`XML_TITLE`) | `<movement-title>` | Native |
| `number` (`XML_NUMBER`) | `<movement-number>` | Native |
| composer (from reworked attribution) | `<identification><creator type="composer">` | Native |
| lyricist (from reworked attribution) | `<identification><creator type="lyricist">` | Native |
| format version (`XML_VERSION`) | `<encoding><software>SongScribe x.y</software>` | Native+conv |
| date — structured y/m/d | `<misc-field name="composition-date">` as ISO 8601 (`1987-12-01`, partial OK) | Ext |
| lyrics date — structured y/m/d | `<misc-field name="lyrics-date">` as ISO 8601 (same partial-date rules; omitted when equal to composition date) | Ext |
| date — display string ("December 1, 1987") | derived; optional `<credit><credit-words>` for on-page display | Native+conv |
| place (`XML_PLACE`) | `<misc-field name="place">` (optional parallel `<credit-words>`) | Ext |
| underlyrics (`XML_UNDERLYRICS`) | `<misc-field name="underlyrics">` | Ext |
| Bangla lyrics (`XML_BANGLA_LYRICS`) | `<misc-field name="bangla-lyrics">` (+ `xml:lang`) | Ext |
| translated lyrics (`XML_TRANSLATED_LYRICS`) | `<misc-field name="translated-lyrics">` | Ext |
| unofficial-translation flag (`XML_UNOFFICIAL_TRANSLATION`) | `<misc-field name="unofficial-translation">` | Ext |
| footnotes (`XML_FOOTNOTES`) | `<misc-field name="footnotes">` | Ext |

### Date handling

The structured year/month/day is the **source of truth**; the formatted string
("December 1, 1987") is **derived** at render time and must not be the stored
value (it is locale/format-dependent and ambiguous to parse back). Store one
ISO 8601 `<misc-field>` — fully decomposable to y/m/d, and ISO partial forms
(`1987`, `1987-12`, `1987-12-01`) cover SongScribe's existing partial dates
(the code gates on `month > 0` / `day > 0`). Optionally also emit the formatted
string as `<credit-words>` for external display; SongScribe ignores that credit
on read and reloads from the ISO field.

The same ISO 8601 rules apply to `<misc-field name="lyrics-date">`, which
records when the lyrics were written independently of the music. Omit the field
when the lyrics date equals (or is unknown relative to) the composition date;
emit it only when the two dates are distinct.

---

## Layout → `<defaults>` / `<print>`

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| line width (`XML_LINE_WIDTH`, ss) | `<scaling>` + `<system-layout>` (ss → tenths, ×10) | Native+conv |
| view fonts: music / lyric / word roles | `<music-font>`, `<lyric-font>`, `<word-font>` | Native |
| view fonts: title / composer / other roles | `<misc-field name="font-...">` (no native slot) | Ext |
| top padding (`XML_TOP_SPACE`, ss) | `<top-system-distance>` (or `<misc-field>` if exact) | Native+conv |
| attribution start Y (`XML_INFO_STARTY`, ss) | `<credit>` `default-y` (ss → tenths) | Native+conv |
| row-height adjustment (`XML_ROW_HEIGHT`, ss) | `<system-distance>` (or `<misc-field>`) | Native+conv |
| lyrics Y position (`XML_LYRICS_YPOS`, ss) | per-`<lyric>` `default-y` | Native+conv |
| element spacing ratio (`XML_NOTE_DIST_CHANGE`) | `<misc-field>` (system-level spacing factor) | Ext |
| dynamic-layout flag (`XML_DYNAMIC_LAYOUT`, always true) | drop, or `<misc-field>` | Ext |

---

## Per-measure attributes

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| key accidental count + type (`XML_KEYS` + `XML_KEYTYPE`) | `<key><fifths>` — FLATS → −n, SHARPS → +n, NONE → 0 | Native+conv |
| song / per-note tempo (`Tempo`) | `<sound tempo="bpm"/>` + `<direction><direction-type><metronome>` | Native |
| ↳ visible BPM (`visibleTempo`) | `<per-minute>` | Native |
| ↳ beat unit (`tempoType`) | `<beat-unit>` | Native |
| ↳ description (`tempoDescription`) | `<words>` in the same `<direction>` | Native |
| ↳ hide tempo (`shouldShowTempo=false`) | `print-object="no"` on the direction-type | Native |
| metric modulation (`BeatChange` = duration/beat) | `<metronome>` with two `<metronome-note>` + `<metronome-relation>` | Native |

---

## Note / element → `<note>`

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| durations (SEMIBREVE…DEMI_SEMIQUAVER) | `<type>` (whole…32nd) + `<duration>` | Native |
| rests (…_REST) | `<note><rest/>` + `<type>` | Native |
| grace (GRACE_QUAVER) | `<grace/>` + `<type>eighth` | Native |
| staff position (`XML_STAFF_POSITION`) + accidental + key | `<pitch><step><octave>` (+ `<alter>`) via `getPitch()` | Native+conv |
| dot count (`XML_DOTTED`) | `<dot/>` ×n | Native |
| accidental (`XML_PREFIX`) | `<accidental>` + `<alter>` | Native |
| cautionary accidental (`XML_PREFIX_IN_PARENTHESIS`) | `<accidental cautionary="yes">` / `parentheses="yes"` | Native |
| accent (`XML_FORCE_ARTICULATION`) | `<notations><articulations><accent>` | Native |
| staccato (`XML_DURATION_ARTICULATION`) | `<articulations><staccato>` | Native |
| fermata (`XML_FERMATA`) | `<notations><fermata>` | Native |
| dynamic marking (`XML_DYNAMIC`) | `<direction><dynamics>` or `<notations><dynamics>` | Native |
| stem up flag (`XML_UPPER`) + manual flag (`XML_STEM_DIRECTION_AUTO`) | `<stem>up\|down`; auto/manual override flag → note `<other-*>` | Native (+Ext flag) |
| X offset (`XML_XPOS`, px) | `<note default-x>` (px → tenths) | Native+conv |
| glissando attachment type (CONNECTED / SLIDE_OUT) | `<notations><slide>` / `<glissando>` start/stop | Native |
| ↳ x1/x2 translate (`XML_GLISSANDO_X1/X2_TRANSLATE`) | `default-x` on slide/glissando, or `<other-notation>` | Native+conv |
| standalone glissando element (`ElementType.GLISSANDO`) | `<glissando>` line notation | Native |
| breath mark (`BREATH_MARK`) | `<articulations><breath-mark>` on preceding note | Native |
| barlines (SINGLE / DOUBLE / FINAL_DOUBLE) | `<barline><bar-style>` regular / light-light / light-heavy | Native |
| repeats (REPEAT_LEFT / RIGHT / LEFT_RIGHT) | `<barline><repeat direction="forward\|backward">` | Native |

---

## Lyrics (per verse) → `<lyric>`

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| verse number (`XML_LYRIC_NUMBER`) | `<lyric number="N">` | Native |
| syllabic (single/begin/middle/end) (`XML_SYLLABIC`) | `<syllabic>` | Native |
| text (`XML_LYRIC_TEXT`) | `<text>` | Native |
| compound-word marker (`COMPOUND_WORD_MARKER`) | `<elision>` (native compound-syllable mechanism) | Native |
| extender (start/stop/continue) (`XML_EXTEND_TAG`) | `<extend type="...">` | Native |

---

## Line-level range spans (index pairs → per-note distribution)

These are stored on `Line` as index-pair spans; MusicXML distributes the same
information per note. Bijective: expand on write, re-collapse on read.

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| beaming (`XML_BEAMINGS`) | per-note `<beam>begin/continue/end` | Native+conv |
| ties (`XML_TIES`) | `<tie>` (sound) + `<tied>` (notation) start/stop | Native+conv |
| tuplets (`XML_TUPLETS`) | per-note `<time-modification>` + `<tuplet>` bracket start/stop | Native+conv |
| crescendo / diminuendo (`XML_CRESCENDO` / `XML_DIMINUENDO`) | `<direction><wedge type="crescendo\|diminuendo">` … `stop` | Native+conv |
| trills (`XML_TRILLS`) | `<ornaments><trill-mark>` + `<wavy-line>` start/stop | Native+conv |
| first/second endings (`XML_FSENDINGS`) | `<barline><ending number type="start\|stop">` | Native+conv |

---

## Annotations → `<direction>`

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| text (`name`) | `<direction-type><words>` | Native |
| horizontal alignment (`alignment` / `xAlignment`) | `<words halign>` / `justify` | Native |
| Y position (`yPosPx`) | `<words default-y>` (px → tenths) | Native+conv |
| user Y offset (`userYOffsetSs`) | `<words relative-y>` | Native+conv |

---

## Losslessness

Every persisted field has a home. Native+conv cases are bijective:
staff-space ↔ tenths (×10); staff position + key + clef ↔ step/octave/alter;
line-range spans ↔ per-note markers; line breaks ↔ invisible-barline-at-system-break.
The only `<miscellaneous>` residents are genuinely SongScribe-specific score-level
data (underlyrics, Bangla/translated lyrics, footnotes, composition date/place,
extra font roles, two layout factors) — exactly what `<miscellaneous>` exists for,
stored verbatim and reloaded exactly.

### Legacy read-only fields

These `*IO` constants exist for reading old `.mssw` files and are **not** part of
the current write schema, so they need no MusicXML mapping (they vanish on
migration): per-element `XML_VOLUME`, `XML_TRILL` (trills are now line-level
ranges), `XML_SYLLABLE_MOVEMENT`, `XML_SYLLABLE_RELATION_MOVEMENT`,
`XML_FORCE_SYLLABLE`, `XML_INVERT_FRACTION_BEAM_ORIENTATION`, and the line-level
Y-position fields (`tempoChangeYPos`, `beatChangeYPos`, `firstSecondEndingYPos`,
`trillYPos`) superseded by per-instance offsets.
