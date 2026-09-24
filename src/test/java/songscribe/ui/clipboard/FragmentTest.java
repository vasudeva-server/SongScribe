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

package songscribe.ui.clipboard;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.Trill;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What a {@link Fragment} does about the key it was copied under when it lands somewhere else.
 *
 * <p>A fragment carries the key of the line it came from, not the key of the line it is pasted
 * into, so the same clipboard content is stranded in one destination and meaningful in another.
 * The question a paste asks about that is here: which of its own key changes arrive restating what
 * is already running.
 *
 * <p>The reduction happens before the clones are inserted rather than after, so the chain walk,
 * the fit measurement and the span reconciliation all read the run that actually
 * lands. That is why it is asserted on the fragment rather than on the destination line.
 */
class FragmentTest extends UnitTest {

    /** The key running at the insertion point in every case here. */
    private static final Key DESTINATION_KEY = Key.NO_ACCIDENTALS;

    /** The key a fragment's own key change establishes when it is meant to be a real change. */
    private static final Key FRAGMENT_KEY = Key.TWO_SHARPS;

    private static final int STAFF_POSITION = 6;

    /**
     * A fragment of {@code note, barline, key change, note} — the shape {@link Fragment#capture}
     * produces for a range beginning at a key change, since it widens back over the barline.
     *
     * @param key the key the key change establishes
     * @return the fragment, carrying a span over the pair and a span clear of it
     */
    private static Fragment fragmentWithKeyChange(Key key) {
        var leading = note(STAFF_POSITION);
        var barline = singleBarline();
        var trailing = note(STAFF_POSITION);

        return new Fragment(
            List.of(leading, barline, keyChange(key), trailing),
            List.of(new Trill(barline, trailing), new Trill(leading, leading)));
    }

    /** The elements of {@code spans}, so a survivor can be named by the elements it joins. */
    private static List<StaffElement> endpointsOf(Span<?> span) {
        return Arrays.asList(span.getAnchorElement(), span.getEndElement());
    }

    @Test
    void testAFragmentStrandingNothingIsReturnedUnchanged() {
        var fragment = fragmentWithKeyChange(FRAGMENT_KEY);

        assertThat(fragment.withoutRedundantKeyChanges(DESTINATION_KEY))
            .as("the key change changes the key where it lands, so there is nothing to remove")
            .isSameAs(fragment);
    }

    @Test
    void testAKeyChangeLandingOnTheKeyAlreadyRunningGoesWithItsBarlineAndEverythingOnThem() {
        var fragment = fragmentWithKeyChange(FRAGMENT_KEY);
        var leading = fragment.elements().getFirst();
        var trailing = fragment.elements().getLast();

        var reduced = fragment.withoutRedundantKeyChanges(FRAGMENT_KEY);

        assertThat(reduced.elements())
            .as("the pair goes and the notes either side of it stay")
            .containsExactly(leading, trailing);
        assertThat(reduced.spans().stream().map(FragmentTest::endpointsOf))
            .as("a span with an endpoint on either half of the pair goes with it, as it would if "
                + "the user deleted the pair off a line")
            .containsExactly(List.of(leading, leading));
    }
}
