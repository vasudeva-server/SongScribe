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

import java.awt.Font;
import java.util.Collections;

import org.jspecify.annotations.Nullable;

import songscribe.font.LocalFonts;
import songscribe.font.TextMeasurement;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;

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
public final class Tuplet extends Span {

    /** Inward shortening at each bracket endpoint (LilyPond: -0.2ss). */
    public static final double ARM_EXTENSION_SS = 0.2;  // 1.6px

    /** Italic serif font for tuplet numbers (font size in staff-spaces). */
    public static final float TUPLET_FONT_SIZE_SS = 1.65f;

    /** Italic serif font for tuplet numbers. */
    public static final Font TUPLET_FONT = LocalFonts.load("C059-Italic.otf", TUPLET_FONT_SIZE_SS);

    /** Vertical arm height of bracket endpoints (LilyPond: 0.7ss). */
    public static final double BRACKET_ARM_HEIGHT_SS = 0.7;  // 5.6px



    /** Maximum bracket slope, as a fraction of rise over run (LilyPond: 0.5). */
    public static final double MAX_SLOPE_FACTOR = 0.5;

    /** Minimum number of non-rest notes a tuplet must span to have a meaningful bracket. */
    private static final int MIN_NON_REST_NOTES = 2;

    /**
     * Floor on the width {@link #getSpanWidthSs} reports, in staff spaces. A collision region of
     * zero width reserves nothing and lets the layer above sit on top of the bracket, so a span
     * whose two elements have not been separated yet still claims one staff space.
     */
    public static final double MIN_SPAN_WIDTH_SS = 1.0;

    /** Measured ink height of a tuplet number in staff-spaces (representative digit "3"). */
    public static final double TUPLET_NUMBER_INK_HEIGHT_SS = measureNumberInkHeightSs();

    private static double measureNumberInkHeightSs() {
        var inkHeightSs = TextMeasurement.inkHeight(
            TextMeasurement.requireVisualBounds("3", TUPLET_FONT));

        if (inkHeightSs <= 0) {
            throw new IllegalStateException(
                "Tuplet number font produced zero ink height; the font may have failed to load");
        }

        return inkHeightSs;
    }

    /**
     * The measured ink width of {@code grade} drawn as a tuplet number, in staff spaces.
     * <p>
     * Measured by {@link TextMeasurement} rather than in the paint-time font render
     * context, so it is available before anything is drawn — the number-only tuplet's hit region
     * is the number's ink and nothing else, and hit regions are built at layout time.
     */
    public static double numberInkWidthSs(int grade) {
        return TextMeasurement.requireVisualBounds(String.valueOf(grade), TUPLET_FONT).getWidth();
    }

    // The grade and the ratio are set once and never change afterwards. Changing the grade
    // alone would leave the tuplet printing one ratio and playing another, so there is no
    // setter: a grade change is a remove plus an add, which re-derives both.
    private final int grade;
    private @Nullable TupletRatio ratio;
    private int verticalPositionSs = 0;

    /**
     * Creates a fully specified tuplet grouping: {@code grade} notes played in the time
     * {@code ratio} states.
     * <p>
     * The ratio's written value is a base type plus its dots rather than a tick duration, so
     * it needs no decomposition on the way out to MusicXML.
     *
     * @param anchorElement The first note in the tuplet
     * @param endElement    The last note in the tuplet
     * @param grade         The tuplet number (3 for triplet, 5 for quintuplet, etc.)
     * @param ratio         The time the group occupies
     */
    public Tuplet(StaffElement anchorElement, StaffElement endElement, int grade, TupletRatio ratio) {
        super(anchorElement, endElement);
        this.grade = grade;
        this.ratio = ratio;
    }

    /** Shared by the canonical constructor's peers; leaves the ratio unresolved. */
    private Tuplet(StaffElement anchorElement, StaffElement endElement, int grade) {
        super(anchorElement, endElement);
        this.grade = grade;
        ratio = null;
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
    protected Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        // Undo/redo round-trips through here, so every field must be carried over —
        // including the unresolved state, which must copy as unresolved.
        Tuplet copy;

        if (ratio == null) {
            copy = withUnresolvedRatio(newAnchor, newEnd, grade);
        } else {
            copy = new Tuplet(newAnchor, newEnd, grade, ratio);
        }

        copy.verticalPositionSs = verticalPositionSs;
        return copy;
    }

    /**
     * Reserved vertical height for a bracketed tuplet (bracket line + arm). A number-only
     * tuplet (see {@link #isNumberOnly(Line)}) reserves {@link #numberOnlyHeightSs()}
     * instead, so a caller laying out a tuplet branches on {@code isNumberOnly} to choose
     * between the two; neither height is meaningful without that choice, which is why a
     * tuplet has no context-free height query.
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
     * reference X, whether the anchor draws an upward stem, and the stem thickness. The arm sits
     * on the anchor note's stem side (insetting a full notehead width for a down-stem, only the
     * stem width for an up-stem) and extends {@link #ARM_EXTENSION_SS} outward past it. An anchor
     * with no upward stem — a down-stem, a whole note, a rest — insets the full head width,
     * putting the arm outside the head's left edge.
     * <p>
     * Shared by the renderer ({@code TupletRenderer.renderTupletsFromLine}) and the stacker's sloped
     * clearance ({@code StructuralStacker.computeTupletClearanceLeftYSs}) so the reserved arm position
     * and the drawn arm position stay in lockstep — mirroring LilyPond's use of the bracket's visual
     * X-bounds {@code x0}/{@code x1} for both slope and placement (tuplet-bracket.cc).
     *
     * @param anchorXSs      X of the anchor column's reference point
     * @param anchorStemUp   whether the anchor draws an upward stem ({@link #anchorStemUp()})
     * @param stemSs         stem thickness in staff spaces
     * @param noteheadWidthSs the anchor note's own head width in staff spaces
     */
    public static double bracketLeftEdgeXSs(double anchorXSs, boolean anchorStemUp,
        double stemSs, double noteheadWidthSs) {
        var insetSs = anchorStemUp ? stemSs : noteheadWidthSs;

        return anchorXSs + noteheadWidthSs - insetSs - ARM_EXTENSION_SS;
    }

    /**
     * Whether this tuplet's anchor draws an upward stem: a stemmed note whose stem points up.
     * An anchor with no stem to draw — a whole note, a rest — never does, whatever direction it
     * would answer. The one statement of the test every bracket edge and the number-only rule
     * read, so the drawn bracket, its reserved room and its hit region agree on which side of
     * the anchor the arm sits.
     *
     * @return {@code true} when the anchor is a stemmed note whose stem points up
     */
    public boolean anchorStemUp() {
        return getAnchorElement() instanceof NoteElement note
            && note.getType().isNoteWithStem()
            && note.direction().isUp();
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
        var spans = line.spanRegistry();

        return spans.findBeamAt(requireAnchorElementIndex()) != null
            && spans.findBeamAt(requireEndElementIndex()) != null
            && anchorStemUp();
    }

    /**
     * Returns whether this tuplet's anchor and end elements span at
     * least {@value #MIN_NON_REST_NOTES} non-rest notes. A tuplet bracket has no
     * meaningful slope to compute across fewer notes, so a load-time validator should
     * reject any tuplet for which this returns false.
     */
    public boolean hasValidSpan(Line line) {
        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (anchorIndex == null || endIndex == null) {
            return false;
        }

        var nonRestCount = 0;

        for (int index = anchorIndex; index <= endIndex; index++) {
            if (!line.getElement(index).getType().isRest()) {
                nonRestCount++;

                if (nonRestCount >= MIN_NON_REST_NOTES) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Decides what happens to this tuplet when {@code change} lands: the group is removed by
     * any structural change strictly inside its bracket, and kept by everything else.
     * <p>
     * A tuplet is a rhythmic claim about exactly the elements between its endpoints, so its
     * survival depends on its interior, not only on its endpoints the way a tie's or a beam's
     * does. An insertion after the anchor and no later than the end element adds time the
     * group did not account for; a deletion overlapping the bracket takes time away; and a
     * replacement or an in-place modification that rewrites a written duration inside it
     * — the distinction {@link ElementField#DURATION_AFFECTING} draws — leaves the group
     * rhythmically invalid (refs #726). An insertion at the anchor's own index, or just past
     * the end element, stays outside and leaves the group alone.
     * <p>
     * Only a replacement touching a duration-carrying element counts. A tuplet may contain a
     * barline or a breath mark, which contribute no time to the group, so swapping one of
     * those for another non-duration element cannot unbalance it. Past that gate, any change
     * of element type or dot count drops the group. That is deliberately broader than "the
     * written duration changed": a note swapped for a rest of the same value keeps the group's
     * total time but can starve it of the non-rest notes {@link #hasValidSpan} demands, so it
     * goes too. The user re-brackets what they meant.
     * <p>
     * A tuplet whose endpoints are not both in {@code line} has nothing there to judge and is
     * kept.
     *
     * @param change the pending change, not yet applied to {@code line}
     * @param line   the line being changed, still in its pre-change state
     */
    @Override
    public SpanOutcome.Simple outcomeFor(ElementChange change, Line line) {
        var range = line.spanRegistry().rangeOf(this);

        if (range == null) {
            return SpanOutcome.Simple.KEEP;
        }

        var invalidated = switch (change) {
            case ElementChange.Insertion insertion ->
                range.begin() < insertion.index() && insertion.index() <= range.end();

            case ElementChange.Deletion deletion -> range.overlaps(deletion.range());

            case ElementChange.Replacement replacement ->
                range.contains(replacement.index())
                    && replacementUnbalances(replacement.oldElement(), replacement.newElement());

            case ElementChange.Modification modification ->
                range.contains(modification.index())
                    && !Collections.disjoint(modification.fields(), ElementField.DURATION_AFFECTING);
        };

        return invalidated ? SpanOutcome.Simple.REMOVE : SpanOutcome.Simple.KEEP;
    }

    /**
     * Whether swapping {@code oldElement} for {@code newElement} inside this tuplet rewrites a
     * written duration the group was built on — see {@link #outcomeFor}.
     */
    private static boolean replacementUnbalances(StaffElement oldElement, StaffElement newElement) {
        var oldType = oldElement.getType();
        var newType = newElement.getType();

        if (!oldType.isDuration() && !newType.isDuration()) {
            return false;
        }

        return oldType != newType || Dottable.dotsOf(oldElement) != Dottable.dotsOf(newElement);
    }

    /**
     * Returns the horizontal span the tuplet bracket reserves, in staff spaces: the distance
     * between its two elements, floored so that a bracket over elements sharing a column still
     * reserves a region with width to it.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return the span width in staff spaces, never below {@link #MIN_SPAN_WIDTH_SS} and so
     *     always positive
     */
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(MIN_SPAN_WIDTH_SS, endXSs - anchorXSs);
    }

    /**
     * Returns the tuplet grade (3 for triplet, 5 for quintuplet, etc.).
     */
    public int getGrade() {
        return grade;
    }

    /**
     * Returns the time this group occupies, or null while the ratio is unresolved.
     */
    public @Nullable TupletRatio getRatio() {
        return ratio;
    }

    /**
     * Returns whether the ratio is known. Only a tuplet built by
     * {@link #withUnresolvedRatio} can be unresolved, and only until the
     * dom-package load pass resolves or drops it.
     */
    public boolean isResolved() {
        return ratio != null;
    }

    /**
     * Completes the ratio of a tuplet built by {@link #withUnresolvedRatio}.
     * <p>
     * Package-private on purpose: the dom-package load pass is the only caller, so
     * that no outside code can retroactively re-time a tuplet the user already sees.
     */
    void resolveRatio(TupletRatio ratio) {
        this.ratio = ratio;
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

    /**
     * Replaces any existing tuplet that overlaps this one: every tuplet whose range overlaps
     * [anchor, end] is removed before this tuplet is added, each removal its own tracked
     * mutation so undo restores the originals.
     */
    @Override
    protected void applyDisplacementPolicy(Line line) {
        var spans = line.spanRegistry();
        spans.removeOverlapping(Tuplet.class, spans.requireRangeOf(this));
    }

    @Override
    protected Mutation additionRecordFor(Line line) {
        return new TupletAddition(line, this);
    }

    @Override
    protected Mutation removalRecordFor(Line line) {
        return new TupletRemoval(line, this);
    }
}
