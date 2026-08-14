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
     * the music even though an element separates them on the staff. Two are excluded:
     *
     * <ul>
     *   <li>a final double barline ends the piece, so nothing may sound across it
     *       (refs #527);</li>
     *   <li>a key signature can alter the pitch class the two notes sit at, so the note
     *       after it may no longer sound what the note before it did — and a tie joins two
     *       notes of the same pitch. The rule excludes every key change rather than only
     *       the ones that happen to touch that pitch class, so that whether a tie survives
     *       does not depend on where its notes sit.</li>
     * </ul>
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
        return type.isNonDuration()
            && type != ElementType.FINAL_DOUBLE_BARLINE
            && type != ElementType.KEY_SIGNATURE;
    }

    /**
     * This tie's two endpoints as one line resolved them, so that "between the two notes"
     * is asked of the line performing the edit rather than of each endpoint's own line.
     *
     * @param anchorBound where the anchor sits relative to the asking line
     * @param endBound    where the end sits relative to the asking line
     */
    private record Endpoints(SpanBound anchorBound, SpanBound endBound) {

        /** Whether the asking line's half of this tie passes over {@code index}. */
        private boolean covers(int index) {
            return Span.containing(index).test(anchorBound, endBound);
        }

        /**
         * Whether {@code index} lies strictly past the anchor. An anchor off the asking
         * line's left edge is before every index there, so in the line holding the tie's
         * end everything up to that end lies past the anchor.
         */
        private boolean isAfterAnchor(int index) {
            return !anchorBound.atOrAfter(index);
        }

        /**
         * Whether {@code index} lies strictly short of the end — the mirror of
         * {@link #isAfterAnchor}, so in the line holding the tie's anchor everything past
         * that anchor lies short of an end off the right edge.
         */
        private boolean isBeforeEnd(int index) {
            return !endBound.atOrBefore(index);
        }
    }

    /**
     * This tie's endpoints as {@code line} resolves them, or {@code null} when {@code line}
     * can say nothing about where one of them sits.
     * <p>
     * Both rules below resolve through here, so "unresolvable" is defined once and the
     * insertion and replacement checks cannot disagree about a tie whose endpoint has been
     * detached or whose far line the song no longer holds. An {@link SpanBound#ABSENT} bound
     * has no direction, so neither rule may read it as bounding anything: the tie is on its
     * way out, and the edit is not what removes it.
     * <p>
     * Resolving through the asking line is what makes a cross-line tie work. Its two
     * endpoints live in different lines, so {@link #getAnchorElementIndex()} and
     * {@link #getEndElementIndex()} would answer with two positions measured in two different
     * lines — for a tie anchored at index 7 of one line and ending at index 0 of the next,
     * every "between them" test would be {@code index > 7 && index <= 0} and never true.
     */
    private @Nullable Endpoints resolveIn(Line line) {
        var anchorBound = line.anchorIndexOf(this);
        var endBound = line.endIndexOf(this);

        if (anchorBound.isAbsent() || endBound.isAbsent()) {
            return null;
        }

        return new Endpoints(anchorBound, endBound);
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * of {@code line} lands something between the two tied notes that may not sit there. A
     * note or a rest breaks the tie — the notes no longer sound as one — as does anything
     * else that is not a legal separator, such as a grace note. A barline or repeat takes no
     * musical time, so a tie across one is ordinary notation and the insertion leaves it
     * alone (refs #726).
     * <p>
     * For a tie straddling a line boundary the region between the two notes is whatever
     * follows the anchor in the anchor's line plus whatever precedes the end in the end's
     * line. That falls out of the bounds rather than being a separate rule: the asking line
     * resolves its own endpoint to a position and the far one to an edge it runs off, and an
     * edge is unbounded in its direction. Nothing here tests for index 0, so an end note
     * preceded by a grace note is handled by where the end actually resolves.
     * <p>
     * Must be called on the <em>pre-insertion</em> line state.
     *
     * @param insertedIndex the index at which the element will be inserted (pre-insertion)
     * @param insertedType  the type of the element being inserted
     * @param line          the line receiving the insertion (pre-insertion state)
     */
    @Override
    protected boolean isInvalidatedByInsertion(int insertedIndex, ElementType insertedType, Line line) {
        if (isLegalSeparator(insertedType)) {
            return false;
        }

        var endpoints = resolveIn(line);

        if (endpoints == null) {
            return false;
        }

        // The end's index is inside the invalidating range where the anchor's is not:
        // inserting at the end's index displaces the end note rightwards, so the new element
        // lands between the two, while inserting at the anchor's displaces the anchor and
        // lands outside.
        return endpoints.covers(insertedIndex) && endpoints.isAfterAnchor(insertedIndex);
    }

    /**
     * Returns true if replacing {@code oldElement} with {@code newElement} leaves this tie in a
     * state the user could not have created: an endpoint that is no longer a note sounding what
     * it sounded before, or a separator that may no longer sit between the two notes. A
     * replacement outside the tie leaves it alone (refs #726).
     * <p>
     * The replaced element's position and both endpoints are resolved in {@code line}, so a
     * tie straddling a line boundary compares three positions measured the same way. See
     * {@link #isInvalidatedByInsertion} for what the two lines' halves then cover.
     * <p>
     * Must be called on the <em>pre-replacement</em> line state, while {@code oldElement} is
     * still in the line.
     *
     * @param oldElement the element being replaced (still in the line at call time)
     * @param newElement the element that will replace it
     * @param line       the line receiving the replacement (pre-replacement state)
     */
    @Override
    protected boolean isInvalidatedByReplacement(
        StaffElement oldElement, StaffElement newElement, Line line
    ) {
        var endpoints = resolveIn(line);

        if (endpoints == null) {
            return false;
        }

        var replacedIndex = line.getElementIndex(oldElement);

        // Only an element of this line has a position the bounds can be compared against. The
        // guard is not redundant with the coverage test: a bound off an edge covers every
        // index in its direction, so it would swallow the -1 of an element that is elsewhere.
        if (replacedIndex < 0 || !endpoints.covers(replacedIndex)) {
            return false;
        }

        // Strictly inside: the replacement takes over the separator slot between the two notes,
        // so it must be something a tie may straddle.
        if (endpoints.isAfterAnchor(replacedIndex) && endpoints.isBeforeEnd(replacedIndex)) {
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
     * fallthrough tree, keying off <em>both</em> noteheads' stems. Each rule below is tried in
     * order; a rule that does not apply falls through to the next.
     *
     * <ol>
     *   <li>Both noteheads have stems, and both point UP: DOWN.
     *   <li>Both have stems but disagree: fall through to the final default.
     *   <li>Only the left notehead has a stem: the opposite of that stem's direction.
     *   <li>Only the right notehead has a stem: the opposite of that stem's direction.
     *   <li>Neither has a stem: decide by staff position relative to the middle line — above the
     *       middle gives UP, below gives DOWN, and exactly on the middle line falls through.
     *   <li>Default (NEUTRAL): UP.
     * </ol>
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
