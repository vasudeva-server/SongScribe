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

import songscribe.smufl.BBox;
import songscribe.smufl.Engraving;
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
    DEMI_SEMIQUAVER("Thirtysecond note", KeyEvent.VK_1, 0, PPQ / 8, 0),

    // Rests
    SEMIBREVE_REST("Whole rest", KeyEvent.VK_6, -1, PPQ * 4, -1),
    MINIM_REST("Half rest", KeyEvent.VK_5, -1, PPQ * 2, 0),
    CROTCHET_REST("Quarter rest", KeyEvent.VK_4, -1, PPQ, 0),
    QUAVER_REST("Eighth rest", KeyEvent.VK_3, -1, PPQ / 2, 0),
    SEMIQUAVER_REST("Sixteenth rest", KeyEvent.VK_2, -1, PPQ / 4, 0),
    DEMI_SEMIQUAVER_REST("Thirtysecond rest", KeyEvent.VK_1, -1, PPQ / 8, 0),

    // Grace notes
    GRACE_QUAVER("Grace eighth", KeyEvent.VK_G, 0, 0, 0),

    // Other
    GLISSANDO("Glissando", KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK, 0, 0),
    REPEAT_LEFT("Repeat left", KeyEvent.VK_L, 0, 0, 0),
    REPEAT_RIGHT("Repeat right", KeyEvent.VK_R, 0, 0, 0),
    REPEAT_LEFT_RIGHT("Repeate left/right", 0, 0),
    BREATH_MARK("Breath mark", 0, -7),
    SINGLE_BARLINE("Single barline", 0, 0),
    DOUBLE_BARLINE("Double barline", 0, 0),
    FINAL_DOUBLE_BARLINE("Final double barline", 0, 0),
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

    /** Normal note stem length in staff spaces. */
    public static final double STEM_LENGTH_SS = 3.5;

    /** Grace note stem length in staff spaces. */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

    /** Uniform scale factor applied to grace note glyphs (75% of normal size). */
    public static final float GRACE_NOTE_SCALE = 0.75f;

    /** Height of the 5-line staff (4 gaps of 1 ss each) in staff spaces. */
    public static final double STAFF_HEIGHT_SS = 4.0;

    // LilyPond-derived barline thicknesses in staff spaces (base = 0.1 ss)
    private static final double BASE_LINE_THICKNESS_SS = 0.1;
    static final double THIN_BARLINE_SS  = BASE_LINE_THICKNESS_SS * 1.9;
    static final double THICK_BARLINE_SS = BASE_LINE_THICKNESS_SS * 6.0;
    static final double BARLINE_SEP_SS   = BASE_LINE_THICKNESS_SS * 3.0;

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

    private StaffElement createDefaultInstance() {
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
     * @param upper {@code true} for stem-up; {@code false} for stem-down
     */
    public double getElementHeightSs(boolean upper) {
        return upper ? heightUpSs : heightDownSs;
    }

    /**
     * Returns the horizontal center of the element in staff spaces.
     * For note types, includes the flag.
     */
    public double getFullElementCenterXSs() {
        return fullWidthSs / 2;
    }

    /**
     * Returns the element width in staff spaces, excluding flag extent for note types.
     */
    public double getElementWidthSs() {
        return baseWidthSs;
    }

    /**
     * Returns the horizontal center of the element in staff spaces.
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
     * @param terminalType the terminal element type ({@link #isValidTerminal()} must be true)
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
            return THICK_BARLINE_SS + BARLINE_SEP_SS + THIN_BARLINE_SS / 2;
        }

        if (isBarLine() || isRepeat()) {
            // Generic: center on the rightmost thin barline
            return fullWidthSs - THIN_BARLINE_SS / 2;
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
     * For non-stemmed elements this equals {@link #getTopYOffsetSs(boolean)}.
     */
    public double getNoteheadTopOffsetSs() {
        return noteheadTopOffsetSs;
    }

    /**
     * Returns the Y offset in staff spaces from the notehead center to the top of the element.
     * The returned value is negative (the top is above the note center).
     * <p>
     * For stem-up notes this is the stem tip; for stem-down the notehead top; for
     * non-stemmed elements the glyph bbox top or half of the staff height above center.
     *
     * @param upper {@code true} for stem-up; {@code false} for stem-down
     */
    public double getTopYOffsetSs(boolean upper) {
        return upper ? topOffsetUpSs : topOffsetDownSs;
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
        return isBarLine() || isRepeat() || this == BREATH_MARK;
    }

    public boolean isGraceNote() {
        return this == GRACE_QUAVER;
    }

    /**
     * A content element is a note, rest, or breath mark — elements that carry
     * musical content. Grace notes and glissandos are non-content.
     */
    public boolean isContentElement() {
        return isPitchedNote() || isRest() || this == BREATH_MARK;
    }

    /**
     * Non-content elements are skipped during first-second ending validation
     * but are allowed to be present in the selection.
     */
    public boolean isNonContentElement() {
        return isGraceNote() || this == GLISSANDO;
    }

    /**
     * A terminal element can end a first-second ending selection:
     * single barline, double barline, final double barline, or left repeat.
     */
    public boolean isTerminal() {
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
     */
    public boolean isValidTerminal() {
        return this == FINAL_DOUBLE_BARLINE || this == REPEAT_RIGHT;
    }

    /**
     * Returns the SMuFL flag glyph for this note type and stem direction, or {@code null} if this
     * type has no flag (whole, half, quarter notes, rests, non-note types).
     *
     * @param upper {@code true} for stem-up (flag on right of stem); {@code false} for stem-down
     * @return The flag glyph, or {@code null} if this type carries no flag
     */
    @Nullable
    public SMuFLGlyph getFlagGlyph(boolean upper) {
        return switch (this) {
            case QUAVER -> upper ? SMuFLGlyph.FLAG_8TH_UP : SMuFLGlyph.FLAG_8TH_DOWN;
            case GRACE_QUAVER -> SMuFLGlyph.FLAG_8TH_UP;
            case SEMIQUAVER -> upper ? SMuFLGlyph.FLAG_16TH_UP : SMuFLGlyph.FLAG_16TH_DOWN;
            case DEMI_SEMIQUAVER -> upper ? SMuFLGlyph.FLAG_32ND_UP : SMuFLGlyph.FLAG_32ND_DOWN;
            default -> null;
        };
    }

    @Nullable
    private static SMuFLGlyph getStemUpFlagGlyph(ElementType type) {
        return type.getFlagGlyph(true);
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

        // Glissandos are rendered as lines between two notes — their actual visual
        // extent is context-dependent. These nominal bounds satisfy the non-zero
        // contract without affecting layout (glissandos are decorations on notes,
        // not standalone positioned elements).
        GLISSANDO.setSymmetricBounds(1, 1, -0.5);  // widthSs=1, heightSs=1, topOffsetSs=-0.5

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
                var flagGlyph = getStemUpFlagGlyph(type);

                if (flagGlyph != null) {
                    var flagBBox = requireBBox(flagGlyph, type);
                    width = Math.max(width, stemUpX + flagBBox.right());
                }

                type.fullWidthSs = width;
                type.baseWidthSs = headRight;
                type.fullElementHeightSs = headBottom - headTop;
                type.noteheadTopOffsetSs = headTop;

                // Height up: from top of stem to bottom of notehead
                var upTop = stemUpY - STEM_LENGTH_SS;
                type.heightUpSs = headBottom - upTop;
                type.topOffsetUpSs = upTop;    // stem tip above center (negative)

                // Height down: from top of notehead to bottom of stem
                var downBottom = stemDownY + STEM_LENGTH_SS;
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
            throw RuntimeError.exit("Missing stem anchors for NOTEHEAD_BLACK (needed for grace notes)");
        }

        var stemUpX = anchors.stemUpSE().x() * scale;
        var stemUpY = anchors.stemUpSE().y() * scale;

        var upTop = stemUpY - GRACE_NOTE_STEM_LENGTH_SS;
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
            type.setSymmetricBounds(bbox.width(), bbox.height(), bbox.top());
        }
    }

    private static void computeBarlineBoundsSs() {
        var topOffset = -STAFF_HEIGHT_SS / 2;

        SINGLE_BARLINE.setSymmetricBounds(THIN_BARLINE_SS, STAFF_HEIGHT_SS, topOffset);
        DOUBLE_BARLINE.setSymmetricBounds(2 * THIN_BARLINE_SS + BARLINE_SEP_SS, STAFF_HEIGHT_SS, topOffset);
        FINAL_DOUBLE_BARLINE.setSymmetricBounds(THIN_BARLINE_SS + THICK_BARLINE_SS + BARLINE_SEP_SS, STAFF_HEIGHT_SS, topOffset);
    }

    private static void computeRepeatBoundsSs() {
        var dotsAdvance = Engraving.REPEAT_DOTS_ADVANCE_WIDTH_SS;
        var topOffset = -STAFF_HEIGHT_SS / 2;

        // Match the renderer's actual layout: dots | sep | thin | sep | thick
        var singleRepeatWidth = dotsAdvance + BARLINE_SEP_SS + THIN_BARLINE_SS + BARLINE_SEP_SS + THICK_BARLINE_SS;

        REPEAT_LEFT.setSymmetricBounds(singleRepeatWidth, STAFF_HEIGHT_SS, topOffset);
        REPEAT_RIGHT.setSymmetricBounds(singleRepeatWidth, STAFF_HEIGHT_SS, topOffset);

        // REPEAT_LEFT_RIGHT shares the thick bar: dots | sep | thin | sep | thick | sep | thin | sep | dots
        var leftRightWidth = 2 * dotsAdvance + 4 * BARLINE_SEP_SS + 2 * THIN_BARLINE_SS + THICK_BARLINE_SS;
        REPEAT_LEFT_RIGHT.setSymmetricBounds(leftRightWidth, STAFF_HEIGHT_SS, topOffset);
    }

    private static BBox requireBBox(@Nullable SMuFLGlyph glyph, ElementType context) {
        var bbox = (glyph != null) ? SMuFLMetadata.getBBox(glyph) : null;

        if (bbox == null) {
            throw RuntimeError.exit("Missing SMuFL bounding box for " + glyph + " (needed by " + context + ')');
        }

        return bbox;
    }

    private static void validateElementBounds() {
        for (var type : values()) {
            if (type.aliasOf != null) {
                continue;
            }

            if (type.fullWidthSs <= 0 || type.heightUpSs <= 0 || type.heightDownSs <= 0) {
                throw RuntimeError.exit("Invalid element bounds for " + type +
                    ": widthSs=" + type.fullWidthSs +
                    ", heightUpSs=" + type.heightUpSs +
                    ", heightDownSs=" + type.heightDownSs);
            }
        }
    }
}
