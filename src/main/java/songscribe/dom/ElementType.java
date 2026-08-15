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

import static songscribe.midi.MidiSequenceBuilder.PPQ;

import module java.desktop;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.smufl.BBox;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.error.RuntimeError;
import songscribe.util.UIUtils;

public enum ElementType {
    // Notes
    SEMIBREVE("Whole note", KeyEvent.VK_6, 0, PPQ * 4, 0),
    MINIM("Half note", KeyEvent.VK_5, 0, PPQ * 2, 0),
    CROTCHET("Quarter note", KeyEvent.VK_4, 0, PPQ, 0),
    QUAVER("Eighth note", KeyEvent.VK_3, 0, PPQ / 2, 0),
    SEMIQUAVER("Sixteenth note", KeyEvent.VK_2, 0, PPQ / 4, 0),
    DEMI_SEMIQUAVER("Thirty-second note", KeyEvent.VK_1, 0, PPQ / 8, 0),

    // Rests
    SEMIBREVE_REST("Whole rest", KeyEvent.VK_6, -1, PPQ * 4, -1),
    MINIM_REST("Half rest", KeyEvent.VK_5, -1, PPQ * 2, 0),
    CROTCHET_REST("Quarter rest", KeyEvent.VK_4, -1, PPQ, 0),
    QUAVER_REST("Eighth rest", KeyEvent.VK_3, -1, PPQ / 2, 0),
    SEMIQUAVER_REST("Sixteenth rest", KeyEvent.VK_2, -1, PPQ / 4, 0),
    DEMI_SEMIQUAVER_REST("Thirty-second rest", KeyEvent.VK_1, -1, PPQ / 8, 0),

    // Grace notes
    GRACE_QUAVER("Grace note", KeyEvent.VK_G, 0, 0, 0),

    // Repeats
    REPEAT_LEFT("Repeat left", KeyEvent.VK_L, 0, 0, 0),
    REPEAT_RIGHT("Repeat right", KeyEvent.VK_R, 0, 0, 0),
    REPEAT_LEFT_RIGHT("Repeat left/right", 0, 0),

    // Half a staff space above the top staff line. This is the single source for where a
    // breath mark sits: NoteRenderer.renderBreathMark() derives the glyph's Y from it and the
    // hit rect is built from it, so the drawn glyph and its clickable area cannot disagree.
    BREATH_MARK("Breath mark", 0, -5),

    // Barlines
    SINGLE_BARLINE("Single barline", 0, 0),
    DOUBLE_BARLINE("Double barline", 0, 0),
    FINAL_DOUBLE_BARLINE("Final double barline", 0, 0),

    // Key signatures. Deliberately placed after the barlines and before the IO aliases: the
    // three ordinal-range predicates — isNote(), isRest() and isBarLine() — all end before
    // this point, so none of them picks it up.
    KEY_CHANGE("Key signature", 0, 0),

    // IO aliases
    SEMIBREVEREST(ElementType.SEMIBREVE_REST),
    MINIMREST(ElementType.MINIM_REST),
    CROTCHETREST(ElementType.CROTCHET_REST),
    QUAVERREST(ElementType.QUAVER_REST),
    SEMIQUAVERREST(ElementType.SEMIQUAVER_REST),
    DEMISEMIQUAVERREST(ElementType.DEMI_SEMIQUAVER_REST),
    GRACEQUAVER(ElementType.GRACE_QUAVER),
    REPEATLEFT(ElementType.REPEAT_LEFT),
    REPEATRIGHT(ElementType.REPEAT_RIGHT),
    REPEATLEFTRIGHT(ElementType.REPEAT_LEFT_RIGHT),
    BREATHMARK(ElementType.BREATH_MARK),
    SINGLEBARLINE(ElementType.SINGLE_BARLINE),
    DOUBLEBARLINE(ElementType.DOUBLE_BARLINE),
    FINALDOUBLEBARLINE(ElementType.FINAL_DOUBLE_BARLINE);

    static {
        // Create instances for canonical types
        for (var type : values()) {
            //noinspection ConstantValue
            if (type.instance == null && type.aliasOf == null) {
                type.instance = type.createDefaultInstance();
            }
        }

        // Copy instances for alias types
        for (var type : values()) {
            if (type.aliasOf != null) {
                type.instance = type.aliasOf.instance;
            }
        }
    }

    /**
     * Maps each ElementType to its corresponding SMuFL glyph.
     * Used for metadata-driven bounds computation and rendering.
     * Barline and repeat types are absent — they compute bounds from engraving defaults.
     */
    private static final Map<ElementType, SMuFLGlyph> SMUFL_GLYPHS = Map.ofEntries(
        Map.entry(SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE),
        Map.entry(MINIM, SMuFLGlyph.NOTEHEAD_HALF),
        Map.entry(CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(QUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(GRACE_QUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(SEMIBREVE_REST, SMuFLGlyph.REST_WHOLE),
        Map.entry(MINIM_REST, SMuFLGlyph.REST_HALF),
        Map.entry(CROTCHET_REST, SMuFLGlyph.REST_QUARTER),
        Map.entry(QUAVER_REST, SMuFLGlyph.REST_8TH),
        Map.entry(SEMIQUAVER_REST, SMuFLGlyph.REST_16TH),
        Map.entry(DEMI_SEMIQUAVER_REST, SMuFLGlyph.REST_32ND),
        Map.entry(BREATH_MARK, SMuFLGlyph.BREATH_MARK_COMMA)
    );

    // ========================================================================
    // Geometry constants — canonical definitions; layout/ui reference these
    // ========================================================================

    /** Uniform scale factor applied to grace note glyphs (75% of normal size). */
    public static final float GRACE_NOTE_SCALE = 0.75f;

    static {
        computeElementBoundsSs();
    }

    public static int getMenuShortcutKeyMask() {
        return !GraphicsEnvironment.isHeadless()
            ? UIUtils.MENU_SHORTCUT_MASK
            : 0;
    }

    @SuppressWarnings("NullAway") // instance is initialized in static block, but NullAway doesn't track that
    private StaffElement instance;
    @Nullable
    private final String name;
    @Nullable
    private final KeyStroke acceleratorKey;
    private final int defaultDuration;
    private final int defaultStaffPosition;
    @Nullable
    private final ElementType aliasOf;
    private double fullWidthSs;
    private double baseWidthSs;
    private double fullElementHeightSs;
    private double noteheadTopOffsetSs;
    private double heightUpSs;
    private double heightDownSs;
    private double topOffsetUpSs;
    private double topOffsetDownSs;

    ElementType(
        @Nullable String name,
        int keyCode,
        int modifiers,
        int defaultDuration,
        int defaultStaffPosition
    ) {
        this.name = name;
        this.defaultDuration = defaultDuration;
        this.defaultStaffPosition = defaultStaffPosition;
        aliasOf = null;

        if (keyCode != 0) {
            if (modifiers == -1) {
                modifiers = getMenuShortcutKeyMask();
            }

            acceleratorKey = KeyStroke.getKeyStroke(keyCode, modifiers);
        } else {
            acceleratorKey = null;
        }
    }

    ElementType(
        @Nullable String name,
        int defaultDuration,
        int defaultStaffPosition
    ) {
        this(name, 0, 0, defaultDuration, defaultStaffPosition);
    }

    ElementType(ElementType aliasOf) {
        this.aliasOf = aliasOf;
        name = aliasOf.name;
        acceleratorKey = aliasOf.acceleratorKey;
        defaultDuration = aliasOf.defaultDuration;
        defaultStaffPosition = aliasOf.defaultStaffPosition;
    }

    /**
     * Builds the shared instance {@link #getInstance()} hands out and {@link #newInstance()} clones.
     * <p>
     * The instance's Java class matches the type: a key signature is a {@link KeyChangeElement},
     * so a caller that reaches an element through the type registry gets one it can ask for a key,
     * and a {@code newInstance()} clone is an element the document can hold. It starts in C major —
     * the key that draws nothing — because a default instance states no key of its own and a
     * caller that means a particular key sets it.
     */
    private StaffElement createDefaultInstance() {
        if (this == KEY_CHANGE) {
            return new KeyChangeElement(new Key(KeyType.NONE, 0));
        }

        if (isRest() || isNonDuration()) {
            return new StructuralElement(this);
        }

        return new StaffElement(this);
    }

    public StaffElement getInstance() {
        return instance;
    }

    public StaffElement newInstance() {
        return instance.clone();
    }

    public @Nullable String getName() {
        return name;
    }

    public @Nullable KeyStroke getAcceleratorKey() {
        return acceleratorKey;
    }

    /**
     * Returns the element width in staff spaces. Includes flag extent for stemmed notes.
     */
    public double getFullElementWidthSs() {
        return fullWidthSs;
    }

    /**
     * Returns the element height in staff spaces for the given stem direction.
     *
     * @param direction {@code UP} for stem-up; {@code DOWN} for stem-down
     */
    public double getElementHeightSs(StaffElement.Direction direction) {
        return direction.isUp() ? heightUpSs : heightDownSs;
    }

    /**
     * Returns the horizontal center of the element in staff spaces.
     * For note types, includes the flag.
     */
    public double getFullElementCenterXSs() {
        return fullWidthSs / 2;
    }

    /**
     * Returns the distance in staff spaces from the element origin to the right edge of the
     * element's glyph — the notehead alone for note types, excluding stem, flag, and
     * augmentation dots, and already grace-scaled for grace notes. This is a right edge measured
     * from the origin, not a bounding-box width: for glyphs whose bounding box does not start at
     * x = 0 the two differ.
     *
     * <p>This is the single source of truth for how wide an element's own glyph is. Anything that
     * needs a notehead width should call this rather than
     * {@link SMuFLConstants#NOTE_HEAD_WIDTH_SS}, which is the black notehead
     * specifically and leaves a whole note about half a staff space short (refs #694).
     */
    public double getElementWidthSs() {
        return baseWidthSs;
    }

    /**
     * Returns the horizontal midpoint in staff spaces between the element origin and the right
     * edge of the element's glyph (see {@link #getElementWidthSs()}).
     * For note types, excludes the flag.
     */
    public double getElementCenterXSs() {
        return baseWidthSs / 2;
    }

    /**
     * Returns the X position at which the given terminal type should be placed
     * so its right edge aligns flush with the right edge of the line.
     *
     * @param lineWidthSs  the line width in staff spaces
     * @param terminalType the terminal element type ({@link #isValidSongTerminal()} must be true)
     */
    public static double terminalFlushRightXSs(double lineWidthSs, ElementType terminalType) {
        return lineWidthSs - terminalType.baseWidthSs;
    }

    /**
     * Returns the X offset from this element's layout X to the ending bracket anchor point
     * (the left vertical stroke of a volta bracket). For barlines and repeats, the anchor
     * aligns with the center of the governing thin barline; for all other types, returns 0.
     */
    public double endingAnchorXOffsetSs() {
        if (this == REPEAT_LEFT) {
            // Layout: thick | sep | thin | sep | dots — anchor on the thin barline center
            return LineThickness.THICK_BARLINE_SS + LineThickness.BARLINE_SEPARATION_SS
                + LineThickness.THIN_BARLINE_SS / 2;
        }

        if (isBarLine() || isRepeat()) {
            // Generic: center on the rightmost thin barline
            return fullWidthSs - LineThickness.THIN_BARLINE_SS / 2;
        }

        return 0;
    }

    /**
     * Returns the element height in staff spaces, excluding the stem for note types.
     */
    public double getFullElementHeightSs() {
        return fullElementHeightSs;
    }

    /**
     * Returns the Y offset in staff spaces from the notehead center to the top of the notehead.
     * The returned value is negative (the top is above the note center).
     * For non-stemmed elements this equals {@link #getTopYOffsetSs(StaffElement.Direction)}.
     */
    public double getNoteheadTopOffsetSs() {
        return noteheadTopOffsetSs;
    }

    /**
     * Returns the Y offset in staff spaces from the notehead center to the bottom of the notehead.
     * The returned value is positive (the bottom is below the note center).
     * Mirrors {@link #getNoteheadTopOffsetSs()}.
     */
    public double getNoteheadBottomOffsetSs() {
        return noteheadTopOffsetSs + fullElementHeightSs;
    }

    /**
     * Returns the Y offset in staff spaces from the notehead center to the top of the element.
     * The returned value is negative (the top is above the note center).
     * <p>
     * For stem-up notes this is the stem tip; for stem-down the notehead top; for
     * non-stemmed elements the glyph bbox top or half of the staff height above center.
     *
     * @param direction {@code UP} for stem-up; {@code DOWN} for stem-down
     */
    public double getTopYOffsetSs(StaffElement.Direction direction) {
        return direction.isUp() ? topOffsetUpSs : topOffsetDownSs;
    }

    public int getDefaultDuration() {
        return defaultDuration;
    }

    public int getDefaultStaffPosition() {
        return defaultStaffPosition;
    }

    /**
     * Returns the SMuFL glyph corresponding to this note type's primary glyph
     * (notehead, rest, or breath mark).
     * Returns null for barlines and repeats (they compute bounds from engraving defaults).
     */
    @Nullable
    public SMuFLGlyph getSMuFLGlyph() {
        return SMUFL_GLYPHS.get(this);
    }

    /**
     * Returns the SMuFL glyph for this element type, or exits the app if absent
     * (indicating a broken install missing required metadata).
     */
    public SMuFLGlyph requireSMuFLGlyph() {
        var glyph = SMUFL_GLYPHS.get(this);

        if (glyph == null) {
            throw RuntimeError.missingResource("Missing SMuFL glyph for element type " + this);
        }

        return glyph;
    }

    public boolean isPitchedNote() {
        //noinspection ConstantValue
        return (
            ordinal() >= SEMIBREVE.ordinal() &&
                ordinal() <= DEMI_SEMIQUAVER.ordinal()
        );
    }

    public boolean isNote() {
        return isPitchedNote() || isGraceNote();
    }

    public boolean isNoteWithStem() {
        return isNote() && this != SEMIBREVE;
    }

    public boolean isRest() {
        return (
            ordinal() >= SEMIBREVE_REST.ordinal() &&
                ordinal() <= DEMI_SEMIQUAVER_REST.ordinal()
        );
    }

    public boolean isBeamable() {
        return this == QUAVER || this == SEMIQUAVER || this == DEMI_SEMIQUAVER;
    }

    public boolean isRepeat() {
        return (
            this == REPEAT_LEFT ||
                this == REPEAT_RIGHT ||
                this == REPEAT_LEFT_RIGHT
        );
    }

    public boolean isBarLine() {
        return (
            ordinal() >= SINGLE_BARLINE.ordinal() &&
                ordinal() <= FINAL_DOUBLE_BARLINE.ordinal()
        );
    }

    public boolean isDuration() {
        return isPitchedNote() || isRest();
    }

    public boolean isNonDuration() {
        return isBarLine() || isRepeat() || isBreathMark() || this == KEY_CHANGE;
    }

    /**
     * Whether this element cancels every accidental written before it, so that a later note at the
     * same staff position inherits nothing from across it and falls back to the key signature.
     * Convention: any structural marker cancels. A breath mark deliberately does not — it cancels
     * nothing.
     *
     * <p>Both places that resolve which accidental is sounding ask this: the resolver that reads
     * the live line ({@link StaffElement#findEffectiveAccidental}) and the one that reads a
     * projection of how the line will look after an edit ({@code AccidentalReconciliation}). They
     * have to agree, or an edit's preview and its result would disagree about a note's pitch.
     *
     * <p>The same {@code isBarLine() || isRepeat()} test appears elsewhere in the codebase for
     * unrelated reasons — closing a MusicXML measure, bounding an ending, choosing a glyph — so
     * this is deliberately narrow rather than a general "is a structural marker". A key change
     * belongs here, and only here: a new key signature resets what a later note at the same
     * staff position inherits, but it does not close a measure, bound an ending or pick a glyph.
     */
    public boolean cancelsAccidentals() {
        return isBarLine() || isRepeat() || this == KEY_CHANGE;
    }

    public boolean isGraceNote() {
        return this == GRACE_QUAVER;
    }

    public boolean isBreathMark() {
        return this == BREATH_MARK;
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
     *   <li>a <b>key signature</b> is an attribute of the measure its preceding barline or repeat
     *       opens, not an item of its own, so there is nothing in the file to hang an annotation
     *       on. Nothing is lost to the user: selecting a key signature selects that barline or
     *       repeat with it, and the annotation attaches there.</li>
     * </ul>
     *
     * <p>Attaching one anyway would lose it silently on save, so the annotation action disables
     * itself for such an element rather than let the user type text that cannot be stored. The
     * action asks whether <em>any</em> selected element can carry one, which is why a key
     * signature selected together with its barline still leaves the action enabled.
     */
    public boolean canCarryAnnotation() {
        return !isBreathMark() && this != KEY_CHANGE;
    }

    /**
     * Returns a lowercase, user-facing category name for this element type, suitable for
     * substitution into a message such as "There isn’t enough room on this line for this {0}."
     */
    public String categoryName() {
        if (isNote()) {
            return Strings.get(Strings.LABEL_ELEMENT_CATEGORY_NOTE);
        }

        if (isBarLine()) {
            return Strings.get(Strings.LABEL_ELEMENT_CATEGORY_BARLINE);
        }

        if (isRest()) {
            return Strings.get(Strings.LABEL_ELEMENT_CATEGORY_REST);
        }

        if (isRepeat()) {
            return Strings.get(Strings.LABEL_ELEMENT_CATEGORY_REPEAT);
        }

        if (isBreathMark()) {
            return Strings.get(Strings.LABEL_ELEMENT_CATEGORY_BREATH_MARK);
        }

        if (this == KEY_CHANGE) {
            return Strings.get(Strings.LABEL_ELEMENT_CATEGORY_KEY_CHANGE);
        }

        throw new IllegalStateException("No category name for element type " + this);
    }

    /**
     * Non-content elements are skipped during first-second ending validation
     * but are allowed to be present in the selection. Breath marks are
     * non-content: they may sit inside an ending but cannot anchor or end it.
     * The complement — the elements that can anchor or end an ending — is
     * exactly {@link #isDuration()} (notes and rests).
     * <p>
     * A key signature is non-content for two reasons that point the same way. It is not
     * musical content, so it must not help a selection reach the minimum an ending needs;
     * and a key change must not move where an ending anchors, which is what being skipped
     * delivers — the backward walk for the preceding element passes over it and reaches the
     * barline that {@link KeyChangeElement}'s position invariant guarantees behind it, so
     * the bracket lands exactly where it would have had the key change not been there.
     *
     * @return {@code true} for grace notes, breath marks and key signatures
     */
    public boolean isNonContentElement() {
        return isGraceNote() || this == BREATH_MARK || this == KEY_CHANGE;
    }

    /**
     * A terminal element can end a first-second ending selection:
     * single barline, double barline, final double barline, or left repeat.
     *
     * <p>This is about the right boundary of a first/second-ending bracket, not
     * {@link #isValidSongTerminal()}. {@code REPEAT_RIGHT} is deliberately excluded because it
     * plays the split role that closes the first ending and loops back to the second, so it
     * cannot also serve as the bracket's right boundary.
     */
    public boolean isEndingTerminal() {
        return isBarLine() || this == REPEAT_LEFT;
    }

    /**
     * Returns the rest equivalent of this type, or {@code this} if no rest counterpart exists.
     */
    public ElementType toRest() {
        //noinspection DuplicatedCode
        return switch (this) {
            case SEMIBREVE -> SEMIBREVE_REST;
            case MINIM -> MINIM_REST;
            case CROTCHET -> CROTCHET_REST;
            case QUAVER -> QUAVER_REST;
            case SEMIQUAVER -> SEMIQUAVER_REST;
            case DEMI_SEMIQUAVER -> DEMI_SEMIQUAVER_REST;
            default -> this;
        };
    }

    /**
     * Returns the note equivalent of this type, or {@code this} if no note counterpart exists.
     */
    public ElementType toNote() {
        //noinspection DuplicatedCode
        return switch (this) {
            case SEMIBREVE_REST -> SEMIBREVE;
            case MINIM_REST -> MINIM;
            case CROTCHET_REST -> CROTCHET;
            case QUAVER_REST -> QUAVER;
            case SEMIQUAVER_REST -> SEMIQUAVER;
            case DEMI_SEMIQUAVER_REST -> DEMI_SEMIQUAVER;
            default -> this;
        };
    }

    public boolean drawStaveLongitude() {
        return this != BREATH_MARK;
    }

    public boolean snapToEnd() {
        return (
            this == REPEAT_RIGHT ||
                this == SINGLE_BARLINE ||
                this == DOUBLE_BARLINE ||
                this == FINAL_DOUBLE_BARLINE
        );
    }

    /**
     * Returns {@code true} for barline types that, when present as the last element of
     * the last line, are replaced by the auto-maintained terminal rather than having
     * one appended after them: {@code SINGLE_BARLINE}, {@code DOUBLE_BARLINE},
     * {@code REPEAT_RIGHT}, {@code REPEAT_LEFT_RIGHT}. {@code REPEAT_LEFT} is
     * intentionally excluded — a left-facing repeat does not terminate the line, so
     * the terminal is appended.
     */
    public boolean isReplaceableByTerminal() {
        return this == SINGLE_BARLINE
            || this == DOUBLE_BARLINE
            || this == REPEAT_RIGHT
            || this == REPEAT_LEFT_RIGHT;
    }

    /**
     * Returns {@code true} for element types that may occupy the auto-maintained terminal
     * slot (last position of the last line): {@code FINAL_DOUBLE_BARLINE} or
     * {@code REPEAT_RIGHT}.
     *
     * <p>This is about the <b>song's</b> last slot, not {@link #isEndingTerminal()}.
     */
    public boolean isValidSongTerminal() {
        return this == FINAL_DOUBLE_BARLINE || this == REPEAT_RIGHT;
    }

    /**
     * Returns the SMuFL flag glyph for this note type and stem direction, or {@code null} if this
     * type has no flag (whole, half, quarter notes, rests, non-note types).
     *
     * @param direction {@code UP} for stem-up (flag on right of stem); {@code DOWN} for stem-down
     * @return The flag glyph, or {@code null} if this type carries no flag
     */
    @Nullable
    public SMuFLGlyph getFlagGlyph(StaffElement.Direction direction) {
        return switch (this) {
            case QUAVER -> direction.isUp() ? SMuFLGlyph.FLAG_8TH_UP : SMuFLGlyph.FLAG_8TH_DOWN;
            case GRACE_QUAVER -> SMuFLGlyph.FLAG_8TH_UP;
            case SEMIQUAVER -> direction.isUp() ? SMuFLGlyph.FLAG_16TH_UP : SMuFLGlyph.FLAG_16TH_DOWN;
            case DEMI_SEMIQUAVER -> direction.isUp() ? SMuFLGlyph.FLAG_32ND_UP : SMuFLGlyph.FLAG_32ND_DOWN;
            default -> null;
        };
    }

  // ========================================================================
    // Element bounds in staff spaces
    // ========================================================================

    private static void computeElementBoundsSs() {
        computeNoteBoundsSs(
            SEMIBREVE, MINIM, CROTCHET, QUAVER, SEMIQUAVER, DEMI_SEMIQUAVER);

        computeGraceNoteBoundsSs(GRACE_QUAVER);

        computeGlyphBoundsSs(
            SEMIBREVE_REST, MINIM_REST, CROTCHET_REST,
            QUAVER_REST, SEMIQUAVER_REST, DEMI_SEMIQUAVER_REST,
            BREATH_MARK);

        computeBarlineBoundsSs();
        computeRepeatBoundsSs();
        computeKeySignatureBoundsSs();

        // Copy bounds to alias types
        for (var type : values()) {
            if (type.aliasOf != null) {
                type.copyBoundsFrom(type.aliasOf);
            }
        }

        validateElementBounds();
    }

    private static void computeNoteBoundsSs(ElementType... types) {
        for (var type : types) {
            var glyph = SMUFL_GLYPHS.get(type);
            var bbox = requireBBox(glyph, type);
            var anchors = (glyph != null) ? SMuFLMetadata.requireAnchors(glyph) : null;

            if (anchors != null && anchors.stemUpSE() != null && anchors.stemDownNW() != null) {
                // Stemmed note
                var headTop = bbox.top();
                var headBottom = bbox.bottom();
                var headRight = bbox.right();
                var stemUpX = anchors.requireStemUpSE().x();
                var stemUpY = anchors.requireStemUpSE().y();
                var stemDownY = anchors.requireStemDownNW().y();

                // Width: max of notehead and stem-up flag extent
                var width = headRight;
                var flagGlyph = type.getFlagGlyph(StaffElement.Direction.UP);

                if (flagGlyph != null) {
                    var flagBBox = requireBBox(flagGlyph, type);
                    width = Math.max(width, stemUpX + flagBBox.right());
                }

                type.fullWidthSs = width;
                type.baseWidthSs = headRight;
                type.fullElementHeightSs = headBottom - headTop;
                type.noteheadTopOffsetSs = headTop;

                // Height up: from top of stem to bottom of notehead
                var upTop = stemUpY - SMuFLConstants.STEM_LENGTH_SS;
                type.heightUpSs = headBottom - upTop;
                type.topOffsetUpSs = upTop;    // stem tip above center (negative)

                // Height down: from top of notehead to bottom of stem
                var downBottom = stemDownY + SMuFLConstants.STEM_LENGTH_SS;
                type.heightDownSs = downBottom - headTop;
                type.topOffsetDownSs = headTop; // notehead top above center (negative)
            } else {
                // No stem (semibreve)
                type.fullWidthSs = bbox.right();
                type.baseWidthSs = bbox.right();
                type.fullElementHeightSs = bbox.height();
                type.noteheadTopOffsetSs = bbox.top();
                type.heightUpSs = bbox.height();
                type.heightDownSs = bbox.height();
                type.topOffsetUpSs = bbox.top();
                type.topOffsetDownSs = bbox.top();
            }
        }
    }

    private static void computeGraceNoteBoundsSs(ElementType type) {
        var headBBox = requireBBox(SMuFLGlyph.NOTEHEAD_BLACK, type);
        double scale = GRACE_NOTE_SCALE;

        var headBottom = headBBox.bottom() * scale;
        var headRight = headBBox.right() * scale;

        var anchors = SMuFLMetadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);

        if (anchors == null || anchors.stemUpSE() == null) {
            throw RuntimeError.missingResource("Missing stem anchors for NOTEHEAD_BLACK (needed for grace notes)");
        }

        var stemUpX = anchors.stemUpSE().x() * scale;
        var stemUpY = anchors.stemUpSE().y() * scale;

        var upTop = stemUpY - SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS;
        var width = headRight;

        var flagBBox = requireBBox(SMuFLGlyph.FLAG_8TH_UP, type);
        width = Math.max(width, stemUpX + flagBBox.right() * scale);

        var headTop = headBBox.top() * scale;
        var height = headBottom - upTop;

        type.setSymmetricBounds(width, height, upTop);
        type.baseWidthSs = headRight;
        type.fullElementHeightSs = headBottom - headTop;
        type.noteheadTopOffsetSs = headTop;
    }

    /**
     * Sets symmetric bounds where up/down values are identical and
     * noteheadWidth equals element width.
     */
    private void setSymmetricBounds(
        double width, double height, double topOffset
    ) {
        fullWidthSs = width;
        baseWidthSs = width;
        fullElementHeightSs = height;
        noteheadTopOffsetSs = topOffset;
        heightUpSs = height;
        heightDownSs = height;
        topOffsetUpSs = topOffset;
        topOffsetDownSs = topOffset;
    }

    private void copyBoundsFrom(ElementType source) {
        fullWidthSs = source.fullWidthSs;
        baseWidthSs = source.baseWidthSs;
        fullElementHeightSs = source.fullElementHeightSs;
        noteheadTopOffsetSs = source.noteheadTopOffsetSs;
        heightUpSs = source.heightUpSs;
        heightDownSs = source.heightDownSs;
        topOffsetUpSs = source.topOffsetUpSs;
        topOffsetDownSs = source.topOffsetDownSs;
    }

    private static void computeGlyphBoundsSs(ElementType... types) {
        for (var type : types) {
            var glyph = SMUFL_GLYPHS.get(type);
            var bbox = requireBBox(glyph, type);
            // Width is the glyph's right edge from the origin, not the bbox width, so that
            // baseWidthSs means the same thing for every glyph-bearing type (see
            // getElementWidthSs()). The two differ only when bBoxSW's x is non-zero.
            type.setSymmetricBounds(bbox.right(), bbox.height(), bbox.top());
        }
    }

    private static void computeBarlineBoundsSs() {
        var topOffset = -Staff.STAFF_HEIGHT_SS / 2;

        SINGLE_BARLINE.setSymmetricBounds(LineThickness.THIN_BARLINE_SS, Staff.STAFF_HEIGHT_SS, topOffset);
        DOUBLE_BARLINE.setSymmetricBounds(
            2 * LineThickness.THIN_BARLINE_SS + LineThickness.BARLINE_SEPARATION_SS, Staff.STAFF_HEIGHT_SS, topOffset);
        FINAL_DOUBLE_BARLINE.setSymmetricBounds(
            LineThickness.THIN_BARLINE_SS + LineThickness.THICK_BARLINE_SS + LineThickness.BARLINE_SEPARATION_SS,
            Staff.STAFF_HEIGHT_SS, topOffset);
    }

    private static void computeRepeatBoundsSs() {
        var dotsAdvance = SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS;
        var topOffset = -Staff.STAFF_HEIGHT_SS / 2;

        // Match the renderer's actual layout: dots | sep | thin | sep | thick
        var singleRepeatWidth = dotsAdvance + LineThickness.BARLINE_SEPARATION_SS + LineThickness.THIN_BARLINE_SS
            + LineThickness.BARLINE_SEPARATION_SS + LineThickness.THICK_BARLINE_SS;

        REPEAT_LEFT.setSymmetricBounds(singleRepeatWidth, Staff.STAFF_HEIGHT_SS, topOffset);
        REPEAT_RIGHT.setSymmetricBounds(singleRepeatWidth, Staff.STAFF_HEIGHT_SS, topOffset);

        // REPEAT_LEFT_RIGHT shares the thick bar: dots | sep | thin | sep | thick | sep | thin | sep | dots
        var leftRightWidth = 2 * dotsAdvance + 4 * LineThickness.BARLINE_SEPARATION_SS
            + 2 * LineThickness.THIN_BARLINE_SS + LineThickness.THICK_BARLINE_SS;
        REPEAT_LEFT_RIGHT.setSymmetricBounds(leftRightWidth, Staff.STAFF_HEIGHT_SS, topOffset);
    }

    /**
     * Sets {@code KEY_CHANGE}'s bounds to the degenerate case: the narrowest key signature
     * that draws anything, a single accidental, spanning the staff vertically the way a
     * barline does.
     * <p>
     * Every other type in this enum has one width, so the constant is the whole answer. A key
     * signature does not: its width depends on the {@link Key} it carries and on the key it
     * cancels, neither of which the enum constant knows. The real width comes from the element
     * instance ({@code KeyChangeElement.getContentWidthSs()}); what is set here is only the
     * floor, so that a caller reasoning from the type alone never over-reserves and
     * {@code validateElementBounds} still has something positive to check.
     */
    private static void computeKeySignatureBoundsSs() {
        var narrowestAccidentalSs = Math.min(
            StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_FLAT),
            StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_SHARP)
        );

        KEY_CHANGE.setSymmetricBounds(
            narrowestAccidentalSs, Staff.STAFF_HEIGHT_SS, -Staff.STAFF_HEIGHT_SS / 2);
    }

    private static BBox requireBBox(@Nullable SMuFLGlyph glyph, ElementType context) {
        var bbox = (glyph != null) ? SMuFLMetadata.getBBox(glyph) : null;

        if (bbox == null) {
            throw RuntimeError.missingResource("Missing SMuFL bounding box for " + glyph + " (needed by " + context + ')');
        }

        return bbox;
    }

    private static void validateElementBounds() {
        for (var type : values()) {
            if (type.aliasOf != null) {
                continue;
            }

            if (type.fullWidthSs <= 0 || type.heightUpSs <= 0 || type.heightDownSs <= 0) {
                throw RuntimeError.missingResource("Invalid element bounds for " + type +
                    ": widthSs=" + type.fullWidthSs +
                    ", heightUpSs=" + type.heightUpSs +
                    ", heightDownSs=" + type.heightDownSs);
            }
        }
    }
}
