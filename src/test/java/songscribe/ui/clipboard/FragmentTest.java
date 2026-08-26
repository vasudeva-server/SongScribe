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
import songscribe.dom.Ending;
import songscribe.dom.Key;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What a {@link Fragment} does about the key it was copied under when it lands somewhere else.
 *
 * <p>A fragment carries the key of the line it came from, not the key of the line it is pasted
 * into, so the same clipboard content is stranded in one destination and meaningful in another.
 * Both questions a paste asks about that are here: which key the fragment leaves in effect behind
 * it, and which of its own key signatures arrive restating what is already running.
 *
 * <p>The reduction happens before the clones are inserted rather than after, so the accidental
 * reconciliation, the fit measurement and the span reconciliation all read the run that actually
 * lands. That is why it is asserted on the fragment rather than on the destination line.
 */
class FragmentTest extends UnitTest {

    /** The key running at the insertion point in every case here. */
    private static final Key DESTINATION_KEY = Key.NO_ACCIDENTALS;

    /** The key a fragment's own signature establishes when it is meant to be a real change. */
    private static final Key FRAGMENT_KEY = Key.TWO_SHARPS;

    /** A third key, so a fragment holding two signatures has two that differ. */
    private static final Key LATER_FRAGMENT_KEY = Key.THREE_FLATS;

    private static final int STAFF_POSITION = 6;

    /** The accidental recorded for the fragment's leading note in its source context. */
    private static final StaffElement.Accidental LEADING_PRIOR = StaffElement.Accidental.SHARP;

    /** The accidental recorded for the fragment's trailing note in its source context. */
    private static final StaffElement.Accidental TRAILING_PRIOR = StaffElement.Accidental.FLAT;

    /**
     * A fragment of {@code note, barline, signature, note} — the shape {@link Fragment#capture}
     * produces for a range beginning at a key signature, since it widens back over the barline.
     *
     * @param key the key the signature establishes
     * @return the fragment, carrying a span over the pair and a span clear of it
     */
    private static Fragment fragmentWithSignature(Key key) {
        var leading = note(STAFF_POSITION);
        var barline = singleBarline();
        var trailing = note(STAFF_POSITION);

        return new Fragment(
            List.of(leading, barline, keyChange(key), trailing),
            Arrays.asList(LEADING_PRIOR, null, null, TRAILING_PRIOR),
            List.of(new Ending(barline, trailing), new Ending(leading, leading)));
    }

    /** The elements of {@code spans}, so a survivor can be named by the elements it joins. */
    private static List<StaffElement> endpointsOf(Span span) {
        return Arrays.asList(span.getAnchorElement(), span.getEndElement());
    }

    @Test
    void testTheKeyAtTheEndIsTheFragmentsLastSignatureWhenItCarriesOne() {
        var leading = note(STAFF_POSITION);

        var fragment = new Fragment(
            List.of(leading, singleBarline(), keyChange(FRAGMENT_KEY),
                singleBarline(), keyChange(LATER_FRAGMENT_KEY)),
            Arrays.asList(LEADING_PRIOR, null, null, null, null),
            List.of());

        assertThat(fragment.keyAtEndUnder(DESTINATION_KEY)).isEqualTo(LATER_FRAGMENT_KEY);
    }

    @Test
    void testTheKeyAtTheEndIsTheInsertionKeyWhenTheFragmentCarriesNoSignature() {
        var fragment = new Fragment(
            List.of(note(STAFF_POSITION)), Arrays.asList(LEADING_PRIOR), List.of());

        assertThat(fragment.keyAtEndUnder(DESTINATION_KEY)).isEqualTo(DESTINATION_KEY);
    }

    @Test
    void testAFragmentStrandingNothingIsReturnedUnchanged() {
        var fragment = fragmentWithSignature(FRAGMENT_KEY);

        assertThat(fragment.withoutRedundantKeyChanges(DESTINATION_KEY))
            .as("the signature changes the key where it lands, so there is nothing to remove")
            .isSameAs(fragment);
    }

    @Test
    void testASignatureLandingOnTheKeyAlreadyRunningGoesWithItsBarlineAndEverythingOnThem() {
        var fragment = fragmentWithSignature(FRAGMENT_KEY);
        var leading = fragment.elements().getFirst();
        var trailing = fragment.elements().getLast();

        var reduced = fragment.withoutRedundantKeyChanges(FRAGMENT_KEY);

        assertThat(reduced.elements())
            .as("the pair goes and the notes either side of it stay")
            .containsExactly(leading, trailing);
        assertThat(reduced.priorAccidentals())
            .as("the recorded source accidentals stay parallel to the elements that survive")
            .containsExactly(LEADING_PRIOR, TRAILING_PRIOR);
        assertThat(reduced.spans().stream().map(FragmentTest::endpointsOf))
            .as("a span with an endpoint on either half of the pair goes with it, as it would if "
                + "the user deleted the pair off a line")
            .containsExactly(List.of(leading, leading));
    }
}
