/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.engraving.Staff;
import songscribe.engraving.StemMetrics;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StemmedNotehead;
import songscribe.smufl.WholeNotehead;

import static songscribe.engraving.BarStroke.DOTS;
import static songscribe.engraving.BarStroke.THICK;
import static songscribe.engraving.BarStroke.THIN;

/**
 * What an element is — a quarter note, an eighth rest, a left repeat — and everything that
 * follows from that alone: its category, its note value where it has one, how it is drawn, and
 * the room its glyph takes on a staff. What varies between two elements of one type — pitch,
 * dots, accidental, a key — lives on the element.
 *
 * <p>Each constant declares one {@link Kind} and one {@link ElementAppearance}, and carries a
 * {@link NoteValue} exactly when its kind is a note, a grace note or a rest. The eleven
 * predicates that name a category answer from the kind alone, and this matrix is the whole of
 * what those eleven promise:
 *
 * <pre>
 *                  NOTE  GRACE_NOTE  REST  BARLINE  REPEAT  BREATH_MARK  KEY_CHANGE
 *   isPitchedNote   ✓
 *   isGraceNote            ✓
 *   isNote          ✓      ✓
 *   isRest                            ✓
 *   isDuration      ✓                 ✓
 *   isBarLine                               ✓
 *   isRepeat                                         ✓
 *   isBar                                   ✓        ✓
 *   isBreathMark                                              ✓
 *   isKeyChange                                                            ✓
 *   isNonDuration                           ✓        ✓        ✓            ✓
 * </pre>
 *
 * The predicates that state a rule rather than name a category — accidental cancellation,
 * lyric-chain behavior, annotation capacity, beaming eligibility, ending-anchor eligibility,
 * and the bar rules below — are not in the matrix, and two of them,
 * {@link #isNoteWithStem()} and {@link #isBeamable()}, do not answer from the kind at all.
 *
 * <p>A grace note sounds a pitch and occupies no time, so it is a note that is neither a duration
 * nor a non-duration: {@link #isDuration()} and {@link #isNonDuration()} do not partition the
 * kinds.
 *
 * <p><b>Which bars may stand where.</b> Five predicates are rules about particular bars rather
 * than about a kind, and are read together: {@link #isValidSongTerminal()}, the bars that may
 * occupy the song's last slot; {@link #isReplaceableByTerminal()}, the bars that slot replaces
 * rather than follows; {@link #isEndingTerminal()}, the bars that can close a first/second-ending
 * selection; {@link #closesRepeat()}, the bars playback loops back from, at which an ending's two
 * brackets split; and {@link #closesSection()}, the bars nothing runs past, on which a volta's
 * closing stroke hangs.
 *
 * <p>An element's Java class follows its type's appearance: {@link #newInstance()} chooses the
 * class from the appearance, and {@link KeyChangeElement}'s own constructors build the one class
 * its type admits. No other path builds an element.
 *
 * <p>The measurements are in staff spaces relative to the element's origin, computed once from
 * the appearance. X grows rightward from the glyph's left origin; Y grows downward from the
 * element's center, so a top offset is negative and a bottom offset positive.
 */
public enum ElementType {
    // Notes
    SEMIBREVE(Kind.NOTE, NoteValue.WHOLE, new NoteheadAppearance(WholeNotehead.WHOLE)),
    MINIM(Kind.NOTE, NoteValue.HALF, new NoteheadAppearance(StemmedNotehead.HALF)),
    CROTCHET(Kind.NOTE, NoteValue.QUARTER, new NoteheadAppearance(StemmedNotehead.BLACK)),
    QUAVER(Kind.NOTE, NoteValue.EIGHTH, new NoteheadAppearance(StemmedNotehead.BLACK)),
    SEMIQUAVER(Kind.NOTE, NoteValue.SIXTEENTH, new NoteheadAppearance(StemmedNotehead.BLACK)),
    DEMI_SEMIQUAVER(Kind.NOTE, NoteValue.THIRTY_SECOND,
        new NoteheadAppearance(StemmedNotehead.BLACK)),

    // Rests
    SEMIBREVE_REST(Kind.REST, NoteValue.WHOLE,
        new DottableGlyphAppearance(SMuFLGlyph.REST_WHOLE, -2)),
    MINIM_REST(Kind.REST, NoteValue.HALF, new DottableGlyphAppearance(SMuFLGlyph.REST_HALF, 0)),
    CROTCHET_REST(Kind.REST, NoteValue.QUARTER,
        new DottableGlyphAppearance(SMuFLGlyph.REST_QUARTER, 0)),
    QUAVER_REST(Kind.REST, NoteValue.EIGHTH, new DottableGlyphAppearance(SMuFLGlyph.REST_8TH, 0)),
    SEMIQUAVER_REST(Kind.REST, NoteValue.SIXTEENTH,
        new DottableGlyphAppearance(SMuFLGlyph.REST_16TH, 0)),
    DEMI_SEMIQUAVER_REST(Kind.REST, NoteValue.THIRTY_SECOND,
        new DottableGlyphAppearance(SMuFLGlyph.REST_32ND, 0)),

    // Grace notes
    GRACE_QUAVER(Kind.GRACE_NOTE, NoteValue.EIGHTH, new NoteheadAppearance(StemmedNotehead.BLACK)),

    // Repeats
    REPEAT_LEFT(Kind.REPEAT, BarAppearance.of(THICK, THIN, DOTS)),
    REPEAT_RIGHT(Kind.REPEAT, BarAppearance.of(DOTS, THIN, THICK)),
    REPEAT_LEFT_RIGHT(Kind.REPEAT, BarAppearance.of(DOTS, THIN, THICK, THIN, DOTS)),

    // Half a staff space above the top staff line. This is the single source for where a
    // breath mark sits: StaffElementRenderer.renderBreathMark() derives the glyph's Y from it and the
    // hit rect is built from it, so the drawn glyph and its clickable area cannot disagree.
    BREATH_MARK(Kind.BREATH_MARK, new PlainGlyphAppearance(SMuFLGlyph.BREATH_MARK_COMMA, -5)),

    // Barlines
    SINGLE_BARLINE(Kind.BARLINE, BarAppearance.SINGLE_BARLINE),
    DOUBLE_BARLINE(Kind.BARLINE, BarAppearance.of(THIN, THIN)),
    FINAL_DOUBLE_BARLINE(Kind.BARLINE, BarAppearance.of(THIN, THICK)),

    // Key changes
    KEY_CHANGE(Kind.KEY_CHANGE, new KeySignatureAppearance());

    /**
     * The category an element type belongs to. Every type has exactly one, and every
     * category predicate on {@link ElementType} is a comparison against it.
     */
    public enum Kind {
        NOTE(Strings.LABEL_ELEMENT_CATEGORY_NOTE),
        GRACE_NOTE(Strings.LABEL_ELEMENT_CATEGORY_GRACE_NOTE),
        REST(Strings.LABEL_ELEMENT_CATEGORY_REST),
        BARLINE(Strings.LABEL_ELEMENT_CATEGORY_BARLINE),
        REPEAT(Strings.LABEL_ELEMENT_CATEGORY_REPEAT),
        BREATH_MARK(Strings.LABEL_ELEMENT_CATEGORY_BREATH_MARK),
        KEY_CHANGE(Strings.LABEL_ELEMENT_CATEGORY_KEY_CHANGE);

        private final String labelKey;

        Kind(String labelKey) {
            this.labelKey = labelKey;
        }

        /**
         * @return the {@link Strings} key of the lowercase word naming this category
         */
        public String labelKey() {
            return labelKey;
        }

        /**
         * @return the lowercase word naming this category, resolved for display
         */
        public String label() {
            return Strings.get(labelKey);
        }
    }

    // ========================================================================
    // Geometry constants — canonical definitions; layout/ui reference these
    // ========================================================================

    /** Uniform scale factor applied to grace note glyphs (75% of normal size). */
    public static final float GRACE_NOTE_SCALE = 0.75f;

    private final Kind kind;
    @Nullable
    private final NoteValue value;
    private final ElementAppearance appearance;
    private final ElementBounds bounds;

    ElementType(Kind kind, NoteValue value, ElementAppearance appearance) {
        this(kind, appearance, value);
    }

    ElementType(Kind kind, ElementAppearance appearance) {
        this(kind, appearance, null);
    }

    private ElementType(Kind kind, ElementAppearance appearance, @Nullable NoteValue value) {
        this.kind = kind;
        this.value = value;
        this.appearance = appearance;
        bounds = computeBounds(kind, value, appearance);
    }

    /**
     * Creates a fresh element of this type.
     * <p>
     * The element's Java class follows the type's appearance, so a caller holding a
     * {@link NoteElement} can ask it for its notehead and one holding a {@link BarElement} for
     * its strokes, each leaf answering from the appearance arm this match guarantees its type
     * carries. A key change starts in C major — the key that draws nothing — because a new
     * element states no key of its own and a caller that means a particular key sets it.
     *
     * @return a new element of this type, on no line, carrying nothing an editor would have
     *     set — no dots, accidental, lyric or attachment
     */
    public StaffElement newInstance() {
        return switch (appearance) {
            case NoteheadAppearance ignored -> new NoteElement(this);
            case GlyphAppearance glyph -> switch (glyph) {
                case DottableGlyphAppearance ignored -> new RestElement(this);
                case PlainGlyphAppearance ignored -> new BreathMarkElement(this);
            };
            case BarAppearance ignored -> new BarElement(this);
            case KeySignatureAppearance ignored -> new KeyChangeElement(Key.NO_ACCIDENTALS);
        };
    }

    /**
     * @return the width an element of this type advances a column by: its glyph, and for a
     *     stemmed note the flag where that reaches past the notehead
     */
    public double getFullElementWidthSs() {
        return bounds.fullWidthSs();
    }

    /**
     * @param direction the stem direction, which decides whether the stem reaches above or below
     *                  the notehead
     * @return the height of an element of this type from its highest to its lowest ink with its
     *     stem in {@code direction}; the glyph's own height for a type with no stem
     */
    public double getElementHeightSs(Direction direction) {
        return direction.isUp() ? bounds.heightUpSs() : bounds.heightDownSs();
    }

    /**
     * The right edge of this type's own glyph — the notehead alone for a note, with no stem, flag
     * or dot — measured from the origin rather than as a bounding-box width, so that a glyph whose
     * ink starts right of its origin is still placed by where its ink ends.
     *
     * <p>This is the one answer to how wide an element's own glyph is. Reading the black
     * notehead's box from {@link SMuFLMetadata#bboxSs} instead leaves a whole note about half a
     * staff space short (refs #694).
     *
     * @return the X of the glyph's right edge, grace-scaled for a grace note
     */
    public double getElementWidthSs() {
        return bounds.baseWidthSs();
    }

    /**
     * @return the X midway between the origin and {@link #getElementWidthSs()}: the center of the
     *     glyph alone, never of a flag
     */
    public double getElementCenterXSs() {
        return bounds.baseWidthSs() / 2;
    }

    /**
     * The X at which an element of this type sits with its glyph's right edge on a line's right
     * edge.
     *
     * @param lineWidthSs the line width in staff spaces
     * @return {@code lineWidthSs} less {@link #getElementWidthSs()}, in staff spaces
     */
    public double flushRightXSs(double lineWidthSs) {
        return lineWidthSs - bounds.baseWidthSs();
    }

    /**
     * @return the height of this type's glyph alone, with no stem
     */
    public double getFullElementHeightSs() {
        return bounds.fullElementHeightSs();
    }

    /**
     * @return the Y of the top of this type's glyph — the notehead for a note, the whole glyph
     *     for a rest, half the staff above center for a bar — negative, and the same as
     *     {@link #getTopYOffsetSs} for a type with no stem
     */
    public double getNoteheadTopOffsetSs() {
        return bounds.noteheadTopOffsetSs();
    }

    /**
     * @return the Y of the bottom of this type's glyph, positive; the counterpart of
     *     {@link #getNoteheadTopOffsetSs()}
     */
    public double getNoteheadBottomOffsetSs() {
        return bounds.noteheadTopOffsetSs() + bounds.fullElementHeightSs();
    }

    /**
     * @param direction the stem direction, which decides whether the stem tip or the notehead is
     *                  the element's highest ink
     * @return the Y of the highest ink of an element of this type with its stem in
     *     {@code direction}, negative: the stem tip when the stem is up, otherwise
     *     {@link #getNoteheadTopOffsetSs()}
     */
    public double getTopYOffsetSs(Direction direction) {
        return direction.isUp() ? bounds.topOffsetUpSs() : bounds.topOffsetDownSs();
    }

    /**
     * @return this type's category
     */
    public Kind kind() {
        return kind;
    }

    /**
     * The note value of a note, a rest or a grace note.
     *
     * @return this type's value, or {@code null} for a type that has none — a bar, a breath
     *     mark or a key change
     */
    public @Nullable NoteValue noteValue() {
        return value;
    }

    /**
     * The time an element of this type occupies by default, in ticks. A type with no note
     * value occupies none, and so does a grace note: its value names its flag, not a duration.
     *
     * @return the ticks of this type's value, or 0 for a type that occupies no time
     */
    public int getDefaultDuration() {
        return value != null && kind != Kind.GRACE_NOTE ? value.ticks() : 0;
    }

    /**
     * How an element of this type is drawn. Every type answers one of the four
     * {@link ElementAppearance} arms, so a caller switching over the result needs no default.
     *
     * @return this type's appearance
     */
    public ElementAppearance appearance() {
        return appearance;
    }

    /**
     * @return {@code true} for a note that occupies time: every note but a grace note
     */
    public boolean isPitchedNote() {
        return kind == Kind.NOTE;
    }

    /**
     * @return {@code true} for anything that sounds a pitch: a note or a grace note
     */
    public boolean isNote() {
        return isPitchedNote() || isGraceNote();
    }

    /**
     * Whether an element of this type carries a stem: whether it is drawn from a notehead
     * a stem attaches to.
     *
     * @return {@code true} for every note but a whole note
     */
    public boolean isNoteWithStem() {
        return appearance instanceof NoteheadAppearance(StemmedNotehead _);
    }

    /**
     * @return {@code true} for a rest of any value
     */
    public boolean isRest() {
        return kind == Kind.REST;
    }

    /**
     * Whether an element of this type can take part in a beam: a pitched note whose value
     * carries a flag, which is what the beam replaces.
     *
     * @return {@code true} for a pitched eighth or shorter
     */
    public boolean isBeamable() {
        return isPitchedNote() && value != null && value.hasFlag();
    }

    /**
     * The number of beams a beamed note of this type carries: the flags its value would carry
     * on its own. Asked of any type, so a walk over a beam's members need not test first; a type
     * that cannot take part in a beam answers 0.
     *
     * @return {@code value.flagCount()} for a beamable type, 0 for anything else
     */
    public int beamCount() {
        return isBeamable() && value != null ? value.flagCount() : 0;
    }

    /**
     * @return {@code true} for a repeat sign, whichever way it faces
     */
    public boolean isRepeat() {
        return kind == Kind.REPEAT;
    }

    /**
     * @return {@code true} for a barline that is not a repeat
     */
    public boolean isBarLine() {
        return kind == Kind.BARLINE;
    }

    /**
     * Whether this type is a structural marker that closes a measure: a barline or a repeat.
     *
     * @return {@code true} for a barline or a repeat — the types drawn from a stroke sequence
     */
    public boolean isBar() {
        return isBarLine() || isRepeat();
    }

    /**
     * @return {@code true} for a type that occupies time: a pitched note or a rest
     */
    public boolean isDuration() {
        return isPitchedNote() || isRest();
    }

    /**
     * @return {@code true} for a type that occupies no time and sounds no pitch: a bar, a
     *     breath mark or a key change. A grace note is not one, and is not a duration either.
     */
    public boolean isNonDuration() {
        return isBar() || isBreathMark() || isKeyChange();
    }

    /**
     * Whether this element ends every accidental in force: a key change.
     */
    public boolean endsAccidentalsInForce() {
        return isKeyChange();
    }

    /**
     * Whether this element intervenes before the next note at any staff position: a barline or
     * a repeat.
     */
    public boolean intervenesInChain() {
        return isBar();
    }

    /**
     * Whether the chain walk looks through this element when deciding what intervenes before a
     * note.
     *
     * @return {@code true} for a rest or a breath mark
     */
    public boolean isTransparentInChain() {
        return isRest() || isBreathMark();
    }

    /**
     * @return {@code true} for the mid-line key change type
     */
    public boolean isKeyChange() {
        return kind == Kind.KEY_CHANGE;
    }

    /**
     * @return {@code true} for a grace note
     */
    public boolean isGraceNote() {
        return kind == Kind.GRACE_NOTE;
    }

    /**
     * Whether a syllable can be written on an element of this type. A syllable is sung on a
     * pitch, so only a note or a grace note can hold one; a rest cannot, and neither can any
     * structural marker.
     *
     * <p>This is the type half of "is this a place a syllable can go". The other half is the
     * grace-host pairing, which is a fact about an element's neighbors rather than its type and
     * lives in {@link StaffElement#canBearSyllable}, which states the two halves together.
     *
     * @return {@code true} for notes and grace notes
     */
    public boolean bearsSyllableText() {
        return isNote();
    }

    /**
     * Whether a lyric chain — a hyphenated word, or a melisma — carries on across an element of
     * this type. A repeat ends a section, so nothing runs through it; everything else carries a
     * chain on. See {@code docs/lyrics.md}.
     *
     * @return {@code true} for every type but a repeat
     */
    private boolean bearsLyricChain() {
        return !isRepeat();
    }

    /**
     * Whether writing an element of this type into a line breaks the lyric chains that ran
     * across the position it lands at.
     *
     * <p>An element that carries a chain onward but can never take a syllable of its own — a
     * rest, a barline, a breath mark, a key change — is transparent: the word and the melisma
     * run through it exactly as they did before it arrived. Everything else interrupts, for one
     * of two opposite reasons. A note or a grace note is a syllable slot arriving empty, so the
     * word it lands inside can no longer be sung as one word. A repeat carries nothing at all.
     *
     * @return {@code true} when an inserted element of this type breaks a word or a melisma
     *     spanning its position
     */
    public boolean interruptsLyricChain() {
        return bearsSyllableText() || !bearsLyricChain();
    }

    /**
     * @return {@code true} for a breath mark
     */
    public boolean isBreathMark() {
        return kind == Kind.BREATH_MARK;
    }

    /**
     * Whether an annotation attached to this element type survives a save/reload round trip.
     * Two types cannot carry one, each for its own reason:
     *
     * <ul>
     *   <li>a <b>breath mark</b> has no place in the file to put one: the writer stores a breath
     *       mark inside the preceding note's markup rather than as an item of its own, and on
     *       reading, the breath-mark element is created as a side effect of finishing that note —
     *       by which point the note has already taken the pending annotation;</li>
     *   <li>a <b>key change</b> is an attribute of the measure its preceding barline or repeat
     *       opens, not an item of its own, so there is nothing in the file to hang an annotation
     *       on. Nothing is lost to the user: selecting a key change selects that barline or
     *       repeat with it, and the annotation attaches there.</li>
     * </ul>
     *
     * <p>Attaching one anyway would lose it silently on save, so the annotation action disables
     * itself for such an element rather than let the user type text that cannot be stored. The
     * action asks whether <em>any</em> selected element can carry one, which is why a key
     * change selected together with its barline still leaves the action enabled.
     *
     * @return {@code true} for every type but a breath mark and a key change
     */
    public boolean canCarryAnnotation() {
        return !isBreathMark() && !isKeyChange();
    }

    /**
     * The lowercase display words for this type — the value word, then the kind word: "quarter
     * note", "whole rest", "barline". This is the one place that composes a type's display
     * words. A grace note omits its value word, because a grace note is named by its kind alone.
     *
     * <p>Dots are the element's, not the type's, so a caller that shows a dotted element adds
     * the dot word itself.
     *
     * @return this type's display words, lowercase
     */
    public String label() {
        var kindWord = kind.label();

        if (value == null || kind == Kind.GRACE_NOTE) {
            return kindWord;
        }

        return Strings.get(value.labelKey()) + ' ' + kindWord;
    }

    /**
     * Non-content elements are skipped during first-second ending validation
     * but are allowed to be present in the selection. Breath marks are
     * non-content: they may sit inside an ending but cannot anchor or end it.
     * The complement — the elements that can anchor or end an ending — is every
     * note, rest, barline and repeat.
     * <p>
     * A key change is non-content for two reasons that point the same way. It is not
     * musical content, so it must not help a selection reach the minimum an ending needs;
     * and a key change must not move where an ending anchors, which is what being skipped
     * delivers — the backward walk for the preceding element passes over it and reaches the
     * barline that {@link KeyChangeElement}'s position invariant guarantees behind it, so
     * the bracket lands exactly where it would have had the key change not been there.
     *
     * @return {@code true} for grace notes, breath marks and key changes
     */
    public boolean isNonContentElement() {
        return isGraceNote() || isBreathMark() || isKeyChange();
    }

    /**
     * A terminal element can end a first-second ending selection:
     * single barline, double barline, final double barline, or left repeat.
     *
     * <p>This is about the right boundary of a first/second-ending bracket, not
     * {@link #isValidSongTerminal()}. {@code REPEAT_RIGHT} is deliberately excluded because it
     * plays the split role that closes the first ending and loops back to the second, so it
     * cannot also serve as the bracket's right boundary.
     *
     * @return {@code true} for a barline or a left repeat
     */
    public boolean isEndingTerminal() {
        return isBarLine() || this == REPEAT_LEFT;
    }

    /**
     * The rest of the same value as this note. Conversion is defined between a pitched note and
     * a rest only: a grace note has no rest counterpart and is not the note of its value, so it
     * converts to itself, as every type with no value does.
     *
     * @return the rest of this pitched note's value; {@code this} for every other type
     * @invariant {@code toRest().toNote()} is {@code this} for a pitched note
     */
    public ElementType toRest() {
        return value != null && kind == Kind.NOTE ? value.rest() : this;
    }

    /**
     * The pitched note of the same value as this rest. Conversion is defined between a pitched
     * note and a rest only, so a grace note converts to itself, as every type with no value does.
     *
     * @return the pitched note of this rest's value; {@code this} for every other type
     * @invariant {@code toNote().toRest()} is {@code this} for a rest
     */
    public ElementType toNote() {
        return value != null && kind == Kind.REST ? value.note() : this;
    }

    /**
     * Whether a bar of this type, standing last on the last line, is replaced by the
     * auto-maintained terminal rather than followed by one. A left-facing repeat opens what
     * follows it and so does not end a line; the terminal is appended after it instead.
     *
     * @return {@code true} for a single barline, a double barline, a right repeat and a
     *     left-right repeat
     */
    public boolean isReplaceableByTerminal() {
        return this == SINGLE_BARLINE
            || this == DOUBLE_BARLINE
            || this == REPEAT_RIGHT
            || this == REPEAT_LEFT_RIGHT;
    }

    /**
     * Whether a bar of this type may occupy the auto-maintained terminal slot, the last position
     * of the last line. A song ends either with a final barline or by repeating back, and nothing
     * else may stand where the song stops.
     *
     * <p>This is about the <b>song's</b> last slot, not {@link #isEndingTerminal()}.
     *
     * @return {@code true} for a final double barline and a right repeat
     */
    public boolean isValidSongTerminal() {
        return this == FINAL_DOUBLE_BARLINE || this == REPEAT_RIGHT;
    }

    /**
     * Whether a bar of this type closes a repeated section — the bar playback loops back
     * from, and the one an ending's two brackets are split at.
     *
     * @return {@code true} for a right repeat and a left-right repeat
     */
    public boolean closesRepeat() {
        return this == REPEAT_RIGHT || this == REPEAT_LEFT_RIGHT;
    }

    /**
     * Whether a bar of this type closes a section nothing runs past, so that a volta bracket
     * closing on it hangs a closing stroke from it.
     *
     * @return {@code true} for a bar that {@link #closesRepeat()} and for a final double barline
     */
    public boolean closesSection() {
        return closesRepeat() || this == FINAL_DOUBLE_BARLINE;
    }

    /**
     * The flag a note of this type carries on a stem in the given direction. A grace note's flag
     * is the up-stem eighth flag whatever the direction, because a grace note's stem always goes
     * up.
     *
     * @param direction the stem direction: a flag hangs right of an up stem and left of a down
     *                  stem
     * @return the flag glyph; {@code null} for a note whose value carries none and for every
     *     type that is not a note
     */
    @Nullable
    public SMuFLGlyph getFlagGlyph(Direction direction) {
        return flagGlyph(kind, value, direction);
    }

    /**
     * The flag glyph of a type given by its parts, so that the constructor's bounds computation
     * can ask it before any constant of this enum exists.
     */
    @Nullable
    private static SMuFLGlyph flagGlyph(
        Kind kind, @Nullable NoteValue value, Direction direction
    ) {
        return switch (kind) {
            // A grace note's stem is always up, whatever direction the caller asks for.
            case GRACE_NOTE -> value == null ? null : value.flagGlyph(Direction.UP);
            case NOTE -> value == null ? null : value.flagGlyph(direction);
            case REST, BARLINE, REPEAT, BREATH_MARK, KEY_CHANGE -> null;
        };
    }

    // ========================================================================
    // Element bounds in staff spaces
    // ========================================================================

    /**
     * The bounds of a type given by its parts. Runs inside the constructor, so it references no
     * constant of this enum: everything it needs is in its arguments.
     */
    private static ElementBounds computeBounds(
        Kind kind, @Nullable NoteValue value, ElementAppearance appearance
    ) {
        return switch (appearance) {
            // A stemmed notehead is the only appearance whose bounds reach past the glyph,
            // so it is the only one that needs the stem and flag.
            case NoteheadAppearance(StemmedNotehead notehead) -> kind == Kind.GRACE_NOTE
                ? computeGraceNoteBoundsSs(kind, value, notehead)
                : computeStemmedNoteBoundsSs(kind, value, notehead);

            // A semibreve carries no stem, so its bounds are its glyph's, exactly as a
            // rest's are.
            case NoteheadAppearance(WholeNotehead notehead) ->
                computeGlyphBoundsSs(notehead.glyph());

            case GlyphAppearance glyph -> computeGlyphBoundsSs(glyph.glyph());

            case BarAppearance bar -> ElementBounds.symmetric(
                new Size(bar.widthSs(), Staff.STAFF_HEIGHT_SS), -Staff.STAFF_HALF_SS);

            case KeySignatureAppearance ignored -> computeKeySignatureBoundsSs();
        };
    }

    private static ElementBounds computeStemmedNoteBoundsSs(
        Kind kind, @Nullable NoteValue value, StemmedNotehead notehead
    ) {
        var headBBox = SMuFLMetadata.bboxSs(notehead.glyph());
        var anchors = SMuFLMetadata.stemAnchors(notehead);
        var headTopSs = headBBox.topSs();
        var headBottomSs = headBBox.bottomSs();
        var headRightSs = headBBox.rightSs();
        var stemUpXSs = anchors.stemUpSE().xSs();

        // Stem tip above the notehead for an up-stem, below it for a down-stem.
        var upTopSs = anchors.stemUpSE().ySs() - StemMetrics.STEM_LENGTH_SS;
        var downBottomSs = anchors.stemDownNW().ySs() + StemMetrics.STEM_LENGTH_SS;

        // Width: max of notehead and stem-up flag extent
        var widthSs = headRightSs;
        var flagGlyph = flagGlyph(kind, value, Direction.UP);

        if (flagGlyph != null) {
            widthSs = Math.max(widthSs, stemUpXSs + SMuFLMetadata.bboxSs(flagGlyph).rightSs());
        }

        return new ElementBounds(
            widthSs,
            headRightSs,
            headBottomSs - headTopSs,
            headTopSs,
            // Height up: from top of stem to bottom of notehead
            headBottomSs - upTopSs,
            // Height down: from top of notehead to bottom of stem
            downBottomSs - headTopSs,
            upTopSs,    // stem tip above center (negative)
            headTopSs   // notehead top above center (negative)
        );
    }

    private static ElementBounds computeGraceNoteBoundsSs(
        Kind kind, @Nullable NoteValue value, StemmedNotehead notehead
    ) {
        var headBBox = SMuFLMetadata.bboxSs(notehead.glyph());
        double scale = GRACE_NOTE_SCALE;

        var headBottomSs = headBBox.bottomSs() * scale;
        var headRightSs = headBBox.rightSs() * scale;

        var stemUpSE = SMuFLMetadata.stemAnchors(notehead).stemUpSE();
        var stemUpXSs = stemUpSE.xSs() * scale;
        var stemUpYSs = stemUpSE.ySs() * scale;

        var upTopSs = stemUpYSs - StemMetrics.GRACE_NOTE_STEM_LENGTH_SS;
        var widthSs = headRightSs;
        var flagGlyph = flagGlyph(kind, value, Direction.UP);

        if (flagGlyph != null) {
            var flagRightSs = SMuFLMetadata.bboxSs(flagGlyph).rightSs() * scale;
            widthSs = Math.max(widthSs, stemUpXSs + flagRightSs);
        }

        var headTopSs = headBBox.topSs() * scale;
        var heightSs = headBottomSs - upTopSs;

        return new ElementBounds(
            widthSs,
            headRightSs,
            headBottomSs - headTopSs,
            headTopSs,
            heightSs,
            heightSs,
            upTopSs,
            upTopSs
        );
    }

    private static ElementBounds computeGlyphBoundsSs(SMuFLGlyph glyph) {
        var bbox = SMuFLMetadata.bboxSs(glyph);
        // Width is the glyph's right edge from the origin, not the bbox width, so that
        // baseWidthSs means the same thing for every glyph-bearing type (see
        // getElementWidthSs()). The two differ only when bBoxSW's x is non-zero.
        return ElementBounds.symmetric(new Size(bbox.rightSs(), bbox.heightSs()), bbox.topSs());
    }

    /**
     * A key change's bounds are the degenerate case: the narrowest key signature that draws
     * anything, a single accidental, spanning the staff vertically the way a barline does.
     * <p>
     * Every other type in this enum has one width, so the constant is the whole answer. A key
     * signature does not: its width depends on the {@link Key} it carries and on the key it
     * cancels, neither of which the enum constant knows. The real width comes from the element
     * instance ({@code KeyChangeElement.getContentWidthSs()}); what is computed here is only the
     * floor, so that a caller reasoning from the type alone never over-reserves.
     */
    private static ElementBounds computeKeySignatureBoundsSs() {
        var narrowestAccidentalSs = Math.min(
            Key.DrawnAccidental.inkWidthSs(SMuFLGlyph.ACCIDENTAL_FLAT),
            Key.DrawnAccidental.inkWidthSs(SMuFLGlyph.ACCIDENTAL_SHARP)
        );

        return ElementBounds.symmetric(
            new Size(narrowestAccidentalSs, Staff.STAFF_HEIGHT_SS), -Staff.STAFF_HALF_SS);
    }
}
