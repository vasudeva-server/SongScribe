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

package songscribe.layout;

import java.awt.Font;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * How far a melisma and a hyphen reach past the column they start on — the promise that a column
 * bearing no syllable is passed over rather than treated as the end of the chain. See
 * {@code docs/lyrics.md}.
 */
class LyricLayoutBuilderTest extends UnitTest {

    /** One element standing between two syllables, with the name its row reports. */
    private record InterveningElement(String description, Supplier<StaffElement> element) {}

    private static final int VERSE = Lyric.FIRST_VERSE;

    /** Uniform column geometry: every fixture column is one staff space of ink, two apart. */
    private static final double COLUMN_WIDTH_SS = 1.0;

    private static final double COLUMN_PITCH_SS = 2.0;

    /** The gap a melisma keeps from the syllable that follows it. */
    private static final double SPACE_WIDTH_SS = 0.25;

    private static final double SYLLABLE_WIDTH_SS = 0.8;

    /** A width no assertion here reads; these fixtures never overflow. */
    private static final double UNREAD_LINE_WIDTH_SS = 100.0;

    private static final double TOLERANCE_SS = 0.0001;

    private static final String FIRST_SYLLABLE = "hal";

    private static final String LAST_SYLLABLE = "le";

    /** A melisma carrier draws no syllable of its own, so it carries no text. */
    private static final String CARRIER_TEXT = "";

    @ParameterizedTest(name = "{0}")
    @MethodSource("interveningElements")
    void testAMelismaRunsThroughAColumnThatBearsNoSyllable(InterveningElement testCase) {
        var start = syllableColumn(0, FIRST_SYLLABLE, Lyric.Syllabic.SINGLE, Lyric.Extend.START);
        var intervening = bareColumn(1, testCase.element().get());
        var next = syllableColumn(2, LAST_SYLLABLE, Lyric.Syllabic.SINGLE, Lyric.Extend.NONE);

        var result = build(List.of(start, intervening, next));
        var extender = onlyConnector(result, LyricConnectorLayout.Kind.EXTENDER);

        assertThat(extender.endXSs())
            .as("the melisma reaches the syllable it is sung into, not the column standing between")
            .isCloseTo(syllableBoxLeftXSs(next) - SPACE_WIDTH_SS, within(TOLERANCE_SS));
    }

    @Test
    void testAHyphenLeftOpenAtTheEndOfALineReachesTheNextSyllableSlot() {
        var opening = syllableColumn(0, FIRST_SYLLABLE, Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);
        var barline = bareColumn(1, StaffElementFactory.singleBarline());
        var slot = bareColumn(2, StaffElementFactory.crotchet());

        var result = build(List.of(opening, barline, slot));
        var hyphen = onlyConnector(result, LyricConnectorLayout.Kind.DANGLING_HYPHEN);

        assertThat(hyphen.endXSs())
            .as("the hyphen is centered against the note that will carry the next syllable")
            .isCloseTo(slot.getLeftEdgeXSs(), within(TOLERANCE_SS));
    }

    /**
     * A note with a grace note paired in front of it carries the grace's syllable, never one of
     * its own, so it is not the slot the word continues on however note-like its type is.
     */
    @Test
    void testAHyphenOpenedOnAGraceNoteReachesPastTheHostThatCanBearNoSyllable() {
        var grace = StaffElementFactory.graceQuaver();
        grace.setGlissando();

        var opening = column(0, grace,
            new Lyric(VERSE, FIRST_SYLLABLE, Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
        var host = bareColumn(1, StaffElementFactory.crotchet());
        var slot = bareColumn(2, StaffElementFactory.crotchet());

        var result = build(List.of(opening, host, slot));
        var hyphen = onlyConnector(result, LyricConnectorLayout.Kind.DANGLING_HYPHEN);

        assertThat(hyphen.endXSs())
            .as("the host carries the grace's syllable, so the word continues on the note after it")
            .isCloseTo(slot.getLeftEdgeXSs(), within(TOLERANCE_SS));
    }

    /**
     * A melisma nothing closes before the line ends reaches its last carrier. A column bearing no
     * syllable is passed over on the way rather than ending the walk, or a melisma written across
     * a barline would be cut short at it.
     */
    @Test
    void testAMelismaLeftOpenAtTheEndOfALineReachesItsLastCarrierPastABareColumn() {
        var start = syllableColumn(0, FIRST_SYLLABLE, Lyric.Syllabic.SINGLE, Lyric.Extend.START);
        var barline = bareColumn(1, StaffElementFactory.singleBarline());
        var carrier = column(2, StaffElementFactory.crotchet(),
            new Lyric(VERSE, CARRIER_TEXT, Lyric.Extend.CONTINUE, null, false));

        var result = build(List.of(start, barline, carrier));
        var extender = onlyConnector(result, LyricConnectorLayout.Kind.DANGLING_EXTENDER);

        assertThat(extender.endXSs())
            .as("the melisma runs over the barline to the carrier beyond it")
            .isCloseTo(carrier.getRightEdgeXSs(), within(TOLERANCE_SS));
    }

    static Stream<InterveningElement> interveningElements() {
        return Stream.of(
            new InterveningElement("a rest", StaffElementFactory::crotchetRest),
            new InterveningElement("a barline", StaffElementFactory::singleBarline),
            new InterveningElement("a breath mark", StaffElementFactory::breathMark),
            new InterveningElement("a key change",
                () -> StaffElementFactory.keyChange(Key.NO_ACCIDENTALS))
        );
    }

    private static LyricLayoutBuilder.Result build(List<ElementColumn> columns) {
        return LyricLayoutBuilder.build(columns, VERSE, metrics(), false, UNREAD_LINE_WIDTH_SS);
    }

    /**
     * The one connector of {@code kind} the fixture produces. Asserting there is exactly one is
     * part of the promise: a chain that reached its target emits a single span.
     */
    private static LyricConnectorLayout onlyConnector(
        LyricLayoutBuilder.Result result,
        LyricConnectorLayout.Kind kind
    ) {
        var matching = result.connectors().stream().filter(connector -> connector.kind() == kind).toList();

        assertThat(matching).as("connectors of kind %s", kind).hasSize(1);

        return matching.getFirst();
    }

    private static ElementColumn syllableColumn(
        int index,
        String text,
        Lyric.Syllabic syllabic,
        Lyric.Extend extend
    ) {
        return column(index, StaffElementFactory.crotchet(),
            new Lyric(VERSE, text, extend, syllabic, false));
    }

    private static ElementColumn bareColumn(int index, StaffElement element) {
        return column(index, element, null);
    }

    private static ElementColumn column(int index, StaffElement element, @Nullable Lyric lyric) {
        var column = new ElementColumn(
            element,
            List.of(),
            0.0,
            COLUMN_WIDTH_SS,
            COLUMN_WIDTH_SS,
            0.0,
            0.0,
            lyric,
            // A carrier — a lyric with no text — is a melisma marker, not a drawn syllable.
            lyric != null && !lyric.text().isEmpty() ? SYLLABLE_WIDTH_SS : 0.0,
            false);

        column.setXSs(index * COLUMN_PITCH_SS);

        return column;
    }

    /** Where {@code column}'s syllable box starts: its text centered on the notehead. */
    private static double syllableBoxLeftXSs(ElementColumn column) {
        return column.getNoteheadCenterXSs() - SYLLABLE_WIDTH_SS / 2.0;
    }

    private static LyricRenderMetrics metrics() {
        var font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

        return new LyricRenderMetrics(font, font, SPACE_WIDTH_SS, SPACE_WIDTH_SS, 0.0);
    }
}
