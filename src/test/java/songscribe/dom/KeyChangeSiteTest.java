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

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * That each of the three places a key change can be bound to answers the key actually sounding
 * there, and says correctly whether writing a given key there would change anything.
 *
 * <p>All three cases run against <b>one line</b>, keyed so that every place answers a different
 * signature: the line's own key, the key a mid-line signature establishes, and the key running at
 * a position between two signatures. A site reading the wrong place would answer one of the other
 * two rather than something obviously absent, which is what makes the fixture worth its length.
 *
 * <p>What rides on the answer is what the key dialog opens on and what its OK refuses. The key
 * already in effect is the choice that would otherwise record an undo step for a change that
 * changes nothing, and {@link KeyChangeSite#wouldChangeAnything} is built on this — so a site
 * naming the wrong place would open the dialog on the wrong key <em>and</em> refuse the wrong one.
 *
 * <p>"Each of the three" is kept true by {@link #testCasesCoverEveryBinding}, which pins the case
 * table to {@link KeyChangeSite.Binding}'s constants rather than to three literals that were
 * complete on the day they were written.
 */
class KeyChangeSiteTest extends UnitTest {

    /** The key the test line's header establishes. */
    private static final Key LINE_KEY = Key.TWO_SHARPS;

    /** The key the signature at {@link #EARLIER_SIGNATURE_INDEX} establishes. */
    private static final Key EARLIER_SIGNATURE_KEY = Key.THREE_FLATS;

    /** The key the signature at {@link #LATER_SIGNATURE_INDEX} establishes. */
    private static final Key LATER_SIGNATURE_KEY = Key.FOUR_SHARPS;

    private static final int EARLIER_SIGNATURE_INDEX = 1;
    private static final int LATER_SIGNATURE_INDEX = 3;

    /** A position with no signature on it, between the two signatures. */
    private static final int BETWEEN_SIGNATURES_INDEX = 2;

    /** A key no place on the test line is in, so writing it anywhere is a change. */
    private static final Key UNUSED_KEY = Key.ONE_FLAT;

    /** The key an inheriting line runs in without establishing one of its own. */
    private static final Key INHERITED_KEY = Key.FIVE_SHARPS;

    /**
     * One place a key change can be bound to, and the key in effect there.
     *
     * @param binding     which of the three places, which pins the table to the enum
     * @param site        builds the site for that place against a given line
     * @param keyInEffect the key {@link KeyChangeSite#keyInEffect()} must answer for it
     */
    private record BindingCase(
        KeyChangeSite.Binding binding, Function<Line, KeyChangeSite> site, Key keyInEffect
    ) {

        @Override
        public String toString() {
            return binding.toString();
        }
    }

    static Stream<BindingCase> bindingCases() {
        return Stream.of(
            new BindingCase(
                KeyChangeSite.Binding.LINE_KEY, KeyChangeSite::lineKey, LINE_KEY),
            new BindingCase(
                KeyChangeSite.Binding.EXISTING_SIGNATURE,
                line -> KeyChangeSite.existingSignature(line, LATER_SIGNATURE_INDEX),
                LATER_SIGNATURE_KEY),
            new BindingCase(
                KeyChangeSite.Binding.NEW_POSITION,
                line -> KeyChangeSite.newPosition(line, BETWEEN_SIGNATURES_INDEX),
                EARLIER_SIGNATURE_KEY)
        );
    }

    /**
     * A line in {@link #LINE_KEY} carrying two mid-line key signatures, so that each place has a
     * different key in effect at the index it is bound to.
     *
     * @return the line, unattached to a song
     */
    private static Line keyedLine() {
        var line = detachedLine();

        line.setKey(LINE_KEY);
        line.addElement(crotchet());
        line.addElement(new KeyChangeElement(EARLIER_SIGNATURE_KEY));
        line.addElement(crotchet());
        line.addElement(new KeyChangeElement(LATER_SIGNATURE_KEY));
        line.addElement(crotchet());

        return line;
    }

    /**
     * A line that establishes no key of its own, running in {@link #INHERITED_KEY} because the
     * line before it left off there.
     *
     * <p>The song is a mock stubbed to answer that inheritance, because what the fixture needs is
     * a line whose own key is absent while its running key is not — and a real two-line song would
     * arrange nothing this one does not.
     *
     * @return the line, in {@link #INHERITED_KEY} but holding no key
     */
    private static Line inheritingLine() {
        var song = minimalSongMock();

        when(song.runningKeyAt(any(Line.class))).thenReturn(INHERITED_KEY);

        return new Line(song);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bindingCases")
    void testEachBindingAnswersTheKeyInEffectWhereItIsBound(BindingCase testCase) {
        var site = testCase.site().apply(keyedLine());

        assertThat(site.keyInEffect()).isEqualTo(testCase.keyInEffect());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bindingCases")
    void testWritingTheKeyAlreadyInEffectChangesNothing(BindingCase testCase) {
        var site = testCase.site().apply(keyedLine());

        assertThat(site.wouldChangeAnything(testCase.keyInEffect())).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bindingCases")
    void testWritingAKeyThatIsNotInEffectChangesSomething(BindingCase testCase) {
        var site = testCase.site().apply(keyedLine());

        assertThat(site.wouldChangeAnything(UNUSED_KEY)).isTrue();
    }

    /**
     * The case the other two cannot reach: every row of the table runs against a line that has a
     * key of its own, where the key in effect is the whole answer. A line that inherits its key
     * takes one of its own when the dialog commits, so the commit changes the document even though
     * the key it writes is the one already sounding.
     */
    @Test
    void testAdoptingTheInheritedKeyStillChangesAnInheritingLine() {
        var site = KeyChangeSite.lineKey(inheritingLine());

        assertThat(site.keyInEffect()).isEqualTo(INHERITED_KEY);
        assertThat(site.wouldChangeAnything(INHERITED_KEY)).isTrue();
    }

    @Test
    void testCasesCoverEveryBinding() {
        assertThat(bindingCases().map(BindingCase::binding))
            .containsExactlyInAnyOrder(KeyChangeSite.Binding.values());
    }
}
