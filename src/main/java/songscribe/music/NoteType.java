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

import java.awt.*;
import java.awt.event.*;
import java.util.Map;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.renderer.GraceNoteRenderer;
import songscribe.util.UIUtils;

import static songscribe.ui.playback.PlaybackController.PPQ;

@SuppressWarnings("ALL")
public enum NoteType {
    // Notes
    SEMIBREVE(
        "Whole note", KeyEvent.VK_6, 0,
        new Rectangle(0, 24, 15, 8), new Rectangle(0, 24, 15, 8),
        PPQ * 4, 0
    ),
    MINIM(
        "Half note", KeyEvent.VK_5, 0,
        new Rectangle(0, 0, 11, 32), new Rectangle(0, 24, 11, 32),
        PPQ * 2, 0
    ),
    CROTCHET(
        "Quarter note", KeyEvent.VK_4, 0,
        new Rectangle(0, 0, 11, 32), new Rectangle(0, 24, 11, 32),
        PPQ, 0
    ),
    QUAVER(
        "Eighth note", KeyEvent.VK_3, 0,
        new Rectangle(0, 0, 18, 32), new Rectangle(0, 24, 11, 32),
        PPQ / 2, 0
    ),
    SEMIQUAVER(
        "Sixteenth note", KeyEvent.VK_2, 0,
        new Rectangle(0, 0, 19, 32), new Rectangle(0, 24, 11, 32),
        PPQ / 4, 0
    ),
    DEMI_SEMIQUAVER(
        "Thirtysecond note", KeyEvent.VK_1, 0,
        new Rectangle(0, 0, 19, 32), new Rectangle(0, 24, 11, 32),
        PPQ / 8, 0
    ),

    // Rests
    SEMIBREVE_REST(
        "Whole rest", KeyEvent.VK_6, -1,
        new Rectangle(0, 21, 9, 4), new Rectangle(0, 21, 9, 4),
        PPQ * 4, -1
    ),
    MINIM_REST(
        "Half rest", KeyEvent.VK_5, -1,
        new Rectangle(0, 24, 9, 4), new Rectangle(0, 24, 9, 4),
        PPQ * 2, 0
    ),
    CROTCHET_REST(
        "Quarter rest", KeyEvent.VK_4, -1,
        new Rectangle(0, 15, 8, 25), new Rectangle(0, 15, 8, 25),
        PPQ, 0
    ),
    QUAVER_REST(
        "Eighth rest", KeyEvent.VK_3, -1,
        new Rectangle(0, 22, 9, 15), new Rectangle(0, 22, 9, 15),
        PPQ / 2, 0
    ),
    SEMIQUAVER_REST(
        "Sixteenth rest", KeyEvent.VK_2, -1,
        new Rectangle(0, 21, 12, 22), new Rectangle(0, 21, 12, 22),
        PPQ / 4, 0
    ),
    DEMI_SEMIQUAVER_REST(
        "Thirtysecond rest", KeyEvent.VK_1, -1,
        new Rectangle(0, 13, 14, 34), new Rectangle(0, 13, 14, 34),
        PPQ / 8, 0
    ),

    // Grace notes
    GRACE_QUAVER(
        "Grace eighth", KeyEvent.VK_G, 0,
        new Rectangle(2, 11, 13, 20), new Rectangle(0, 24, 9, 20),
        0, 0
    ),
    // Other
    GLISSANDO(
        "Glissando", KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK,
        null, null, 0, 0
    ),
    REPEAT_LEFT(
        "Repeat left", KeyEvent.VK_L, 0,
        new Rectangle(0, 12, 13, 32), new Rectangle(0, 12, 13, 32),
        0, 0
    ),
    REPEAT_RIGHT(
        "Repeat right", KeyEvent.VK_R, 0,
        new Rectangle(5, 12, 13, 32), new Rectangle(5, 12, 13, 32),
        0, 0
    ),
    REPEAT_LEFT_RIGHT(
        "Repeate left/right",
        new Rectangle(0, 12, 22, 32), new Rectangle(0, 12, 22, 32),
        0, 0
    ),
    BREATH_MARK(
        "Breath mark",
        new Rectangle(1, 24, 6, 11), new Rectangle(1, 24, 6, 11),
        0, -7
    ),
    SINGLE_BARLINE(
        "Single barline",
        new Rectangle(17, 12, 1, 32), new Rectangle(17, 12, 1, 32),
        0, 0
    ),
    DOUBLE_BARLINE(
        "Double barline",
        new Rectangle(12, 12, 6, 32), new Rectangle(12, 12, 6, 32),
        0, 0
    ),
    FINAL_DOUBLE_BARLINE(
        "Final double barline",
        new Rectangle(10, 12, 8, 32), new Rectangle(10, 12, 8, 32),
        0, 0
    ),

    PASTE(
        null,
        new Rectangle(0, 12, 18, 38), new Rectangle(0, 12, 18, 38),
        0, 0
    ),
    // IO aliases
    SEMIBREVEREST(NoteType.SEMIBREVE_REST),
    MINIMREST(NoteType.MINIM_REST),
    CROTCHETREST(NoteType.CROTCHET_REST),
    QUAVERREST(NoteType.QUAVER_REST),
    SEMIQUAVERREST(NoteType.SEMIQUAVER_REST),
    DEMISEMIQUAVERREST(NoteType.DEMI_SEMIQUAVER_REST),
    GRACEQUAVER(NoteType.GRACE_QUAVER),
    GRACESEMIQUAVER(NoteType.GRACE_QUAVER),
    REPEATLEFT(NoteType.REPEAT_LEFT),
    REPEATRIGHT(NoteType.REPEAT_RIGHT),
    REPEATLEFTRIGHT(NoteType.REPEAT_LEFT_RIGHT),
    BREATHMARK(NoteType.BREATH_MARK),
    SINGLEBARLINE(NoteType.SINGLE_BARLINE),
    DOUBLEBARLINE(NoteType.DOUBLE_BARLINE),
    FINALDOUBLEBARLINE(NoteType.FINAL_DOUBLE_BARLINE);

    static {
        // Set up singleton markers
        Note.GLISSANDO_NOTE.initNoteType(GLISSANDO);
        Note.PASTE_NOTE.initNoteType(PASTE);
        GLISSANDO.instance = Note.GLISSANDO_NOTE;
        PASTE.instance = Note.PASTE_NOTE;

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
     * Maps each NoteType to its corresponding SMuFL notehead/rest glyph.
     * Used for metadata-driven bounds computation and rendering.
     */
    private static final Map<NoteType, SMuFLGlyph> SMUFL_NOTEHEADS = Map.ofEntries(
        Map.entry(SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE),
        Map.entry(MINIM, SMuFLGlyph.NOTEHEAD_HALF),
        Map.entry(CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(QUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
        Map.entry(SEMIBREVE_REST, SMuFLGlyph.REST_WHOLE),
        Map.entry(MINIM_REST, SMuFLGlyph.REST_HALF),
        Map.entry(CROTCHET_REST, SMuFLGlyph.REST_QUARTER),
        Map.entry(QUAVER_REST, SMuFLGlyph.REST_8TH),
        Map.entry(SEMIQUAVER_REST, SMuFLGlyph.REST_16TH),
        Map.entry(DEMI_SEMIQUAVER_REST, SMuFLGlyph.REST_32ND)
    );

    private static final double STEM_LENGTH_SS = 3.5;

    static {
        recomputeRectsFromSMuFL();
    }

    public static int getMenuShortcutKeyMask() {
        return !GraphicsEnvironment.isHeadless()
            ? UIUtils.MENU_SHORTCUT_MASK
            : 0;
    }

    private Note instance;
    private final String name;
    private final KeyStroke acceleratorKey;
    private final Rectangle realUpNoteRect;
    private final Rectangle realDownNoteRect;
    private final int defaultDuration;
    private final int defaultStaffPosition;
    private final NoteType aliasOf;

    NoteType(
        String name,
        int keyCode,
        int modifiers,
        Rectangle realUp,
        Rectangle realDown,
        int defaultDuration,
        int defaultStaffPosition
    ) {
        this.name = name;
        this.realUpNoteRect = realUp;
        this.realDownNoteRect = realDown;
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

    NoteType(
        String name,
        Rectangle realUp,
        Rectangle realDown,
        int defaultDuration,
        int defaultStaffPosition
    ) {
        this(name, 0, 0, realUp, realDown, defaultDuration, defaultStaffPosition);
    }

    NoteType(NoteType aliasOf) {
        this.aliasOf = aliasOf;
        this.name = aliasOf.name;
        this.acceleratorKey = aliasOf.acceleratorKey;
        this.realUpNoteRect = aliasOf.realUpNoteRect;
        this.realDownNoteRect = aliasOf.realDownNoteRect;
        this.defaultDuration = aliasOf.defaultDuration;
        this.defaultStaffPosition = aliasOf.defaultStaffPosition;
    }

    private Note createDefaultInstance() {
        if (isRest() || isBarLine() || isRepeat() || this == BREATH_MARK) {
            return new NonNote(this);
        }

        return new Note(this);
    }

    public Note getInstance() {
        return instance;
    }

    public Note newInstance() {
        if (this == GLISSANDO || this == PASTE) {
            return instance;
        }

        return instance.clone();
    }

    public String getName() {
        return name;
    }

    public String getTip() {
        return UIUtils.makeTooltipWithKeystroke(name, acceleratorKey);
    }

    public KeyStroke getAcceleratorKey() {
        return acceleratorKey;
    }

    public Rectangle getRealUpNoteRect() {
        return realUpNoteRect;
    }

    public Rectangle getRealDownNoteRect() {
        return realDownNoteRect;
    }

    public int getDefaultDuration() {
        return defaultDuration;
    }

    public int getDefaultStaffPosition() {
        return defaultStaffPosition;
    }

    /**
     * Returns the SMuFL glyph corresponding to this note type's notehead or rest.
     * Returns null for non-musical elements (barlines, repeats, breath marks, etc.).
     */
    @Nullable
    public SMuFLGlyph getSMuFLNoteheadGlyph() {
        return SMUFL_NOTEHEADS.get(this);
    }

    public boolean isRealNote() {
        //noinspection ConstantValue
        return (
            ordinal() >= SEMIBREVE.ordinal() &&
            ordinal() <= DEMI_SEMIQUAVER.ordinal()
        );
    }

    public boolean isNote() {
        return isRealNote() || isGraceNote();
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

    public boolean isGraceNote() {
        return this == GRACE_QUAVER;
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

    // ========================================================================
    // SMuFL rectangle computation
    // ========================================================================

    private static void recomputeRectsFromSMuFL() {
        var metadata = SMuFLMetadata.getInstance();
        int hotSpotY = Note.HOT_SPOT.y;

        // Regular notes
        computeNoteRects(metadata, hotSpotY,
            SEMIBREVE, MINIM, CROTCHET, QUAVER, SEMIQUAVER, DEMI_SEMIQUAVER);

        // Grace notes use pre-composed acciaccatura glyphs (already at correct size)
        computeGraceNoteRects(metadata, hotSpotY, GRACE_QUAVER);

        // Rests
        for (var type : new NoteType[] {
            SEMIBREVE_REST, MINIM_REST, CROTCHET_REST,
            QUAVER_REST, SEMIQUAVER_REST, DEMI_SEMIQUAVER_REST
        }) {
            var glyph = SMUFL_NOTEHEADS.get(type);
            var bbox = metadata.getBBox(glyph);

            if (bbox == null) {
                continue;
            }

            int y = (int) Math.round(StaffSpaces.toPixels(bbox.top()) + hotSpotY);
            int w = (int) Math.round(StaffSpaces.toPixels(bbox.width()));
            int h = (int) Math.round(StaffSpaces.toPixels(bbox.height()));
            type.realUpNoteRect.setBounds(0, y, w, h);
            type.realDownNoteRect.setBounds(0, y, w, h);
        }
    }

    private static void computeNoteRects(
        SMuFLMetadata metadata, int hotSpotY, NoteType... types
    ) {
        double stemLength = StaffSpaces.toPixels(STEM_LENGTH_SS);

        for (var type : types) {
            var glyph = SMUFL_NOTEHEADS.get(type);

            if (glyph == null) {
                continue;
            }

            var bbox = metadata.getBBox(glyph);

            if (bbox == null) {
                continue;
            }

            double headTop = StaffSpaces.toPixels(bbox.top());
            double headBottom = StaffSpaces.toPixels(bbox.bottom());
            double headRight = StaffSpaces.toPixels(bbox.right());

            var anchors = metadata.getAnchors(glyph);

            if (anchors != null && anchors.stemUpSE() != null) {
                // Stemmed note
                double stemUpX = StaffSpaces.toPixels(anchors.stemUpSE().x());
                double stemUpY = StaffSpaces.toPixels(anchors.stemUpSE().y());
                double stemDownY = StaffSpaces.toPixels(anchors.stemDownNW().y());

                // Stem-up: stem extends upward from stemUpSE anchor
                double upTop = stemUpY - stemLength;
                double upRight = headRight;

                // For beamable notes, extend width to include flag
                var flagGlyph = getStemUpFlagGlyph(type);

                if (flagGlyph != null) {
                    var flagBBox = metadata.getBBox(flagGlyph);

                    if (flagBBox != null) {
                        upRight = Math.max(upRight,
                            stemUpX + StaffSpaces.toPixels(flagBBox.right()));
                    }
                }

                type.realUpNoteRect.setBounds(
                    0,
                    (int) Math.round(upTop + hotSpotY),
                    (int) Math.round(upRight),
                    (int) Math.round(headBottom - upTop)
                );

                // Stem-down: stem extends downward from stemDownNW anchor
                double downBottom = stemDownY + stemLength;

                type.realDownNoteRect.setBounds(
                    0,
                    (int) Math.round(headTop + hotSpotY),
                    (int) Math.round(headRight),
                    (int) Math.round(downBottom - headTop)
                );
            } else {
                // No stem (semibreve)
                int y = (int) Math.round(headTop + hotSpotY);
                int w = (int) Math.round(headRight);
                int h = (int) Math.round(headBottom - headTop);
                type.realUpNoteRect.setBounds(0, y, w, h);
                type.realDownNoteRect.setBounds(0, y, w, h);
            }
        }
    }

    /**
     * Grace notes use pre-composed acciaccatura glyphs whose BBoxes
     * are scaled by the grace note rendering scale factor.
     */
    private static void computeGraceNoteRects(
        SMuFLMetadata metadata, int hotSpotY, NoteType... types
    ) {
        var upBBox = metadata.getBBox(SMuFLGlyph.GRACE_NOTE_ACCIACCATURA_STEM_UP);
        var downBBox = metadata.getBBox(SMuFLGlyph.GRACE_NOTE_ACCIACCATURA_STEM_DOWN);

        if (upBBox == null || downBBox == null) {
            return;
        }

        double scale = GraceNoteRenderer.GRACE_NOTE_SCALE;

        int upY = (int) Math.round(StaffSpaces.toPixels(upBBox.top()) * scale + hotSpotY);
        int upW = (int) Math.round(StaffSpaces.toPixels(upBBox.width()) * scale);
        int upH = (int) Math.round(StaffSpaces.toPixels(upBBox.height()) * scale);

        int downY = (int) Math.round(StaffSpaces.toPixels(downBBox.top()) * scale + hotSpotY);
        int downW = (int) Math.round(StaffSpaces.toPixels(downBBox.width()) * scale);
        int downH = (int) Math.round(StaffSpaces.toPixels(downBBox.height()) * scale);

        for (var type : types) {
            type.realUpNoteRect.setBounds(0, upY, upW, upH);
            type.realDownNoteRect.setBounds(0, downY, downW, downH);
        }
    }

    @Nullable
    private static SMuFLGlyph getStemUpFlagGlyph(NoteType type) {
        return switch (type) {
            case QUAVER -> SMuFLGlyph.FLAG_8TH_UP;
            case SEMIQUAVER -> SMuFLGlyph.FLAG_16TH_UP;
            case DEMI_SEMIQUAVER -> SMuFLGlyph.FLAG_32ND_UP;
            default -> null;
        };
    }
}
