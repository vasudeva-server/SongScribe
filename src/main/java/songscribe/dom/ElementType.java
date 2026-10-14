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
 * What an element is — a quarter note, an eighth rest, a left repeat. What varies between two
 * elements of one type — pitch, dots, accidental, a key — lives on the element.
 *
 * <p>Each constant carries a {@link NoteValue} exactly when its kind is a note, a grace note or
 * a rest. The predicates that name a category answer from the kind alone.
 *
 * <p>A grace note is a note that is neither a duration nor a non-duration:
 * {@link #isDuration()} and {@link #isNonDuration()} do not partition the kinds.
 *
 * <p>An element's Java class follows its type's appearance: {@link #newInstance()} chooses the
 * class from the appearance, and {@link KeyChangeElement}'s own constructors build the one class
 * its type admits. No other path builds an element.
 *
 * <p>The measurements are in staff spaces relative to the element's origin. X grows rightward
 * from the glyph's left origin; Y grows downward from the element's center, so a top offset is
 * negative and a bottom offset positive.
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
     * The category an element type belongs to. Every type has exactly one.
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

        public String labelKey() {
            return labelKey;
        }

        public String label() {
            return Strings.get(labelKey);
        }
    }

    // ========================================================================
    // Geometry constants — canonical definitions; layout/ui reference these
    // ========================================================================

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
     * A key change starts in C major, the key that draws nothing; a caller that means a
     * particular key sets it.
     *
     * <p>The element is on no line and carries nothing an editor would have set — no dots,
     * accidental, lyric or attachment.
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

    public double getFullElementWidthSs() {
        return bounds.fullWidthSs();
    }

    public double getElementHeightSs(Direction direction) {
        return direction.isUp() ? bounds.heightUpSs() : bounds.heightDownSs();
    }

    /**
     * The right edge of this type's own glyph — the notehead alone for a note — measured from
     * the origin rather than as a bounding-box width, so that a glyph whose ink starts right of
     * its origin is placed by where its ink ends. Reading the black notehead's box from
     * {@link SMuFLMetadata#bboxSs} instead leaves a whole note about half a staff space short.
     */
    public double getElementWidthSs() {
        return bounds.baseWidthSs();
    }

    /**
     * @return the center of the glyph alone, never of a flag
     */
    public double getElementCenterXSs() {
        return bounds.baseWidthSs() / 2;
    }

    public double flushRightXSs(double lineWidthSs) {
        return lineWidthSs - bounds.baseWidthSs();
    }

    public double getFullElementHeightSs() {
        return bounds.fullElementHeightSs();
    }

    public double getNoteheadTopOffsetSs() {
        return bounds.noteheadTopOffsetSs();
    }

    public double getNoteheadBottomOffsetSs() {
        return bounds.noteheadTopOffsetSs() + bounds.fullElementHeightSs();
    }

    public double getTopYOffsetSs(Direction direction) {
        return direction.isUp() ? bounds.topOffsetUpSs() : bounds.topOffsetDownSs();
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return {@code null} for a type that has none — a bar, a breath
     *     mark or a key change
     */
    public @Nullable NoteValue noteValue() {
        return value;
    }

    /**
     * A grace note occupies none: its value names its flag, not a duration.
     */
    public int getDefaultDuration() {
        return value != null && kind != Kind.GRACE_NOTE ? value.ticks() : 0;
    }

    public ElementAppearance appearance() {
        return appearance;
    }

    public boolean isPitchedNote() {
        return kind == Kind.NOTE;
    }

    public boolean isNote() {
        return isPitchedNote() || isGraceNote();
    }

    public boolean isNoteWithStem() {
        return appearance instanceof NoteheadAppearance(StemmedNotehead _);
    }

    public boolean isRest() {
        return kind == Kind.REST;
    }

    public boolean isBeamable() {
        return isPitchedNote() && value != null && value.hasFlag();
    }

    /**
     * A type that cannot take part in a beam answers 0.
     */
    public int beamCount() {
        return isBeamable() && value != null ? value.flagCount() : 0;
    }

    public boolean isRepeat() {
        return kind == Kind.REPEAT;
    }

    public boolean isBarLine() {
        return kind == Kind.BARLINE;
    }

    public boolean isBar() {
        return isBarLine() || isRepeat();
    }

    public boolean isDuration() {
        return isPitchedNote() || isRest();
    }

    public boolean isNonDuration() {
        return isBar() || isBreathMark() || isKeyChange();
    }

    public boolean endsAccidentalsInForce() {
        return isKeyChange();
    }

    public boolean intervenesInChain() {
        return isBar();
    }

    public boolean isTransparentInChain() {
        return isRest() || isBreathMark();
    }

    public boolean isKeyChange() {
        return kind == Kind.KEY_CHANGE;
    }

    public boolean isGraceNote() {
        return kind == Kind.GRACE_NOTE;
    }

    /**
     * The type half of "is this a place a syllable can go"; the other half, the grace-host
     * pairing, lives in {@link StaffElement#canBearSyllable}.
     */
    public boolean bearsSyllableText() {
        return isNote();
    }

    /**
     * A repeat ends a section, so nothing runs through it.
     */
    boolean bearsLyricChain() {
        return !isRepeat();
    }

    /**
     * An element that carries a chain onward but can never take a syllable of its own — a
     * rest, a barline, a breath mark, a key change — is transparent: the word and the melisma
     * run through it exactly as they did before it arrived. Everything else interrupts, for one
     * of two opposite reasons. A note or a grace note is a syllable slot arriving empty, so the
     * word it lands inside can no longer be sung as one word. A repeat carries nothing at all.
     */
    public boolean interruptsLyricChain() {
        return bearsSyllableText() || !bearsLyricChain();
    }

    public boolean isBreathMark() {
        return kind == Kind.BREATH_MARK;
    }

    /**
     * A breath mark and a key change cannot carry an annotation that survives a save/reload
     * round trip: the file has no item of its own for either. Attaching one anyway would lose it
     * silently, so the annotation action disables itself for such an element. The action asks
     * whether <em>any</em> selected element can carry one, which is why a key change selected
     * together with its barline leaves the action enabled.
     */
    public boolean canCarryAnnotation() {
        return !isBreathMark() && !isKeyChange();
    }

    /**
     * A grace note omits its value word. Dots are the element's, not the type's, so a caller
     * that shows a dotted element adds the dot word itself.
     */
    public String label() {
        var kindWord = kind.label();

        if (value == null || kind == Kind.GRACE_NOTE) {
            return kindWord;
        }

        return Strings.get(value.labelKey()) + ' ' + kindWord;
    }

    /**
     * Non-content elements are skipped during first-second ending validation but are allowed in
     * the selection: breath marks may sit inside an ending but cannot anchor or end it. A key
     * change is non-content so that it cannot help a selection reach the minimum an ending
     * needs and cannot move where an ending anchors.
     */
    public boolean isNonContentElement() {
        return isGraceNote() || isBreathMark() || isKeyChange();
    }

    /**
     * The right boundary of a first/second-ending bracket, not {@link #isValidSongTerminal()}.
     * {@code REPEAT_RIGHT} is excluded because it plays the split role that closes the first
     * ending, so it cannot also be the bracket's right boundary.
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
     * A left-facing repeat opens what follows it and so does not end a line.
     */
    public boolean isReplaceableByTerminal() {
        return this == SINGLE_BARLINE
            || this == DOUBLE_BARLINE
            || this == REPEAT_RIGHT
            || this == REPEAT_LEFT_RIGHT;
    }

    /**
     * A song ends either with a final barline or by repeating back. About the <b>song's</b> last
     * slot, not {@link #isEndingTerminal()}.
     */
    public boolean isValidSongTerminal() {
        return this == FINAL_DOUBLE_BARLINE || this == REPEAT_RIGHT;
    }

    /**
     * The bar playback loops back from, and the one an ending's two brackets are split at.
     */
    public boolean closesRepeat() {
        return this == REPEAT_RIGHT || this == REPEAT_LEFT_RIGHT;
    }

    /**
     * The section a later {@code :|} plays back to starts just after it.
     */
    public boolean opensRepeatSection() {
        return this == REPEAT_LEFT || this == REPEAT_LEFT_RIGHT;
    }

    /**
     * Wider than {@link #closesRepeat()}: a {@code ||} and a final barline end every open
     * section without sending playback back, so a {@code :|} after them finds no section to play
     * back to unless another bar opens one.
     */
    public boolean closesRepeatSection() {
        return closesRepeat() || this == DOUBLE_BARLINE || this == FINAL_DOUBLE_BARLINE;
    }

    /**
     * A grace note's flag is the up-stem eighth flag whatever the direction.
     *
     * @return {@code null} for a note whose value carries none and for every type that is not a
     *     note
     */
    @Nullable
    public SMuFLGlyph getFlagGlyph(Direction direction) {
        return flagGlyph(kind, value, direction);
    }

    /**
     * Takes a type's parts so that the constructor's bounds computation can ask it before any
     * constant of this enum exists.
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
     * Runs inside the constructor, so it references no constant of this enum.
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
     * Only the floor: the narrowest key signature that draws anything. The real width depends on
     * the {@link Key} the element carries and cancels, so it comes from the element instance
     * ({@code KeyChangeElement.getContentWidthSs()}); a caller reasoning from the type alone
     * never over-reserves.
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
