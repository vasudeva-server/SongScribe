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

/**
 * Represents a tie connecting two elements of the same pitch.
 * <p>
 * Ties connect exactly two elements and are rendered as a curved arc.
 * The placement (above or below) depends on the stem direction of the elements.
 */
public class Tie extends Span {

    /**
     * Arc height of a tie curve.
     */
    public static final double TIE_ARC_HEIGHT_SS = 1.0;  // 8px

    /**
     * Creates a new tie between two elements.
     *
     * @param anchorElement The first (starting) element of the tie
     * @param endElement    The second (ending) element of the tie
     */
    public Tie(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    @Override
    protected Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Tie(newAnchor, newEnd);
    }

    @Override
    public double getContentHeightSs() {
        return TIE_ARC_HEIGHT_SS;
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }

    /**
     * Returns whether {@code type} may sit between two tied notes.
     *
     * <p>Non-duration elements take no time, so the notes on either side stay adjacent in
     * the music even though an element separates them on the staff. A final double barline
     * is the exception: it ends the piece, so nothing may sound across it (refs #527).
     *
     * <p>One definition, two readers: {@code RangeQueries.canToggleTie} asks it before letting
     * the user create a tie over a separator, and {@link #isInvalidatedByInsertion} asks it
     * before letting an insertion land inside an existing one. Were they to disagree on the
     * types, an insertion could put an element between two tied notes that the user would
     * have been forbidden to tie across.
     *
     * <p>The two readers share the <em>type</em> rule only, not a count. Drawing a tie is
     * limited to two notes with at most one element between them, because that is all a
     * selection can hold; an insertion has no such limit and deliberately lets separators
     * accumulate, since each one still takes no time and the notes stay adjacent in the music.
     */
    public static boolean isLegalSeparator(ElementType type) {
        return type.isNonDuration() && type != ElementType.FINAL_DOUBLE_BARLINE;
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * lands something between the two tied notes that may not sit there. A note or a rest
     * breaks the tie — the notes no longer sound as one — as does anything else that is not
     * a legal separator, such as a grace note. A barline or repeat takes no musical time, so
     * a tie across one is ordinary notation and the insertion leaves it alone (refs #726).
     * <p>
     * Must be called on the <em>pre-insertion</em> line state.
     *
     * @param insertedIndex the index at which the element will be inserted (pre-insertion)
     * @param insertedType  the type of the element being inserted
     * @param line          the owning line (pre-insertion state)
     */
    @Override
    public boolean isInvalidatedByInsertion(int insertedIndex, ElementType insertedType, Line line) {
        if (isLegalSeparator(insertedType)) {
            return false;
        }

        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (anchorIndex < 0 || endIndex < 0) {
            return false;
        }

        // The end index is inside the invalidating range where the anchor's is not: inserting
        // at endIndex displaces the end note rightwards, so the new element lands between the
        // two, while inserting at anchorIndex displaces the anchor and lands outside.
        return insertedIndex > anchorIndex && insertedIndex <= endIndex;
    }

    /**
     * Returns true if replacing {@code oldElement} with {@code newElement} leaves this tie in a
     * state the user could not have created: an endpoint that is no longer a note sounding what
     * it sounded before, or a separator that may no longer sit between the two notes. A
     * replacement outside the tie leaves it alone (refs #726).
     * <p>
     * Must be called on the <em>pre-replacement</em> line state, while {@code oldElement} is
     * still in the line.
     *
     * @param oldElement the element being replaced (still in the line at call time)
     * @param newElement the element that will replace it
     * @param line       the owning line (pre-replacement state)
     */
    @Override
    public boolean isInvalidatedByReplacement(
        StaffElement oldElement, StaffElement newElement, Line line
    ) {
        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();
        var replacedIndex = line.getElementIndex(oldElement);

        if (anchorIndex < 0 || endIndex < 0
                || replacedIndex < anchorIndex || replacedIndex > endIndex) {
            return false;
        }

        // Strictly inside: the replacement takes over the separator slot between the two notes,
        // so it must be something a tie may straddle.
        if (replacedIndex > anchorIndex && replacedIndex < endIndex) {
            return !isLegalSeparator(newElement.getType());
        }

        // An endpoint. The tie was legal before the edit, so it stays legal exactly when the
        // incoming note sounds what the outgoing one did: same staff position, same explicit
        // accidental. Only the written duration is then free to change.
        //
        // Deliberately not a getPitch() comparison against the note at the other end. Pitch
        // resolves an omitted accidental from the key signature and from what a tie carries
        // across a barline, neither of which newElement can answer for while it is still
        // outside the line — and neither of which a replacement that leaves the staff position
        // and the accidental alone can have changed.
        return !newElement.getType().isPitchedNote()
            || newElement.getStaffPosition() != oldElement.getStaffPosition()
            || newElement.getAccidental() != oldElement.getAccidental();
    }

    /**
     * Returns whether this tie arcs above its notes rather than below them.
     * <p>
     * Declared here rather than on {@link Span} because a tie is the only span whose side is
     * a computed property — every other span's side is fixed by its own geometry.
     */
    public boolean isAbove() {
        // isAbove and arcSign share one convention: arcSign < 0 == arc bulges up == tie above.
        return arcSign() < 0;
    }

    /**
     * The tie arc's render/seed sign in Y-down screen space: {@code +1} = arc bulges
     * <em>downward</em> (tie below the notes), {@code -1} = arc bulges upward (tie above).
     *
     * <p>Single source of truth for tie direction, consumed by three live sites:
     * the renderer ({@code LayoutEngine.calculateTies}), the skyline seeder's reserve
     * side ({@code NoteAttachedStacker.seedTieBounds}), and the MusicXML export
     * ({@link #isAbove()} → {@code <tied orientation>}). All three must agree, so a
     * conflicting-stem tie renders, seeds, and exports on the same side.
     *
     * <p>The <em>visual</em> arc direction ({@code tieDir}, a {@link StaffElement.Direction}
     * where UP = arc bulges up = tie above) follows LilyPond's {@code get_default_dir}
     * fallthrough tree, keying off <em>both</em> noteheads' stems:
     *
     * <pre>
     *                  tieDirection(left, right)
     *                           │
     *         ┌─────────────────┴──────────────────┐
     *    both have stems?                      not both
     *         │ yes                                 │
     *    both UP? ── yes ─→ DOWN                     │
     *         │ no                                   │
     *         └───────────────┐          ┌───────────┴───────────┐
     *                         │     only left stem?        only right stem?
     *                         │        │ yes                    │ yes
     *                         │   opposite(left)          opposite(right)
     *                         │        │                        │
     *                    (fall through)                    (neither stem)
     *                         │                                  │
     *                         │                    staff pos vs middle line:
     *                         │                     above → UP · below → DOWN
     *                         │                     on middle → (fall through)
     *                         └──────────────┬───────────────────┘
     *                                   NEUTRAL → UP
     * </pre>
     *
     * <p>The single inversion from musical "above" to Y-down "arc sign" lives in the
     * {@code .opposite()} call below — and nowhere else. {@code tieDir = UP} (tie above)
     * → {@code opposite()} = DOWN → {@code sign()} = {@code -1} (above); {@code tieDir = DOWN}
     * (tie below) → {@code +1}. Do not re-derive {@code ±1} per branch.
     *
     * @return {@code +1} when the arc bulges downward (tie below), {@code -1} when it
     * bulges upward (tie above)
     */
    public int arcSign() {
        return tieArcDirection().opposite().sign();
    }

    /**
     * Computes the tie's <em>visual</em> arc direction (UP = arc bulges up = tie above)
     * via the fallthrough tree documented on {@link #arcSign()}. Reads both notes'
     * {@link ElementType#isNoteWithStem()}, {@link StaffElement#getDirection()}, and
     * {@link StaffElement#getStaffPosition()}.
     */
    private StaffElement.Direction tieArcDirection() {
        var left = getAnchorElement();
        var right = getEndElement();

        // Either endpoint null → neutral default (mirrors the former isAbove() null guard).
        if (left == null || right == null) {
            return StaffElement.Direction.UP;
        }

        var leftHasStem = left.getType().isNoteWithStem();
        var rightHasStem = right.getType().isNoteWithStem();

        if (leftHasStem && rightHasStem) {
            // Both stems up → tie below; any other pairing (both down, or conflicting) falls
            // through to the neutral default.
            if (left.getDirection().isUp() && right.getDirection().isUp()) {
                return StaffElement.Direction.DOWN;
            }

            return StaffElement.Direction.UP;
        }

        if (leftHasStem) {
            return left.getDirection().opposite();
        }

        if (rightHasStem) {
            return right.getDirection().opposite();
        }

        // Neither note has a stem → key off staff position. getStaffPosition() is Y-down
        // (0 = middle line, positive = below), so above the middle line is negative.
        // Same-pitch tie ⇒ both notes share a staff position; read the left.
        var staffPosition = left.getStaffPosition();

        if (staffPosition < 0) {
            return StaffElement.Direction.UP;
        }

        if (staffPosition > 0) {
            return StaffElement.Direction.DOWN;
        }

        // Exactly on the middle line → neutral default.
        return StaffElement.Direction.UP;
    }
}
