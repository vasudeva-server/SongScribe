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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Ending;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.Trill;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What a {@link Fragment} holds and does.
 *
 * <p>{@link Fragment#capture} copies a range of a line: a span goes with it only when both its
 * ends are inside, a tie only when the note it ends on is, and a final barline is normalized to a
 * plain double one. {@link Fragment#instantiate} gives fresh objects on every call, so one capture
 * can be pasted again and again.
 *
 * <p>A fragment carries the key of the line it came from, not the key of the line it is pasted
 * into, so the same clipboard content is stranded in one destination and meaningful in another.
 * {@link Fragment#withoutRedundantKeyChanges} answers which of its own key changes arrive
 * restating what is already running.
 *
 * <p>That reduction happens before the clones are inserted rather than after, so the chain walk,
 * the fit measurement and the span reconciliation all read the run that actually
 * lands. That is why it is asserted on the fragment rather than on the destination line.
 */
class FragmentTest extends UnitTest {

    /** The key running at the insertion point in every key-change case here. */
    private static final Key DESTINATION_KEY = Key.NO_ACCIDENTALS;

    /** The key a fragment's own key change establishes when it is meant to be a real change. */
    private static final Key FRAGMENT_KEY = Key.TWO_SHARPS;

    /** An empty line follows each case's line, so it is never the song's last. */
    private static final String EMPTY_LINE = "";

    private static final String FOUR_QUAVERS = "Q Q Q Q";

    /** The span the capture cases hold: the middle two of {@link #FOUR_QUAVERS}. */
    private static final ElementRange CAPTURED_SPAN = new ElementRange(1, 2);

    /** The first index of {@link #endingFragment}: the note the ending is anchored on. */
    private static final int ENDING_ANCHOR_INDEX = 0;

    /** The index of the barline that ends the ending, in front of the key change. */
    private static final int ENDING_END_INDEX = 3;

    /** The index of the key change, which the barline before it travels with. */
    private static final int KEY_CHANGE_INDEX = 4;

    /** The line of a one-line song whose notation is {@code notation}. */
    private static Line lineOfNotation(String notation) {
        return compactSong(Key.NO_ACCIDENTALS, notation, EMPTY_LINE, EMPTY_LINE).getLine(0);
    }

    /** A line of {@link #FOUR_QUAVERS} holding a trill over {@link #CAPTURED_SPAN}. */
    private static Line quaversWithTrill() {
        var line = lineOfNotation(FOUR_QUAVERS);

        addWithoutTracking(line.getSong(), () -> trillOver(line, CAPTURED_SPAN));

        return line;
    }

    /**
     * A fragment of {@code note, repeat, note, barline, key change, note}, with an ending over the
     * first four and a trill over the first note. It is what {@link Fragment#capture} produces for
     * the whole of a line whose key change stands behind the barline that ends the ending.
     */
    private static Fragment endingFragment(Key key) {
        var song = compactSong(
            Key.NO_ACCIDENTALS, "6 :| 6 | K:" + key.name() + " 6", EMPTY_LINE, EMPTY_LINE);
        var line = song.getLine(0);

        addWithoutTracking(song, () -> endingOver(line, new ElementRange(ENDING_ANCHOR_INDEX, ENDING_END_INDEX)));
        addWithoutTracking(song, () -> trillOver(line, ElementRange.single(ENDING_ANCHOR_INDEX)));

        return Fragment.capture(line, wholeRangeOf(line));
    }

    /**
     * A range of {@link #quaversWithTrill}'s line, and what capturing it makes of the trill.
     *
     * @param description the case, as the test's display name
     * @param range       the range captured
     */
    private record RangeCase(String description, ElementRange range) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("rangesHoldingBothEnds")
    void testASpanWithBothEndsInTheRangeIsCapturedOntoTheClones(RangeCase testCase) {
        var line = quaversWithTrill();

        var fragment = Fragment.capture(line, testCase.range());
        var firstIndex = CAPTURED_SPAN.begin() - testCase.range().begin();
        var lastIndex = CAPTURED_SPAN.end() - testCase.range().begin();

        assertThat(fragment.spans()).singleElement().satisfies(span -> {
            assertThat(span).isInstanceOf(Trill.class);
            assertThat(span.getAnchorElement()).isSameAs(fragment.elements().get(firstIndex));
            assertThat(span.getEndElement()).isSameAs(fragment.elements().get(lastIndex));
        });
    }

    static Stream<RangeCase> rangesHoldingBothEnds() {
        return Stream.of(
            new RangeCase("the range is exactly the span", CAPTURED_SPAN),
            new RangeCase("the range reaches past both ends", new ElementRange(0, CAPTURED_SPAN.end() + 1))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rangesMissingAnEnd")
    void testASpanWithAnEndOutsideTheRangeIsDropped(RangeCase testCase) {
        var fragment = Fragment.capture(quaversWithTrill(), testCase.range());

        assertThat(fragment.spans()).isEmpty();
    }

    static Stream<RangeCase> rangesMissingAnEnd() {
        return Stream.of(
            new RangeCase("the range stops on the span's anchor", new ElementRange(0, CAPTURED_SPAN.begin())),
            new RangeCase("the range starts on the span's end", new ElementRange(CAPTURED_SPAN.end(), CAPTURED_SPAN.end() + 1))
        );
    }

    /**
     * A range of a line holding a note tied to the one after it, and whether the first note's
     * clone keeps the tie.
     *
     * @param description the case, as the test's display name
     * @param range       the range captured
     * @param tied        whether the first clone is tied to the next
     */
    private record TieCase(String description, ElementRange range, boolean tied) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("tieCases")
    void testACopiedNoteKeepsItsTieOnlyWhenTheNoteItEndsOnIsCopiedToo(TieCase testCase) {
        var line = lineOfNotation("6 ~ 6 6");

        var fragment = Fragment.capture(line, testCase.range());

        assertThat(fragment.elements().getFirst())
            .isInstanceOfSatisfying(NoteElement.class, clone ->
                assertThat(clone.isTiedToNext()).isEqualTo(testCase.tied()));
    }

    static Stream<TieCase> tieCases() {
        return Stream.of(
            new TieCase("the tie's end is copied too", new ElementRange(0, 1), true),
            new TieCase("the tie's end is not copied", new ElementRange(0, 0), false)
        );
    }

    @Test
    void testACapturedFinalBarlineBecomesAPlainDoubleBarline() {
        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(finalDoubleBarline());
        });
        var line = song.getLine(0);

        var fragment = Fragment.capture(line, wholeRangeOf(line));

        assertThat(fragment.elements().getLast().getType()).isEqualTo(ElementType.DOUBLE_BARLINE);
        assertThat(line.getElement(line.elementCount() - 1).getType())
            .as("the source line keeps its final barline")
            .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    @Test
    void testALoneOrphanGraceNoteGivesAnEmptyFragment() {
        var line = lineOfNotation("g6 6");
        ((NoteElement) line.getElement(0)).setSlide(Slide.GLISSANDO);

        var fragment = Fragment.capture(line, ElementRange.single(0));

        assertThat(fragment.elements()).isEmpty();
        assertThat(fragment.spans()).isEmpty();
    }

    @Test
    void testEveryInstantiationHoldsFreshElementsAndSpansAnchoredOnThem() {
        var captured = Fragment.capture(quaversWithTrill(), new ElementRange(0, CAPTURED_SPAN.end() + 1));

        var first = captured.instantiate();
        var second = captured.instantiate();

        for (var instance : List.of(first, second)) {
            assertThat(instance.elements()).hasSameSizeAs(captured.elements());

            for (var index = 0; index < instance.elements().size(); index++) {
                assertThat(instance.elements().get(index))
                    .isNotSameAs(captured.elements().get(index))
                    .isNotSameAs(
                        (instance == first ? second : first).elements().get(index));
            }

            assertThat(instance.spans()).singleElement().satisfies(span -> {
                assertThat(span).isNotSameAs(captured.spans().getFirst());
                assertThat(span.getAnchorElement()).isSameAs(instance.elements().get(CAPTURED_SPAN.begin()));
                assertThat(span.getEndElement()).isSameAs(instance.elements().get(CAPTURED_SPAN.end()));
            });
        }
    }

    @Test
    void testAFragmentStrandingNothingIsReturnedUnchanged() {
        var fragment = endingFragment(FRAGMENT_KEY);

        var reduction = fragment.withoutRedundantKeyChanges(DESTINATION_KEY);

        assertThat(reduction.fragment())
            .as("the key change changes the key where it lands, so there is nothing to remove")
            .isSameAs(fragment);
        assertThat(reduction.strandedSpans()).isEmpty();
    }

    @Test
    void testAKeyChangeLandingOnTheKeyAlreadyRunningGoesWithItsBarlineAndTheSpansEndingOnThem() {
        var fragment = endingFragment(FRAGMENT_KEY);
        var elements = fragment.elements();
        var trill = fragment.spans().stream().filter(Trill.class::isInstance).findFirst().orElseThrow();
        var ending = fragment.spans().stream().filter(Ending.class::isInstance).findFirst().orElseThrow();

        var reduction = fragment.withoutRedundantKeyChanges(FRAGMENT_KEY);

        var staying = new ArrayList<>(elements);
        staying.remove(KEY_CHANGE_INDEX);
        staying.remove(ENDING_END_INDEX);

        assertThat(reduction.fragment().elements())
            .as("the pair goes and the elements either side of it stay")
            .containsExactlyElementsOf(staying);
        assertThat(reduction.fragment().spans())
            .as("a span clear of the pair stays")
            .containsExactly(trill);
        assertThat(reduction.strandedSpans()).singleElement().satisfies(stranded -> {
            assertThat(stranded.span())
                .as("the ending ends on the barline, so it goes with the pair")
                .isSameAs(ending);
            assertThat(stranded.covered()).containsExactlyElementsOf(elements.subList(0, ENDING_END_INDEX + 1));
        });
        assertThat(elements.get(ENDING_END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
        assertThat(elements.get(KEY_CHANGE_INDEX).getType()).isEqualTo(ElementType.KEY_CHANGE);
    }

    @Test
    void testAFragmentThatIsOnlyARedundantKeyChangeWithItsBarlineIsReducedToNothing() {
        var fragment = new Fragment(List.of(singleBarline(), keyChange(FRAGMENT_KEY)), List.of());

        var reduction = fragment.withoutRedundantKeyChanges(FRAGMENT_KEY);

        assertThat(reduction.fragment().elements()).isEmpty();
        assertThat(reduction.fragment().spans()).isEmpty();
        assertThat(reduction.strandedSpans()).isEmpty();
    }
}
