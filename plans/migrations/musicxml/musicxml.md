# SongScribe → MusicXML Format Mapping
**Type:** Reference (format mapping)  
**Created:** 2026-05-19  
**Status:** Draft  
**Issue:** [#288](https://github.com/vasudeva-server/SongScribe/issues/288)  
**Schema:** [docs/musicxml-4.0-schema/](../../../docs/musicxml-4.0-schema/) — MusicXML 4.0 (`score-partwise`)

* * *
## Purpose
Inventory of every field SongScribe persists and where it maps in MusicXML 4.0, to support converting the document format to MusicXML for interoperability and archival (issue #288). The goal is a **lossless** mapping: MusicXML becomes the canonical on-disk format with zero information loss.

Each row is tagged with a bucket:

- **Native** — direct MusicXML element, read by any MusicXML consumer.
  
- **Native+conv** — native element, but requires a unit or data-shape conversion (staff-space ↔ tenths, line-range ↔ per-note marker, etc.). Bijective.
  
- **Ext** — extension point (`<miscellaneous-field>` or `<other-*>`). Stores the value verbatim; lossless round-trip; not semantically understood by other apps.
  

Source of the inventory: `io/SongIO`, `io/LineIO`, `io/StaffElementIO`, `io/TempoIO`, `io/AnnotationIO`, `io/ViewIO`, and the `model` enums (`ElementType`, `Duration`, `KeyType`, `BeatChange`, `Lyric`).

* * *
## Structural model
SongScribe is **line-centric** with **inline barline/repeat elements**; MusicXML requires `<measure>` containers and is **measure-centric**. The reversible mapping:

- One `<part>` (`<part-list>` with a single `<score-part>`), root `score-partwise`.
  
- **Insert a** `<measure>` **boundary at (a) every SongScribe barline element and (b) every line break.** Each SongScribe line becomes one or more measures.
  
  - A real barline element → `<barline>` with the matching `<bar-style>` (+ `<repeat>` for repeats).
    
  - A forced boundary at a line break with no barline → `<barline><bar-style>none</bar-style></barline>` (invisible).
    
- Line (system) breaks → `<print new-system="yes"/>` on the measure that starts the line. Because every line break is forced to coincide with a measure boundary, this round-trips: on read, an invisible barline coinciding with a system break = line break; a visible one = a real barline element.
  
- Meterless flow → `<attributes><time print-object="no"><senza-misura/></time>`. Choose `<divisions>` to express the dot/duration set.
  
- No clef concept → emit treble `<clef><sign>G</sign><line>2</line></clef>`; ignore on read (treble is the only clef).
  

This corrects two earlier mistaken assumptions: SongScribe **does** model barlines and repeats (as inline `ElementType` values), and note pitch **is** fully derivable (`StaffElement.getPitch()` yields a concrete pitch from staff position + accidental

- key), so `<step>/<octave>/<alter>` is a clean equivalent, not a lossy guess.
  

* * *
## Song header → `<score-partwise>` head
Attribution is being reworked from a single text blob into discrete fields (composer, lyricist, arranger, date, lyrics date, rights, place). The mapping below reflects the **post-rework** shape: composer/lyricist/arranger become native `<creator>` entries and rights becomes a native `<rights>` entry.

This table covers only the **metadata** that lives in the score head (`<movement-title>` / `<movement-number>` / `<identification>`). The on-page **display** of the title and every attribution role is emitted separately as `<credit>` elements — see § "Credits". MusicXML's recommended practice is to carry the same information in both places (metadata for consumers, credits for rendering); the two are intentional duplicates.

**Subtitle is not in this table.** MusicXML has no `<movement-subtitle>` or `<identification>` equivalent for a subtitle. The subtitle's canonical home is its `subtitle` `<credit>` element (see § "Credits"); there is no head-metadata duplicate. SongScribe both writes and reads the subtitle from that credit.

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| `title` (`XML_TITLE`) | `<movement-title>` (bare title; display also as a `title` `<credit>` using `getNumberedTitle()` — title with movement-number prefix) | Native |
| `number` (`XML_NUMBER`) | `<movement-number>` | Native |
| composer (from reworked attribution) | `<identification><creator type="composer">` (display also as a `composer` `<credit>`) | Native |
| lyricist (from reworked attribution) | `<identification><creator type="lyricist">` (display also as a `lyricist` `<credit>`) | Native |
| `isArrangement()` flag (`XML_ARRANGEMENT`) | when `true`: `<identification><creator type="arranger">Sri Chinmoy</creator>` (arranger is always Sri Chinmoy; display also as an `arranger` `<credit>`); when `false`: omit | Native |
| `lyricsSource` (`XML_LYRICS_SOURCE`) — `LyricsSource` enum: `LYRICIST` / `TEXT` / `OTHER` | `<misc-field name="lyrics-source">` storing the enum name; determines the connector used when rendering the lyricist credit ("by" / "from" / ": ") | Ext |
| rights / copyright (from reworked attribution) | `<identification><rights>` (display also as a `rights` `<credit>`) | Native |
| format version (`XML_VERSION`) | `<encoding><software>SongScribe x.y</software>` | Native+conv |
| date — structured y/m/d | `<misc-field name="composition-date">` as ISO 8601 (`1987-12-01`, partial OK); display string as a `composition date` `<credit>` | Ext |
| lyrics date — structured y/m/d | `<misc-field name="lyrics-date">` as ISO 8601 (same partial-date rules; omitted when equal to composition date); display string as a `lyrics date` `<credit>` | Ext |
| place (`XML_PLACE`) | `<misc-field name="composition-place">` (display also as a `place` `<credit>`) | Ext |
| unofficial-translation flag (`XML_UNOFFICIAL_TRANSLATION`) | `<misc-field name="unofficial-translation">` | Ext |

The score-below text blocks — underlyrics, Bangla lyrics, translated lyrics, and footnotes — are **not** stored in `<miscellaneous>`. They are emitted as last-page `<credit>` elements (see § "Credits"), which serve as their canonical on-disk home.
### Date handling
The structured year/month/day is the **source of truth**; the formatted string ("December 1, 1987") is **derived** at render time and must not be the stored value (it is locale/format-dependent and ambiguous to parse back). Store one ISO 8601 `<misc-field>` — fully decomposable to y/m/d, and ISO partial forms (`1987`, `1987-12`, `1987-12-01`) cover SongScribe's existing partial dates (the code gates on `month > 0` / `day > 0`). The formatted display string is also emitted as the `composition date` / `lyrics date` `<credit>` (see § "Credits"); SongScribe ignores those credits on read and reloads the structured date from the ISO misc-field.

The same ISO 8601 rules apply to `<misc-field name="lyrics-date">`, which records when the lyrics were written independently of the music. Omit the field when the lyrics date equals (or is unknown relative to) the composition date; emit it only when the two dates are distinct.

* * *
## Layout → `<defaults>` / `<print>`
| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| line width (`XML_LINE_WIDTH`, ss) | `<scaling>` + `<system-layout>` (ss → tenths, ×10) | Native+conv |
| view fonts: music / lyric / word roles (`LYRICS` / `ANNOTATION`) | `<music-font>`, `<lyric-font>`, `<word-font>` | Native |
| view fonts: `TITLE` / `SUBTITLE` / `ATTRIBUTION` / `BANGLA` / `FOOTNOTE` roles | carried in the `font-family` / `font-size` / `font-weight` attributes of the corresponding `<credit-words>` (see § "Credits") — `SUBTITLE` rides on the `subtitle` `<credit-words>`, which is also its canonical storage (no separate font misc-field) | Native |
| view font: `SUB_ATTRIBUTION` role (secondary attribution lines, e.g., lyrics-source connector) | `font-family` / `font-size` / `font-weight` on the `<credit-words>` elements that render sub-attribution text; stored as `<misc-field name="sub-attribution-font">` and `<misc-field name="sub-attribution-font-size">` for round-trip fidelity | Ext |
| top padding (`XML_TOP_SPACE`, ss) | `<top-system-distance>` (or `<misc-field>` if exact) | Native+conv |
| attribution user Y offset (`XML_ATTRIBUTION_Y_OFFSET`, ss) — user-supplied shift from computed position | `relative-y` on every attribution `<credit-words>` (ss → tenths); `XML_INFO_STARTY` (absolute start Y) is legacy read-only, written by older files only | Native+conv |
| row-height adjustment (`XML_ROW_HEIGHT`, ss) | `<system-distance>` (or `<misc-field>`) | Native+conv |
| lyrics Y position (`XML_LYRICS_YPOS`, ss) | per-`<lyric>` `default-y` | Native+conv |
| element spacing ratio (`XML_NOTE_DIST_CHANGE`) | `<misc-field>` (system-level spacing factor) | Ext |
| dynamic-layout flag (`XML_DYNAMIC_LAYOUT`, always true) | drop, or `<misc-field>` | Ext |

* * *
## Credits → `<credit>`
All on-page text that is not part of the staff — the title, every attribution role, and the score-below text blocks — is emitted as `<credit>` elements, which appear **after** `<defaults>` **and before** `<part-list>`. Each credit carries its own font and position, so the `TITLE` / `ATTRIBUTION` / `BANGLA` / `FOOTNOTE` view-font roles need no separate storage: their `font-family` / `font-size` / `font-weight` ride directly on the `<credit-words>`.

Shape (title shown; every other role is identical except `<credit-type>` and the text):

```xml
<credit>
  <credit-type>title</credit-type>
  <credit-words font-family="…" font-size="…" font-weight="…"
                justify="center" default-x="…" default-y="…">The Title</credit-words>
</credit>
```
### Attribution credits (first page, top)
One credit per populated role; emitted only when the field has a value. The `<credit-type>` is the role name; the `<credit-words>` text is the rendered display string (for dates, the formatted string such as `December 1, 1987`).

| Role | `<credit-type>` | Display text source | Font role |
|---|---|---|---|
| title | `title` | `getNumberedTitle()` (title with movement-number prefix) | `TITLE` |
| subtitle | `subtitle` | `getSubtitle()` | `SUBTITLE` |
| composer | `composer` | composer field | `ATTRIBUTION` |
| lyricist | `lyricist` | lyricist field | `ATTRIBUTION` |
| arranger (only when `isArrangement()=true`; arranger is always Sri Chinmoy) | `arranger` | `Song.SRI_CHINMOY` (hardcoded) | `ATTRIBUTION` |
| composition date | `composition date` | formatted date string | `ATTRIBUTION` |
| lyrics date | `lyrics date` | formatted lyrics-date string (only when distinct from composition date) | `ATTRIBUTION` |
| rights | `rights` | copyright string | `ATTRIBUTION` |
| place | `place` | place field | `ATTRIBUTION` |

The subtitle credit is the **canonical home** for the subtitle text. Unlike the title and attribution credits (which are display-only on read, with their canonical source of truth in the head metadata), subtitle has **no**`<movement-*>` or `<identification>` equivalent in MusicXML. The `subtitle` credit is therefore both **written and read** by SongScribe. It is emitted only when `getSubtitle()` returns a non-empty string.

The remaining credits in this table are **display-only on read**: their canonical source of truth is the head metadata (`<movement-title>`, `<creator>`, `<rights>`) and the `composition-date` / `lyrics-date` / `composition-place` misc-fields. SongScribe re-derives each credit from those on write and ignores the credit text on read.
### Score-below credits (last page)
The text blocks rendered below the score carry a `page` attribute pointing at the last page. SongScribe currently renders a **single page** (`ScoreView.updatePageLayout`), so the last page is page 1 and `page` is always `1`; the attribute generalizes to successive last pages if a multi-page layout is ever added. Unlike the attribution credits, **these credits are the canonical home** for their text — there is no parallel metadata element — so SongScribe both writes and reads them. Their on-page positions come from the rendered component geometry (see the Phase 7 sub-plan, § "Credit positions").

| SongScribe field | `<credit-type>` | Font role | Bucket |
|---|---|---|---|
| underlyrics (`XML_UNDERLYRICS`) | `underlyrics` | `LYRICS` | Native |
| Bangla lyrics (`XML_BANGLA_LYRICS`) | `bangla-lyrics` | `BANGLA` (+ `xml:lang` on `<credit-words>`) | Native |
| translated lyrics (`XML_TRANSLATED_LYRICS`) | `translation` | `LYRICS` | Native |
| footnotes (`XML_FOOTNOTES`) | `footnotes` | `FOOTNOTE` | Native |

The `unofficial-translation` flag qualifies the translated lyrics but is not display text, so it stays a `<misc-field name="unofficial-translation">` rather than a credit.

* * *
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

* * *
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
| X offset (`XML_XPOS`, px) — user shift from the computed X | offset → `<note relative-x>` (px → tenths); computed base X → `<note default-x>`, write-forward only and ignored on read (see § "Position offsets vs. absolute positions") | Native+conv |
| glissando attachment type (`CONNECTED` only) | `<slide>` start/stop | Native |
| ↳ x1/x2 translate (`XML_GLISSANDO_X1/X2_TRANSLATE`) | computed `default-x`/`default-y` on `<slide>` (write-forward, external fidelity only) | Native+conv |
| Fall (note attribute) | `<notations><articulations><falloff>` on that note; read: `<falloff>` → `setFall()` | Native |
| standalone glissando element (`ElementType.GLISSANDO`) | `<glissando>` line notation | Native |
| breath mark (`BREATH_MARK`) | `<articulations><breath-mark>` on preceding note | Native |
| barlines (SINGLE / DOUBLE / FINAL_DOUBLE) | `<barline><bar-style>` regular / light-light / light-heavy | Native |
| repeats (REPEAT_LEFT / RIGHT / LEFT_RIGHT) | `<barline><repeat direction="forward\|backward">` | Native |

* * *
## Lyrics (per verse) → `<lyric>`
| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| verse number (`XML_LYRIC_NUMBER`) | `<lyric number="N">` | Native |
| syllabic (single/begin/middle/end) (`XML_SYLLABIC`) | `<syllabic>` | Native |
| text (`XML_LYRIC_TEXT`) | `<text>` | Native |
| compound-word marker (`COMPOUND_WORD_MARKER`) | non-breaking hyphen (`Constants.NON_BREAKING_HYPHEN`) appended within `<text>` | Native |
| extender (start/stop/continue) (`XML_EXTEND_TAG`) | `<extend type="...">` | Native |

* * *
## Line-level range spans (index pairs → per-note distribution)
These are stored on `Line` as index-pair spans; MusicXML distributes the same information per note. Bijective: expand on write, re-collapse on read.

| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| beaming (`XML_BEAMINGS`) | per-note `<beam>begin/continue/end` | Native+conv |
| ties (`XML_TIES`) | `<tie>` (sound) + `<tied>` (notation) start/stop | Native+conv |
| tuplets (`XML_TUPLETS`) | per-note `<time-modification>` + `<tuplet>` bracket start/stop | Native+conv |
| crescendo / diminuendo (`XML_CRESCENDO` / `XML_DIMINUENDO`) | `<direction><wedge type="crescendo\|diminuendo">` … `stop` | Native+conv |
| trills (`XML_TRILLS`) | `<ornaments><trill-mark>` + `<wavy-line>` start/stop | Native+conv |
| first/second endings (`XML_FSENDINGS`) | `<barline><ending number type="start\|stop">` | Native+conv |

* * *
## Annotations → `<direction>`
| SongScribe field | MusicXML target | Bucket |
|---|---|---|
| text (`name`) | `<direction-type><words>` | Native |
| horizontal alignment (`alignment` / `xAlignment`) | `<words halign>` / `justify` | Native |
| placement above/below (`Placement` enum) | `<direction placement="above\|below">` (see [annotation-placement-refactor.md](./annotation-placement-refactor.md)) | Native |
| user Y offset (`userYOffsetSs`) | `<words relative-y>` (the offset, read back) | Native+conv |
| computed base Y | `<words default-y>` — write-forward, ignored on read | Native+conv |

* * *
## Losslessness
Every persisted field has a home. Native+conv cases are bijective: staff-space ↔ tenths (×10); staff position + key + clef ↔ step/octave/alter; line-range spans ↔ per-note markers; line breaks ↔ invisible-barline-at-system-break. The on-page text and its fonts (title, subtitle, attribution roles, underlyrics, Bangla/translated lyrics, footnotes) all live in native `<credit>` elements, which also absorb the `TITLE` / `SUBTITLE` / `ATTRIBUTION` / `BANGLA` / `FOOTNOTE` view-font roles via their `<credit-words>` attributes. The subtitle credit is the **canonical home** for the subtitle (no `<movement-*>` equivalent exists in MusicXML), so SongScribe reads as well as writes it. The only `<miscellaneous>` residents that remain are genuinely SongScribe-specific score-level data — the structured ISO composition/lyrics dates, composition-place, the `unofficial-translation` flag, and two layout factors — exactly what `<miscellaneous>` exists for, stored verbatim and reloaded exactly.
### Position offsets vs. absolute positions
Some user-adjustable positions are stored in the model as an **offset from a dynamically computed base**, not as an absolute coordinate — a note's `XML_XPOS` (`final = layout.calculateBaseX(note) + xOffset`) and the attribution `XML_ATTRIBUTION_Y_OFFSET` (a shift from the computed attribution start). The base is recomputed by layout on every load; only the offset is persisted.

MusicXML's `default-x`/`default-y` are **absolute** (measure- or page-relative), and `relative-x`/`relative-y` are added on top of them — the rendered position is `default + relative` (`docs/musicxml-4.0-schema/common.mod:304-307`). So for these offset-only fields:

- `relative-x`**/**`relative-y` **carry the offset** — the sole position datum SongScribe reads back.
  
- `default-x`**/**`default-y` **carry the computed base** (the laid-out position _minus_ the offset, so `default + relative` reproduces the on-screen position for external renderers). They are **write-forward only and ignored on read**; SongScribe recomputes the base via layout. This is the same write-forward-only treatment already used for glissando endpoint coordinates.
  

Annotations follow this same rule. `userYOffsetSs` is exactly such an offset (its
Javadoc: *Final Y = calculated position + userYOffsetSs*), so it maps to
`relative-y`, and the computed base maps to `default-y` (ignored on read).
Annotations additionally carry a discrete **above/below** anchor — formerly the
sign of the `yPosPx` `int`, now a `Placement` enum (see
[annotation-placement-refactor.md](./annotation-placement-refactor.md)) — which is
not a position offset and so gets its own native home, `<direction
placement="above|below">`. The old `yPosPx` magnitude beyond that sign was
vestigial: screen layout never read it.
### Legacy read-only fields
These `*IO` constants exist for reading old `.mssw` files and are **not** part of the current write schema, so they need no MusicXML mapping (they vanish on migration): per-element `XML_VOLUME`, `XML_TRILL` (trills are now line-level ranges), `XML_SYLLABLE_MOVEMENT`, `XML_SYLLABLE_RELATION_MOVEMENT`, `XML_FORCE_SYLLABLE`, `XML_INVERT_FRACTION_BEAM_ORIENTATION`, the line-level Y-position fields (`tempoChangeYPos`, `beatChangeYPos`, `firstSecondEndingYPos`, `trillYPos`) superseded by per-instance offsets, and `XML_INFO_STARTY` (`"rightinfostarty"`) — the old absolute attribution start Y, replaced by `XML_ATTRIBUTION_Y_OFFSET` as a user offset from the computed position.

