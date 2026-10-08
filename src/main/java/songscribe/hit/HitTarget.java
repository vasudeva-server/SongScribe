/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.hit;

import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;

/**
 * The complete vocabulary of things a click on a staff line can address.
 *
 * <p>Addressing something and selecting it are different questions, and {@link Selectable}
 * is which of the two a kind answers. A kind outside it can be resolved to and acted on —
 * a double-click opens an editor, say — but can never become the selection, so the
 * selection and delete paths never see one.
 *
 * <p>Every {@code Selectable} names what it selects <b>by object reference</b>, never by
 * position in the line. An index-addressed target goes silently wrong on mutation: select
 * element 5, delete element 2, and index 5 now names a different but perfectly live
 * element — nothing dangles, the selection simply points at the wrong note. Identity
 * addressing removes that failure mode rather than trying to detect it. Where an index
 * is genuinely needed it is derived at the point of use via
 * {@code Line.getElementIndex(StaffElement)}.
 *
 * <p>There is no {@code Nothing} variant: a query that hits nothing returns
 * {@code null} (see {@link HitRegistry#hitTest}).
 */
public sealed interface HitTarget {

    /**
     * @return how a region addressing this kind resolves against the regions it overlaps
     */
    HitPriority priority();

    /**
     * Whether a region addressing this kind takes part in the mouse-move query,
     * {@link HitRegistry#hitTestHover}.
     * <p>
     * Lyrics are the only kind that does. The mouse-move path suppresses the preview element
     * over lyric text and fires on every pixel of pointer motion, so it must be able to ask
     * about lyrics alone rather than resolving the whole registry.
     *
     * @return {@code true} for the kinds the hover query scans
     */
    default boolean hoverTestable() {
        return false;
    }

    /**
     * The kinds a press can make the selection.
     *
     * <p>A kind outside this interface is addressable but never selected, which is what makes
     * the selection layer's liveness check total: it holds only a {@code Selectable}, so
     * {@link #isLiveOn} is answerable for whatever it holds.
     */
    sealed interface Selectable extends HitTarget {

        /**
         * Whether this target still names something live on {@code line}: whether a selection
         * of it, made on {@code line}, still addresses notation that exists there. The selection
         * layer asks this after an edit, an undo or a redo, and clears a selection that is no
         * longer live.
         * <p>
         * Each kind answers from what records its presence. A staff element, an attachment and
         * an articulation answer from the line they are on. A span answers from the line's span
         * registry, because removing a span leaves its endpoints where they were. A tie answers
         * from both of its notes. The staff line itself is always live.
         *
         * @param line the line the selection is on
         * @return {@code true} when the target is live on {@code line}
         */
        boolean isLiveOn(Line line);
    }

    /**
     * Whether {@code line}'s span registry holds {@code span}: the liveness rule every span
     * target shares. The registry is the only record of a span having been removed, since a
     * removal leaves the span's endpoints where they were.
     */
    private static boolean isRegisteredOn(Span<?> span, Line line) {
        return line.spanRegistry().getSpans().contains(span);
    }

    /**
     * A note head. Addressable but not {@link Selectable}: a note is selected as an index
     * range rather than as a target, so a press on one produces a {@code Selection.Range}.
     */
    record Element(StaffElement element) implements HitTarget {

        @Override
        public HitPriority priority() {
            return HitPriority.ELEMENT;
        }
    }

    /**
     * One syllable box of one verse under an element.
     *
     * @param element the element the syllable is attached to
     * @param verse   zero-based verse index
     */
    record Lyric(StaffElement element, int verse) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.LYRIC;
        }

        @Override
        public boolean hoverTestable() {
            return true;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return element.getParentLine() == line;
        }
    }

    /** A glissando or fall drawn from {@code owner}. */
    record Slide(NoteElement owner) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.SLIDE;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return owner.getParentLine() == line;
        }
    }

    /**
     * A glissando owned by a grace note. Grace-note slides are not selectable; this
     * variant exists so the click can be reported and answered with an explanation
     * rather than silently ignored.
     */
    record GraceGlissando(NoteElement owner) implements HitTarget {

        @Override
        public HitPriority priority() {
            return HitPriority.SLIDE;
        }
    }

    /** A crescendo or diminuendo. */
    record Hairpin(songscribe.dom.Hairpin hairpin) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.HAIRPIN;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return isRegisteredOn(hairpin, line);
        }
    }

    /** A volta / ending bracket. */
    record Ending(songscribe.dom.Ending ending) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ENDING;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return isRegisteredOn(ending, line);
        }
    }

    /** The staff line itself, selectable only from its header region at the left. */
    record StaffLine() implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.STAFF_LINE;
        }

        /** Always live: it addresses the staff line itself, not anything on it. */
        @Override
        public boolean isLiveOn(Line line) {
            return true;
        }
    }

    /** A staccato, accent, tenuto and the like. */
    record Articulation(songscribe.dom.Articulation articulation) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ARTICULATION;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return articulation.getParentLine() == line;
        }
    }

    /** A fermata, dynamic, tempo change, beat change or annotation. */
    record Attachment(songscribe.dom.Attachment attachment) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ATTACHMENT;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return attachment.getParentLine() == line;
        }
    }

    /** A tie curve, or the half of one a line draws when the tie crosses a line break. */
    record Tie(songscribe.dom.Tie tie) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TIE;
        }

        /**
         * Live while its anchor still derives this tie ({@link NoteElement#tie()}) and
         * {@code line} holds either of its notes. The anchor alone cannot answer: it stays on
         * its line when the tie is removed, and a tie across a line break can be selected from
         * either of its two lines while the anchor is on only the first.
         */
        @Override
        public boolean isLiveOn(Line line) {
            return tie.equals(tie.anchor().tie()) && isDrawnOn(line);
        }

        /**
         * Whether {@code line} draws this tie or a half of it: whether it holds either of the
         * tie's two notes.
         *
         * @param line any line of the song
         * @return {@code true} when {@code line} holds the tie's anchor or its end
         */
        public boolean isDrawnOn(Line line) {
            return line.placementOf(tie) != null;
        }
    }

    /** A beam group. */
    record Beam(songscribe.dom.Beam beam) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.BEAM;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return isRegisteredOn(beam, line);
        }
    }

    /** A trill — the {@code tr} glyph together with its wavy-line extension, if any. */
    record Trill(songscribe.dom.Trill trill) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TRILL;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return isRegisteredOn(trill, line);
        }
    }

    /** A tuplet bracket and its number, or the number alone when the group is beamed. */
    record Tuplet(songscribe.dom.Tuplet tuplet) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TUPLET;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return isRegisteredOn(tuplet, line);
        }
    }

    /**
     * The attribution block drawn above the first staff line. It is the one target in this
     * interface that is not notation. It exists so a double-click can be resolved to it, and
     * it is not {@link Selectable}: the block is edited in Song Settings, never on the page.
     *
     * <p>It carries no reference to the block it addresses, for the same reason
     * {@link StaffLine} carries none: a song holds exactly one attribution, so naming it
     * would say nothing the song does not already answer.
     */
    record Attribution() implements HitTarget {

        @Override
        public HitPriority priority() {
            return HitPriority.ATTRIBUTION;
        }
    }
}
