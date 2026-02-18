# Note Attachments & Modifications Catalog

## Element Positioning Summary

### Above-Staff Elements (Positioned from `max(staff.top, note.top)`)

| Element | Code Field | Margin Type | Stem-Dependent? | Notes |
|---------|-----------|-------------|-----------------|-------|
| Tempo Change | `Note.tempoChange` | From dynamic ref | No | "♩ = 80" style markings |
| Beat Change | `Note.beatChange` | From dynamic ref | No | "♩. = ♩" style markings |
| Trill | `Note.trill` | From dynamic ref | No | "tr" symbol with optional line |
| Fermata | `Note.fermata` | From dynamic ref | Yes (minor) | Pause/hold symbol |
| Annotations (above) | `Note.annotation` (when yPos < 0) | From dynamic ref | No | Rare - most go below |
| First/Second Endings | `Line.firstSecondEndings` | From staff top | No | Bracket with "1." or "2." |

### Below-Staff Elements

| Element | Code Field | Margin Type | Notes |
|---------|-----------|-------------|-------|
| Dynamics | `Note.annotation` (when below) | From staff bottom | "p", "f", "ff", "mf", etc. |
| Crescendo | `Line.crescendo` | From staff bottom | Wedge opening right `<` |
| Diminuendo | `Line.diminuendo` | From staff bottom | Wedge opening left `>` |
| Annotations (below) | `Note.annotation` (when yPos > 0) | From staff bottom | Expression marks |
| Lyrics | `Note.acceleration.syllable` | From staff bottom | Shared baseline across line |

### Note-Attached Elements (Part of Note Bounds)

| Element | Position | Stem-Dependent? | Stacking Order (bottom→top) |
|---------|----------|-----------------|----------------------------|
| Accidental | Left of note head | No | N/A |
| Note head + stem | Base | N/A | N/A |
| Dots | Right of note head | No | N/A |
| Staccato | Opposite stem side | Yes | 1 (closest to note) |
| Accent | Opposite stem side | Yes | 2 (outside staccato) |

**Articulation Stacking (from note outward):**
1. Note head
2. Staccato dot (if present)
3. Accent (if present)

Position: If stem up → articulations below; if stem down → articulations above

### Range-Based Elements (Span Multiple Notes)

| Element | Code Field | Position | Stem-Dependent? | Notes |
|---------|-----------|----------|-----------------|-------|
| Tie | `Line.ties` | Opposite stem | Yes | Above for stem-down, below for stem-up |
| Slur | `Line.slurs` | Opposite stem | Yes | Above for stem-down, below for stem-up |
| Beam | `Line.beamings` | Same as stem | Yes | Above for stem-up, below for stem-down |
| Tuplet | `Line.tuplets` | Opposite stem | Yes | Number + optional bracket |

**Stem Direction Rules:**
- **Stem up** (upper voice):
  - Ties/slurs: below notes
  - Beams: above (at stem tips)
  - Tuplets: below
  - Articulations: below

- **Stem down** (lower voice):
  - Ties/slurs: above notes
  - Beams: below (at stem tips)
  - Tuplets: above
  - Articulations: above

---

## Detailed Element Specifications

### 1. TEMPO & RHYTHM MARKINGS (Above Staff)

#### Tempo Change
- **What**: Musical tempo marking (e.g., "Moderate", "Allegro") with BPM and note value
- **Code**: `Note.tempoChange` → `Tempo` class
- **Position**: Above staff, from `max(staff.top, note.top)`
- **Current Y**: `line.getTempoChangeYPos()` (default: 0)
- **Rendering**: `Renderer.drawTempoChange()`, `FughettaRenderer.drawTempoChangeNote()`
- **Fields**:
  - `visibleTempo` (int): BPM value
  - `tempoType` (enum): SEMIBREVE, MINIM_DOTTED, MINIM, CROTCHET_DOTTED, CROTCHET, QUAVER_DOTTED, QUAVER
  - `tempoDescription` (String): Text label
  - `showTempo` (boolean): Display flag
- **File**: `/src/main/java/songscribe/music/Tempo.java`

#### Beat Change
- **What**: Proportional tempo marking (e.g., "♩. = ♩")
- **Code**: `Note.beatChange` → `BeatChange` enum
- **Position**: Above staff, from `max(staff.top, note.top)`
- **Current Y**: `line.getBeatChangeYPos()` (default: -24)
- **Types**: QUAVER_EQUALS_QUAVER, DOTTED_CROTCHET_EQUALS_MINIM, MINIM_EQUALS_DOTTED_CROTCHET, CROTCHET_EQUALS_DOTTED_CROTCHET, DOTTED_CROTCHET_EQUALS_CROTCHET
- **File**: `/src/main/java/songscribe/music/BeatChange.java`

---

### 2. ACCIDENTALS (Part of Note)

#### Accidental
- **What**: Pitch alteration symbols (♯, ♭, ♮, etc.)
- **Code**: `Note.accidental` → `Accidental` enum
- **Position**: Left of note head
- **Types**: NONE, NATURAL, FLAT, SHARP, DOUBLE_NATURAL, DOUBLE_FLAT, DOUBLE_SHARP, NATURAL_FLAT, NATURAL_SHARP
- **Optional**: Parentheses via `Note.isAccidentalInParentheses`
- **File**: `/src/main/java/songscribe/music/Note.java`

---

### 3. ARTICULATIONS (Part of Note Bounds)

#### Accent (Force Articulation)
- **What**: Emphasis mark (>)
- **Code**: `Note.forceArticulation` → `ForceArticulation.ACCENT`
- **Position**: Opposite stem side (stem up → below, stem down → above)
- **Stacking**: Outside staccato (2nd from note)
- **Current Y Calc**: `getArticulationY()` - ±6 staff positions or ±3 from note
- **Rendering**: `Renderer.drawArticulation()`, `FughettaRenderer.drawArticulation()`
- **File**: `/src/main/java/songscribe/music/ForceArticulation.java`

#### Staccato (Duration Articulation)
- **What**: Shortened note (33% duration), dot symbol
- **Code**: `Note.durationArticulation` → `DurationArticulation.STACCATO`
- **Position**: Opposite stem side, 2 staff positions from note
- **Stacking**: Closest to note (1st from note)
- **Visual**: Small ellipse (3.5×3.5 px)
- **Duration**: 33 units
- **File**: `/src/main/java/songscribe/music/DurationArticulation.java`

#### ~~Tenuto~~ (REMOVE)
- **Status**: ⚠️ **MARK FOR REMOVAL** - Never used
- **Code**: `DurationArticulation.TENUTO`

---

### 4. ORNAMENTS (Above Staff)

#### Trill
- **What**: Rapid alternation with note above
- **Code**: `Note.trill` (boolean)
- **Position**: Above staff, from `max(staff.top, note.top)`
- **Current Y**: `line.getTrillYPos()` (default: -27)
- **Visual**: "tr" text with optional wavy line across span
- **Rendering**: `Renderer.drawTrill()`
- **File**: `/src/main/java/songscribe/music/Note.java`

---

### 5. PAUSE MARKINGS (Above Staff)

#### Fermata
- **What**: Pause/hold symbol
- **Code**: `Note.fermata` (boolean)
- **Position**: Above staff/note, slight adjustment for stem direction
- **Current Y**: -9 (default), -11 (stem up, low note), -5 (stem down, low note)
- **Visual**: ∪ symbol from font "fonts/fermata"
- **Duration**: 1.5× note duration
- **Rendering**: `FughettaRenderer.drawFermata()`
- **File**: `/src/main/java/songscribe/music/Note.java`

#### Breath Mark
- **What**: Breathing point for vocalist
- **Code**: `NoteType.BREATH_MARK` (special note type, extends NotNote)
- **Position**: Fixed yPos = -7
- **Visual**: Symbol with bounds `Rectangle(1, 24, 6, 11)`
- **File**: `/src/main/java/songscribe/music/BreathMark.java`

---

### 6. GLISSANDO (Between Notes)

#### Glissando
- **What**: Smooth pitch slide between notes
- **Code**: `Note.glissando` → `Glissando` class
- **Position**: Between note pitch and target pitch
- **Visual**: Scaled/rotated glissando glyphs
- **Fields**: `pitch` (MIDI target), `x1Translate`, `x2Translate`
- **Rendering**: `Renderer.drawGlissando()`, `FughettaRenderer.drawGlissando()`
- **File**: `/src/main/java/songscribe/music/Note.java`

---

### 7. DYNAMICS (Below Staff)

#### Dynamic Annotations (p, f, ff, mf, etc.)
- **What**: Volume/expression markings
- **Code**: `Note.annotation` → `Annotation` class
- **Position**: Below staff (corrected from catalog)
- **Current Y**: `ABOVE = -4 * NOTE_Y_OFFSET` for above, `8 * NOTE_Y_OFFSET` for below
- **Typical Values**: "f" (forte), "p" (piano), "mf", "ff", "pp", "dim.", "rit."
- **X Alignment**: LEFT, CENTER, or RIGHT
- **Rendering**: `Renderer.drawAnnotation()`
- **File**: `/src/main/java/songscribe/music/Annotation.java`

#### Crescendo
- **What**: Gradually increasing volume
- **Code**: `Line.crescendo` → `IntervalSet` (note-to-note range)
- **Position**: Below staff
- **Current Y**: `score.getNoteYPos(6→5, lineIndex)`
- **Visual**: Opening wedge `<` (diverges left to right)
- **Rendering**: `Renderer.drawCrescendos()` → `drawDynamicMarks()`
- **Data**: `DynamicsIntervalData` (x1Shift, x2Shift, yShift)
- **File**: `/src/main/java/songscribe/music/Line.java`

#### Diminuendo
- **What**: Gradually decreasing volume
- **Code**: `Line.diminuendo` → `IntervalSet`
- **Position**: Below staff
- **Current Y**: `score.getNoteYPos(7→6, lineIndex)`
- **Visual**: Closing wedge `>` (converges left to right)
- **Rendering**: `Renderer.drawDiminuendos()` → `drawDynamicMarks()`
- **File**: `/src/main/java/songscribe/music/Line.java`

---

### 8. TIES & SLURS (Stem-Dependent)

#### Tie
- **What**: Connects two notes of same pitch - second note not articulated
- **Code**: `Line.ties` → `IntervalSet`
- **Position**: Opposite stem side
  - Stem up → tie below notes (`yPos - NOTE_Y_OFFSET - 2`)
  - Stem down → tie above notes (`yPos + NOTE_Y_OFFSET + 2`)
- **Visual**: Curved filled path
- **Rendering**: `Renderer.drawTie()`
- **File**: `/src/main/java/songscribe/music/Line.java`

#### Slur
- **What**: Legato phrase marking across multiple notes
- **Code**: `Line.slurs` → `IntervalSet`
- **Position**: Opposite stem side
  - Stem up → slur below
  - Stem down → slur above
- **Visual**: Curved path following note contour
- **Data**: `SlurData` (X1, X2, Y1, Y2, CtrlY for custom curve)
- **Rendering**: `Renderer.drawSlurs()`
- **File**: `/src/main/java/songscribe/music/Line.java`

---

### 9. BEAMING & TUPLETS (Stem-Dependent)

#### Beams
- **What**: Visual connection of beamable notes (8th, 16th, 32nd)
- **Code**: `Line.beamings` → `IntervalSet`
- **Position**: Same side as stem
  - Stem up → beam above (at stem tips)
  - Stem down → beam below (at stem tips)
- **Visual**: Diagonal beam line(s), multiple levels for subdivisions
- **Rendering**: `Renderer.drawBeamsOnLine()` → `drawBeams()`
- **File**: `/src/main/java/songscribe/music/Line.java`

#### Tuplets
- **What**: Non-standard groupings (triplets, quintuplets, etc.)
- **Code**: `Line.tuplets` → `IntervalSet`
- **Position**: Opposite stem side
  - Stem up → tuplet below
  - Stem down → tuplet above
- **Visual**: Number label (e.g., "3", "5") with optional bracket
- **Rendering**: `Renderer.drawTuplets()`
- **File**: `/src/main/java/songscribe/music/Line.java`

---

### 10. LYRICS (Below Staff, Shared Baseline)

#### Under-Staff Lyrics
- **What**: Main sung text
- **Code**: `Composition.underLyrics` (String) + `Note.acceleration.syllable` (per-note)
- **Position**: Below staff with shared baseline across all syllables in line
- **Current Y**: `score.getUnderLyricsYPos()` + line offset
- **Horizontal**: Each syllable aligns with its note
- **Font**: `composition.getLyricsFont()`
- **Rendering**: `Renderer.drawUnderLyrics()` → `drawLyrics()`
- **File**: `/src/main/java/songscribe/music/Note.java`

#### Syllable Relations
- **What**: Visual connectors between syllables
- **Code**: `Note.acceleration.syllableRelation` → `SyllableRelation` enum
- **Types**: NO, EXTENDER (line), DASH, ONE_DASH
- **Fields**: `syllableMovement`, `syllableRelationMovement`, `forceSyllable`
- **File**: `/src/main/java/songscribe/music/Note.java`

#### Bangla Lyrics
- **Code**: `Composition.banglaLyrics`
- **Position**: Below under-lyrics
- **Margin**: `BANGLA_LYRICS_TOP_MARGIN`

#### Translation
- **Code**: `Composition.translatedLyrics`, `isUnofficialTranslation`
- **Position**: Below Bangla lyrics
- **Margin**: `TRANSLATION_TOP_MARGIN`

---

### 11. ENDING MARKERS (Above Staff)

#### First/Second Endings
- **What**: Repeat structure markers
- **Code**: `Line.firstSecondEndings` → `IntervalSet`
- **Position**: Above staff (from staff top, not dynamic reference)
- **Current Y**: `line.getFirstSecondEndingYPos()` (default: -25)
- **Visual**: Bracket with "1." or "2." label
- **Rendering**: `Renderer.drawEnding()` (abstract)
- **File**: `/src/main/java/songscribe/music/Line.java`

---

### 12. NOTE PROPERTIES (Part of Note)

#### Dots
- **What**: Duration augmentation
- **Code**: `Note.dotCount` (0, 1, or 2)
- **Position**: Right of note head
- **Duration**: 1 dot = 1.5×, 2 dots = 1.75×
- **File**: `/src/main/java/songscribe/music/Note.java`

#### Stem Direction
- **Code**: `Note.upper` (boolean)
- **Affects**: All stem-dependent elements (articulations, ties, slurs, beams, tuplets)
- **True**: Stem up
- **False**: Stem down

---

## Visual Example Reference

From the provided image, element vertical order (top to bottom):

1. **Trill** ("tr") - above staff, attached to first note
2. **Tempo** ("♩. = 80") - above staff, right side
3. **Slur** - above notes (stem-down notes)
4. **Fermata** - above notes
5. **Staff lines** - reference point
6. **Notes** with **staccato dots** below (stem-up notes)
7. **Crescendo/Diminuendo wedges** - below staff
8. **Dynamic marking** ("ff") - below staff, centered between wedges
9. **Lyrics** ("foo bar baz do re mi") - below everything

---

## Box Model Rules

### Content Bounds
- Actual drawn pixels of the element

### Padding Bounds
- Content + padding
- Used for hit testing and intersection detection

### Margin Bounds
- Padding + margin
- Used for layout spacing between elements
- Margins can collapse between adjacent elements

---

## Items Marked for Removal

1. **Tenuto** (`DurationArticulation.TENUTO`) - Never used, remove from codebase
