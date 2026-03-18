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

package songscribe.music;

import static songscribe.ui.playback.PlaybackController.PPQ;

import module java.desktop;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.smufl.BBox;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LayoutConstants;
import songscribe.error.FatalError;
import songscribe.util.UIUtils;

@SuppressWarnings("ALL")
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
    PASTE(null, 0, 0),
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
        // Set up singleton markers
        StaffElement.GLISSANDO_PLACEHOLDER.initType(GLISSANDO);
        StaffElement.PASTE_PLACEHOLDER.initType(PASTE);
        GLISSANDO.instance = StaffElement.GLISSANDO_PLACEHOLDER;
        PASTE.instance = StaffElement.PASTE_PLACEHOLDER;

        // Create instances for canonical types
        for (var type : values()) {
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

    static {
        computeElementBoundsSs();
    }

    public static int getMenuShortcutKeyMask() {
        return !GraphicsEnvironment.isHeadless()
            ? UIUtils.MENU_SHORTCUT_MASK
            : 0;
    }

    @Nullable
    private StaffElement instance;
    @Nullable
    private final String name;
    @Nullable
    private final KeyStroke acceleratorKey;
    private final int defaultDuration;
    private final int defaultStaffPosition;
    @Nullable
    private final ElementType aliasOf;
    private double widthSs;
    private double noteheadWidthSs;
    private double noteheadHeightSs;
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
        this.aliasOf = null;

        if (keyCode != 0) {
            if (modifiers == -1) {
                modifiers = getMenuShortcutKeyMask();
            }

            this.acceleratorKey = KeyStroke.getKeyStroke(keyCode, modifiers);
        } else {
            this.acceleratorKey = null;
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
        this.name = aliasOf.name;
        this.acceleratorKey = aliasOf.acceleratorKey;
        this.defaultDuration = aliasOf.defaultDuration;
        this.defaultStaffPosition = aliasOf.defaultStaffPosition;
    }

    private StaffElement createDefaultInstance() {
        if (isRest() || isNonDuration()) {
            return new StructuralElement(this);
        }

        return new StaffElement(this);
    }

    public @Nullable StaffElement getInstance() {
        return instance;
    }

    public StaffElement newInstance() {
        if (this == GLISSANDO || this == PASTE) {
            return Objects.requireNonNull(instance, this + ".instance not initialized");
        }

        return Objects.requireNonNull(instance, this + ".instance not initialized").clone();
    }

    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getTip() {
        if (name == null) {
            return null;
        }

        return UIUtils.makeTooltipWithKeystroke(name, acceleratorKey);
    }

    public @Nullable KeyStroke getAcceleratorKey() {
        return acceleratorKey;
    }

    private void requireVisualBounds() {
        if (this == GLISSANDO || this == PASTE) {
            throw new UnsupportedOperationException(name() + " has no visual bounds");
        }
    }

    /**
     * Returns the element width in staff spaces. Includes flag extent for stemmed notes.
     *
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getElementWidthSs() {
        requireVisualBounds();
        return widthSs;
    }

    /**
     * Returns the element height in staff spaces for the given stem direction.
     *
     * @param upper {@code true} for stem-up; {@code false} for stem-down
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getElementHeightSs(boolean upper) {
        requireVisualBounds();
        return upper ? heightUpSs : heightDownSs;
    }

    /**
     * Returns the horizontal center of the element in staff spaces.
     *
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getCenterXSs() {
        return getElementWidthSs() / 2;
    }

    /**
     * Returns the notehead width in staff spaces, excluding flag extent.
     * For non-note types (rests, barlines, etc.), returns the element width.
     *
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getNoteheadWidthSs() {
        requireVisualBounds();
        return noteheadWidthSs;
    }

    /**
     * Returns the horizontal center of the notehead in staff spaces.
     * For non-note types, returns the element center.
     *
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getNoteheadCenterXSs() {
        return getNoteheadWidthSs() / 2;
    }

    /**
     * Returns the notehead height in staff spaces (excludes the stem).
     * For non-stemmed elements this equals the full element height.
     *
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getNoteheadHeightSs() {
        requireVisualBounds();
        return noteheadHeightSs;
    }

    /**
     * Returns the Y offset in staff spaces from the notehead center to the top of the notehead.
     * The returned value is negative (the top is above the note center).
     * For non-stemmed elements this equals {@link #getTopYOffsetSs(boolean)}.
     *
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getNoteheadTopOffsetSs() {
        requireVisualBounds();
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
     * @throws UnsupportedOperationException for GLISSANDO and PASTE (no visual bounds)
     */
    public double getTopYOffsetSs(boolean upper) {
        requireVisualBounds();
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
     * Returns the rest equivalent of this type, or {@code this} if no rest counterpart exists.
     */
    public ElementType toRest() {
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
        var metadata = SMuFLMetadata.getInstance();
        var defaults = metadata.getEngravingDefaults();

        computeNoteBoundsSs(metadata,
            SEMIBREVE, MINIM, CROTCHET, QUAVER, SEMIQUAVER, DEMI_SEMIQUAVER);

        computeGraceNoteBoundsSs(metadata, GRACE_QUAVER);

        computeGlyphBoundsSs(metadata,
            SEMIBREVE_REST, MINIM_REST, CROTCHET_REST,
            QUAVER_REST, SEMIQUAVER_REST, DEMI_SEMIQUAVER_REST,
            BREATH_MARK);

        computeBarlineBoundsSs(defaults);
        computeRepeatBoundsSs(metadata, defaults);

        // Copy bounds to alias types
        for (var type : values()) {
            if (type.aliasOf != null) {
                type.copyBoundsFrom(type.aliasOf);
            }
        }

        validateElementBounds();
    }

    private static void computeNoteBoundsSs(SMuFLMetadata metadata, ElementType... types) {
        for (var type : types) {
            var glyph = SMUFL_GLYPHS.get(type);
            var bbox = requireBBox(metadata, glyph, type);
            var anchors = (glyph != null) ? metadata.getAnchors(glyph) : null;

            if (anchors != null && anchors.stemUpSE() != null && anchors.stemDownNW() != null) {
                // Stemmed note
                double headTop = bbox.top();
                double headBottom = bbox.bottom();
                double headRight = bbox.right();
                double stemUpX = anchors.stemUpSE().x();
                double stemUpY = anchors.stemUpSE().y();
                double stemDownY = anchors.stemDownNW().y();

                // Width: max of notehead and stem-up flag extent
                double width = headRight;
                var flagGlyph = getStemUpFlagGlyph(type);

                if (flagGlyph != null) {
                    var flagBBox = metadata.getBBox(flagGlyph);

                    if (flagBBox != null) {
                        width = Math.max(width, stemUpX + flagBBox.right());
                    }
                }

                type.widthSs = width;
                type.noteheadWidthSs = headRight;
                type.noteheadHeightSs = headBottom - headTop;
                type.noteheadTopOffsetSs = headTop;

                // Height up: from top of stem to bottom of notehead
                double upTop = stemUpY - LayoutConstants.STEM_LENGTH_SS;
                type.heightUpSs = headBottom - upTop;
                type.topOffsetUpSs = upTop;    // stem tip above center (negative)

                // Height down: from top of notehead to bottom of stem
                double downBottom = stemDownY + LayoutConstants.STEM_LENGTH_SS;
                type.heightDownSs = downBottom - headTop;
                type.topOffsetDownSs = headTop; // notehead top above center (negative)
            } else {
                // No stem (semibreve)
                type.widthSs = bbox.right();
                type.noteheadWidthSs = bbox.right();
                type.noteheadHeightSs = bbox.height();
                type.noteheadTopOffsetSs = bbox.top();
                type.heightUpSs = bbox.height();
                type.heightDownSs = bbox.height();
                type.topOffsetUpSs = bbox.top();
                type.topOffsetDownSs = bbox.top();
            }
        }
    }

    private static void computeGraceNoteBoundsSs(SMuFLMetadata metadata, ElementType type) {
        var headBBox = requireBBox(metadata, SMuFLGlyph.NOTEHEAD_BLACK, type);
        double scale = LayoutConstants.GRACE_NOTE_SCALE;

        double headBottom = headBBox.bottom() * scale;
        double headRight = headBBox.right() * scale;

        var anchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);

        if (anchors == null || anchors.stemUpSE() == null) {
            FatalError.exit("Missing stem anchors for NOTEHEAD_BLACK (needed for grace notes)");
            throw new AssertionError("unreachable");
        }

        double stemUpX = anchors.stemUpSE().x() * scale;
        double stemUpY = anchors.stemUpSE().y() * scale;

        double upTop = stemUpY - LayoutConstants.GRACE_NOTE_STEM_LENGTH_SS;
        double width = headRight;

        var flagBBox = metadata.getBBox(SMuFLGlyph.FLAG_8TH_UP);

        if (flagBBox != null) {
            width = Math.max(width, stemUpX + flagBBox.right() * scale);
        }

        double headTop = headBBox.top() * scale;
        double height = headBottom - upTop;

        type.setSymmetricBounds(width, height, upTop);
        type.noteheadWidthSs = headRight;
        type.noteheadHeightSs = headBottom - headTop;
        type.noteheadTopOffsetSs = headTop;
    }

    /**
     * Sets symmetric bounds where up/down values are identical and
     * noteheadWidth equals element width.
     */
    private void setSymmetricBounds(
        double width, double height, double topOffset
    ) {
        this.widthSs = width;
        this.noteheadWidthSs = width;
        this.noteheadHeightSs = height;
        this.noteheadTopOffsetSs = topOffset;
        this.heightUpSs = height;
        this.heightDownSs = height;
        this.topOffsetUpSs = topOffset;
        this.topOffsetDownSs = topOffset;
    }

    private void copyBoundsFrom(ElementType source) {
        this.widthSs = source.widthSs;
        this.noteheadWidthSs = source.noteheadWidthSs;
        this.noteheadHeightSs = source.noteheadHeightSs;
        this.noteheadTopOffsetSs = source.noteheadTopOffsetSs;
        this.heightUpSs = source.heightUpSs;
        this.heightDownSs = source.heightDownSs;
        this.topOffsetUpSs = source.topOffsetUpSs;
        this.topOffsetDownSs = source.topOffsetDownSs;
    }

    private static void computeGlyphBoundsSs(SMuFLMetadata metadata, ElementType... types) {
        for (var type : types) {
            var glyph = SMUFL_GLYPHS.get(type);
            var bbox = requireBBox(metadata, glyph, type);
            type.setSymmetricBounds(bbox.width(), bbox.height(), bbox.top());
        }
    }

    private static void computeBarlineBoundsSs(EngravingDefaults defaults) {
        double thin = defaults.thinBarlineThickness();
        double thick = defaults.thickBarlineThickness();
        double sep = defaults.barlineSeparation();
        double staffHeight = LayoutConstants.STAFF_HEIGHT_SS;
        double topOffset = -staffHeight / 2;

        SINGLE_BARLINE.setSymmetricBounds(thin, staffHeight, topOffset);
        DOUBLE_BARLINE.setSymmetricBounds(2 * thin + sep, staffHeight, topOffset);
        FINAL_DOUBLE_BARLINE.setSymmetricBounds(thin + thick + sep, staffHeight, topOffset);
    }

    private static void computeRepeatBoundsSs(
        SMuFLMetadata metadata, EngravingDefaults defaults
    ) {
        double thin = defaults.thinBarlineThickness();
        double thick = defaults.thickBarlineThickness();
        double sep = defaults.barlineSeparation();
        double dotSep = defaults.repeatBarlineDotSeparation();
        var dotBBox = requireBBox(metadata, SMuFLGlyph.REPEAT_DOT, REPEAT_LEFT);
        double dotWidth = dotBBox.width();
        double staffHeight = LayoutConstants.STAFF_HEIGHT_SS;
        double topOffset = -staffHeight / 2;

        double singleRepeatWidth = thin + thick + sep + dotSep + dotWidth;

        REPEAT_LEFT.setSymmetricBounds(singleRepeatWidth, staffHeight, topOffset);
        REPEAT_RIGHT.setSymmetricBounds(singleRepeatWidth, staffHeight, topOffset);
        REPEAT_LEFT_RIGHT.setSymmetricBounds(2 * singleRepeatWidth, staffHeight, topOffset);
    }

    private static BBox requireBBox(SMuFLMetadata metadata, @Nullable SMuFLGlyph glyph, ElementType context) {
        var bbox = (glyph != null) ? metadata.getBBox(glyph) : null;

        if (bbox == null) {
            FatalError.exit("Missing SMuFL bounding box for " + glyph + " (needed by " + context + ")");
            throw new AssertionError("unreachable");
        }

        return bbox;
    }

    private static void validateElementBounds() {
        for (var type : values()) {
            if (type == GLISSANDO || type == PASTE || type.aliasOf != null) {
                continue;
            }

            if (type.widthSs <= 0 || type.heightUpSs <= 0 || type.heightDownSs <= 0) {
                FatalError.exit("Invalid element bounds for " + type +
                    ": widthSs=" + type.widthSs +
                    ", heightUpSs=" + type.heightUpSs +
                    ", heightDownSs=" + type.heightDownSs);
            }
        }
    }
}
