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
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;

/**
 * The complete vocabulary of things a click on a staff line can address.
 *
 * <p>A kind outside {@link Selectable} can be resolved to and acted on, but never becomes the
 * selection.
 *
 * <p>Every {@code Selectable} names what it selects by object reference, never by index: an
 * index silently names a different live element after a mutation.
 */
public sealed interface HitTarget {

    HitPriority priority();

    sealed interface Selectable extends HitTarget {

        /**
         * Whether a selection of this target made on {@code line} still addresses notation that
         * exists there. A span answers from the line's span registry, since removing a span
         * leaves its endpoints in place.
         */
        boolean isLiveOn(Line line);
    }

    private static boolean isRegisteredOn(Span<?> span, Line line) {
        return line.spanRegistry().getSpans().contains(span);
    }

    /** A note head. Not {@link Selectable}: a note is selected as an index range. */
    record Element(StaffElement element) implements HitTarget {

        @Override
        public HitPriority priority() {
            return HitPriority.ELEMENT;
        }
    }

    record Lyric(StaffElement element, Song.Script script) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.LYRIC;
        }

        @Override
        public boolean isLiveOn(Line line) {
            return element.getParentLine() == line;
        }
    }

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

    /** Not selectable; exists so the click can be answered with an explanation. */
    record GraceGlissando(NoteElement owner) implements HitTarget {

        @Override
        public HitPriority priority() {
            return HitPriority.SLIDE;
        }
    }

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

        @Override
        public boolean isLiveOn(Line line) {
            return true;
        }
    }

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

    record Tie(songscribe.dom.Tie tie) implements Selectable {

        @Override
        public HitPriority priority() {
            return HitPriority.TIE;
        }

        /** A tie across a line break can be selected from either of its two lines. */
        @Override
        public boolean isLiveOn(Line line) {
            return tie.equals(tie.anchor().tie()) && isDrawnOn(line);
        }

        public boolean isDrawnOn(Line line) {
            return line.placementOf(tie) != null;
        }
    }

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

    /** Not {@link Selectable}: the block is edited in Song Settings, never on the page. */
    record Attribution() implements HitTarget {

        @Override
        public HitPriority priority() {
            return HitPriority.ATTRIBUTION;
        }
    }
}
