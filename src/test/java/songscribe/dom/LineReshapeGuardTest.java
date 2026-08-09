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
package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Pins {@link Line}'s refusal to act on a {@link SpanOutcome.Reshape} from anything but a
 * {@link Hairpin}.
 *
 * <p>The merge pass that consumes a reshape replaces a run of hairpins with a copy of one of
 * them, so there is nothing it could do with any other kind of span. Nothing in production
 * answers {@code Reshape} except a hairpin, which is exactly why this test exists: a future
 * span type that borrows hairpin logic without being added to the check would otherwise reach
 * production with no test standing in its way.
 */
class LineReshapeGuardTest extends UnitTest {

    /** Positions in the fixture line, which holds {@link #NOTE_COUNT} plain notes. */
    private static final int ANCHOR_INDEX = 0;
    private static final int END_INDEX = 1;
    private static final int DELETED_INDEX = 2;
    private static final int NOTE_COUNT = 3;

    /**
     * A span that answers every change with a reshape, as only a hairpin may.
     * <p>
     * The reported positions are a legal range so that nothing but the span's type can be
     * what {@link Line} objects to.
     */
    private static final class AlwaysReshapingSpan extends Span {

        AlwaysReshapingSpan(StaffElement anchorElement, StaffElement endElement) {
            super(anchorElement, endElement);
        }

        @Override
        public SpanOutcome outcomeFor(ElementChange change, Line line) {
            return new SpanOutcome.Reshape(ANCHOR_INDEX, END_INDEX);
        }

        @Override
        protected Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
            return new AlwaysReshapingSpan(newAnchor, newEnd);
        }

        @Override
        public double getSpanWidthSs(double anchorXSs, double endXSs) {
            return 0;
        }
    }

    @Test
    void testANonHairpinAnsweringReshapeIsRejected() {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            }

            line.addSpan(new AlwaysReshapingSpan(
                line.getElement(ANCHOR_INDEX), line.getElement(END_INDEX)));
        });

        assertThatThrownBy(() -> song.withoutMutationTracking(() -> line.removeElement(DELETED_INDEX)))
            .as("only a hairpin may answer Reshape, and the sweep must say so rather than "
                + "silently dropping the answer")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only a hairpin may answer Reshape")
            .hasMessageContaining(AlwaysReshapingSpan.class.getSimpleName());
    }
}
