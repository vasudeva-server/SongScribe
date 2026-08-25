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

import org.jspecify.annotations.Nullable;

import songscribe.dom.LineElement;
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
     * the selection layer's liveness rule total: it holds only a {@code Selectable}, so
     * {@link #owner()} is answerable for whatever it holds.
     */
    sealed interface Selectable extends HitTarget {

        /**
         * The element this target hangs off, used by the selection layer's single liveness
         * rule — walk the parent chain and check that the element is still on a line. Every
         * variant answers this in one line, so revalidation needs no per-variant switch.
         *
         * @return the owning element, or {@code null} for {@link StaffLine}, which addresses
         *         the staff line itself rather than anything on it
         */
        @Nullable LineElement owner();
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
        public StaffElement owner() {
            return element;
        }
    }

    /** A glissando or fall drawn from {@code owner}. */
    record Slide(StaffElement owner) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.SLIDE;
        }
    }

    /**
     * A glissando owned by a grace note. Grace-note slides are not selectable; this
     * variant exists so the click can be reported and answered with an explanation
     * rather than silently ignored.
     */
    record GraceGlissando(StaffElement owner) implements HitTarget {

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
        public LineElement owner() {
            return hairpin;
        }
    }

    /** A volta / ending bracket. */
    record Ending(songscribe.dom.Ending ending) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ENDING;
        }

        @Override
        public LineElement owner() {
            return ending;
        }
    }

    /** The staff line itself, selectable only from its header region at the left. */
    record StaffLine() implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.STAFF_LINE;
        }

        @Override
        public @Nullable LineElement owner() {
            return null;
        }
    }

    /** A staccato, accent, tenuto and the like. */
    record Articulation(songscribe.dom.Articulation articulation) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ARTICULATION;
        }

        @Override
        public LineElement owner() {
            return articulation;
        }
    }

    /** A fermata, dynamic, tempo change, beat change or annotation. */
    record Attachment(songscribe.dom.Attachment attachment) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ATTACHMENT;
        }

        @Override
        public LineElement owner() {
            return attachment;
        }
    }

    /**
     * The accidental of {@code owner}, as distinct from the note itself.
     *
     * <p>This is the one sub-element variant, and it is what makes accidentals
     * selectable at all: an accidental is not a {@code LineElement} but a field on its
     * note, so neither an element reference nor an index can name it on its own.
     */
    record Accidental(StaffElement owner) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.ACCIDENTAL;
        }
    }

    /** A tie or slur curve. */
    record Tie(songscribe.dom.Tie tie) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TIE;
        }

        @Override
        public LineElement owner() {
            return tie;
        }
    }

    /** A beam group. */
    record Beam(songscribe.dom.Beam beam) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.BEAM;
        }

        @Override
        public LineElement owner() {
            return beam;
        }
    }

    /** A trill — the {@code tr} glyph together with its wavy-line extension, if any. */
    record Trill(songscribe.dom.Trill trill) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TRILL;
        }

        @Override
        public LineElement owner() {
            return trill;
        }
    }

    /** A tuplet bracket and its number, or the number alone when the group is beamed. */
    record Tuplet(songscribe.dom.Tuplet tuplet) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TUPLET;
        }

        @Override
        public LineElement owner() {
            return tuplet;
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
