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
