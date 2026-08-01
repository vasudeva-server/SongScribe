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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Represents a tuplet grouping (triplet, quintuplet, etc.).
 * <p>
 * Tuplets group notes that are played in the time normally occupied
 * by a different number of notes. The most common is a triplet (3 notes
 * in the time of 2).
 * <p>
 * The tuplet bracket is typically placed above if stems point down,
 * below if stems point up.
 */
public class Tuplet extends RangeElement {

    /** Inward shortening at each bracket endpoint (LilyPond: -0.2ss). */
    public static final double ARM_EXTENSION_SS = 0.2;  // 1.6px

    /** Italic serif font for tuplet numbers (font size in staff-spaces). */
    public static final float TUPLET_FONT_SIZE_SS = 1.65f;

    /** Italic serif font for tuplet numbers. */
    public static final Font TUPLET_FONT = MyFontUtils.getLocalFont("C059-Italic.otf", TUPLET_FONT_SIZE_SS);

    /** Vertical arm height of bracket endpoints (LilyPond: 0.7ss). */
    public static final double BRACKET_ARM_HEIGHT_SS = 0.7;  // 5.6px



    /** Maximum bracket slope, as a fraction of rise over run (LilyPond: 0.5). */
    public static final double MAX_SLOPE_FACTOR = 0.5;

    /** Minimum number of non-rest notes a tuplet must span to have a meaningful bracket. */
    private static final int MIN_NON_REST_NOTES = 2;

    /** Measured ink height of a tuplet number in staff-spaces (representative digit "3"). */
    public static final double TUPLET_NUMBER_INK_HEIGHT_SS = measureNumberInkHeightSs();

    private static double measureNumberInkHeightSs() {
        var inkHeightSs = GraphicUtils.inkHeight(
            TUPLET_FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, "3").getVisualBounds());

        if (inkHeightSs <= 0) {
            throw new IllegalStateException(
                "Tuplet number font produced zero ink height; the font may have failed to load");
        }

        return inkHeightSs;
    }

    /**
     * The {@code normalNotes} of a tuplet whose ratio has not been resolved yet.
     * A resolved tuplet always has a positive {@code normalNotes}.
     */
    public static final int UNRESOLVED_NORMAL_NOTES = 0;

    // N, M and V are set once, together, and never change afterwards. Changing N alone
    // would leave the tuplet printing one ratio and playing another, so there is no
    // setter: a grade change is a remove plus an add, which re-derives all three.
    private final int grade;
    private int normalNotes;
    private @Nullable ElementType noteValue;
    private int noteValueDots;
    private int verticalPositionSs = 0;

    /**
     * Creates a fully specified tuplet grouping: {@code grade} notes played in the
     * time normally occupied by {@code normalNotes} notes of the written value
     * {@code noteValue} carrying {@code noteValueDots} dots.
     * <p>
     * The written value is stored as a base type plus a dot count rather than as a
     * tick duration, so that it is notatable by construction and needs no
     * decomposition on the way out to MusicXML.
     *
     * @param anchorElement The first note in the tuplet
     * @param endElement    The last note in the tuplet
     * @param grade         The tuplet number (3 for triplet, 5 for quintuplet, etc.)
     * @param normalNotes   The count of {@code noteValue} notes whose time the group occupies
     * @param noteValue     The tuplet's written note value, as a base element type
     * @param noteValueDots The dot count of the written note value
     */
    public Tuplet(StaffElement anchorElement, StaffElement endElement,
        int grade, int normalNotes, ElementType noteValue, int noteValueDots) {
        super(anchorElement, endElement);
        this.grade = grade;
        this.normalNotes = normalNotes;
        this.noteValue = noteValue;
        this.noteValueDots = noteValueDots;
    }

    /** Shared by the canonical constructor's peers; leaves the ratio unresolved. */
    private Tuplet(StaffElement anchorElement, StaffElement endElement, int grade) {
        super(anchorElement, endElement);
        this.grade = grade;
        normalNotes = UNRESOLVED_NORMAL_NOTES;
        noteValue = null;
        noteValueDots = 0;
    }

    /**
     * Creates a tuplet read from a file that did not state its ratio. M and V are
     * pending until the load pass either resolves or drops it — see
     * {@code TupletLoadPass}. This is the only way to obtain a half-built tuplet,
     * and only same-package code can complete it via {@link #resolveRatio}.
     */
    public static Tuplet withUnresolvedRatio(
        StaffElement anchorElement, StaffElement endElement, int grade) {
        return new Tuplet(anchorElement, endElement, grade);
    }

    @Override
    protected RangeElement createCopy(StaffElement newAnchor, StaffElement newEnd) {
        // Undo/redo round-trips through here, so every field must be carried over —
        // including the unresolved state, which must copy as unresolved.
        Tuplet copy;

        if (noteValue == null) {
            copy = withUnresolvedRatio(newAnchor, newEnd, grade);
        } else {
            copy = new Tuplet(newAnchor, newEnd, grade, normalNotes, noteValue, noteValueDots);
        }

        copy.verticalPositionSs = verticalPositionSs;
        return copy;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the <em>bracketed</em> reserved height. A number-only tuplet
     * (see {@link #isNumberOnly(Line)}) reserves {@link #numberOnlyHeightSs()}
     * instead. Because that distinction requires a {@link Line}, callers laying
     * out tuplets must branch on {@code isNumberOnly} rather than rely on this
     * context-free value.
     */
    @Override
    public double getContentHeightSs() {
        return bracketedHeightSs();
    }

    /**
     * Reserved vertical height for a bracketed tuplet (bracket line + arm).
     */
    public static double bracketedHeightSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0 + BRACKET_ARM_HEIGHT_SS;
    }

    /**
     * Reserved vertical height for a number-only tuplet (beamed, stems up).
     */
    @SuppressWarnings("SameReturnValue")
    public static double numberOnlyHeightSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS;
    }

    /**
     * Offset from the box top to the bracket line center in staff-spaces.
     */
    public static double bracketLineOffsetSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0;
    }

    /**
     * X of the visual bracket's left edge — where the left arm is drawn — given the anchor column's
     * reference X, the anchor note's stem direction, and the stem thickness. The arm sits on the
     * anchor note's stem side (insetting a full notehead width for a down-stem, only the stem width
     * for an up-stem) and extends {@link #ARM_EXTENSION_SS} outward past it. A stemless anchor — a
     * whole note — has no stem for the arm to sit on, so it insets the full head width whatever
     * direction the element happens to store, putting the arm outside the head's left edge.
     * <p>
     * Shared by the renderer ({@code TupletRenderer.renderTupletsFromLine}) and the stacker's sloped
     * clearance ({@code StructuralStacker.computeTupletClearanceLeftYSs}) so the reserved arm position
     * and the drawn arm position stay in lockstep — mirroring LilyPond's use of the bracket's visual
     * X-bounds {@code x0}/{@code x1} for both slope and placement (tuplet-bracket.cc).
     *
     * @param anchorXSs      X of the anchor column's reference point
     * @param anchorHasStem  whether the anchor note draws a stem at all
     * @param anchorStemUp   whether the anchor note's stem points up; meaningless, and ignored,
     *                       when {@code anchorHasStem} is false
     * @param stemSs         stem thickness in staff spaces
     * @param noteheadWidthSs the anchor note's own head width in staff spaces
     */
    public static double bracketLeftEdgeXSs(double anchorXSs, boolean anchorHasStem,
        boolean anchorStemUp, double stemSs, double noteheadWidthSs) {
        var insetSs = anchorHasStem && anchorStemUp ? stemSs : noteheadWidthSs;

        return anchorXSs + noteheadWidthSs - insetSs - ARM_EXTENSION_SS;
    }

    /**
     * X of the visual bracket's right edge — where the right arm is drawn — given the end column's
     * reference X. The arm clears the end notehead and extends {@link #ARM_EXTENSION_SS} outward past
     * it. See {@link #bracketLeftEdgeXSs} for why this is shared with the renderer.
     *
     * @param endXSs         X of the end column's reference point
     * @param noteheadWidthSs the end note's own head width in staff spaces
     */
    public static double bracketRightEdgeXSs(double endXSs, double noteheadWidthSs) {
        return endXSs + noteheadWidthSs + ARM_EXTENSION_SS;
    }

    /**
     * Returns true when only the number is drawn (no bracket): the tuplet is beamed
     * at both its anchor and end index and its anchor note has upward stems.
     */
    public boolean isNumberOnly(Line line) {
        var anchor = getAnchorElement();

        return anchor != null
            && line.findBeamAt(getAnchorElementIndex()) != null
            && line.findBeamAt(getEndElementIndex()) != null
            && anchor.getDirection().isUp();
    }

    /**
     * Returns whether this tuplet's anchor and end elements are both set and span at
     * least {@value #MIN_NON_REST_NOTES} non-rest notes. A tuplet bracket has no
     * meaningful slope to compute across fewer notes, so a load-time validator should
     * reject any tuplet for which this returns false.
     */
    public boolean hasValidSpan(Line line) {
        var anchor = getAnchorElement();
        var end = getEndElement();

        if (anchor == null || end == null) {
            return false;
        }

        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (anchorIndex < 0 || endIndex < 0) {
            return false;
        }

        var nonRestCount = 0;

        for (var index = anchorIndex; index <= endIndex; index++) {
            if (!line.getElement(index).getType().isRest()) {
                nonRestCount++;

                if (nonRestCount >= MIN_NON_REST_NOTES) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }

    /**
     * Returns the tuplet grade (3 for triplet, 5 for quintuplet, etc.).
     */
    public int getGrade() {
        return grade;
    }

    /**
     * Returns M — the count of {@link #getNoteValue()} notes whose time this group
     * occupies, or {@link #UNRESOLVED_NORMAL_NOTES} while the ratio is unresolved.
     */
    public int getNormalNotes() {
        return normalNotes;
    }

    /**
     * Returns V's base element type, or null while the ratio is unresolved.
     */
    public @Nullable ElementType getNoteValue() {
        return noteValue;
    }

    /**
     * Returns the dot count of V, this tuplet's written note value.
     */
    public int getNoteValueDots() {
        return noteValueDots;
    }

    /**
     * Returns whether M and V are known. Only a tuplet built by
     * {@link #withUnresolvedRatio} can be unresolved, and only until the
     * dom-package load pass resolves or drops it.
     */
    public boolean isResolved() {
        return noteValue != null && normalNotes > 0;
    }

    /**
     * Completes the ratio of a tuplet built by {@link #withUnresolvedRatio}.
     * <p>
     * Package-private on purpose: the dom-package load pass is the only caller, so
     * that no outside code can retroactively re-time a tuplet the user already sees.
     */
    void resolveRatio(int normalNotes, ElementType noteValue, int noteValueDots) {
        this.normalNotes = normalNotes;
        this.noteValue = noteValue;
        this.noteValueDots = noteValueDots;
    }

    /**
     * Returns the user-adjustable Y offset for this tuplet bracket.
     */
    public int getVerticalPositionSs() {
        return verticalPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this tuplet bracket.
     */
    public void setVerticalPositionSs(int verticalPosition) {
        verticalPositionSs = verticalPosition;
    }

    @Override
    public String toIndexString() {
        var base = getAnchorElementIndex() + "," + getEndElementIndex() + "," + grade;

        if (verticalPositionSs != 0) {
            return base + "," + verticalPositionSs + ";";
        }

        return base + ";";
    }
}
