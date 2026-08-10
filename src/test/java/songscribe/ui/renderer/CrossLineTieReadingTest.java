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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockStatic;
import static songscribe.midi.MidiSequenceBuilder.PPQ;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.Tie;
import songscribe.font.DocumentFonts;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.MessageCenter;
import songscribe.midi.LineTrackBuilder;
import songscribe.midi.PlaybackSettings;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.selection.ReflectionTestHelper;

/**
 * Tests for the tie-reading sites converted to receiver-relative resolution by #493's phases
 * 6a/6b: {@link LineInvariants#isElementInPlayingTie}, the note-on/off suppression in
 * {@link LineTrackBuilder}, and the tie-partner walk in
 * {@link MusicEditOperations#flipStemDirection()}.
 * <p>
 * Each of these sites used to read a {@link Tie}'s anchor and end index as a raw pair. For a
 * same-line tie both indices resolve through the same line and the comparison works; for a
 * cross-line tie the anchor resolves through the anchor's own line and the end through the
 * end's, so the pair silently stops meaning anything (#493's contract: "any site that takes
 * both values from a Tie and compares them gets two indices from two different lines"). These
 * tests build a real two-line {@link Song} with a tie whose anchor is the last note of the
 * first line and whose end is the first element of the second line — the only shape a
 * cross-line tie takes — and check each converted site from both lines' own perspective.
 */
class CrossLineTieReadingTest extends UnitTest {

    // Element indices in the CrossLineFixture below: each line holds the tie endpoint it owns
    // plus one unrelated note, so a converted site can be checked against a note that is
    // deliberately not part of the tie.
    private static final int FIRST_LINE_UNRELATED_INDEX = 0;
    private static final int ANCHOR_INDEX = 1;
    private static final int END_INDEX = 0;
    private static final int SECOND_LINE_UNRELATED_INDEX = 1;

    private static final TieRenderer TIE_RENDERER = TieRenderer.getInstance();
    private static final Color SELECTION_COLOR = Color.BLUE;

    private static final int LYRIC_FONT_SIZE = 12;
    private static final Font LYRIC_FONT = new Font(Font.MONOSPACED, Font.PLAIN, LYRIC_FONT_SIZE);

    private static final int DEFAULT_MIDI_INSTRUMENT = 0;
    private static final int NORMAL_TEMPO_PERCENT = 100;
    private static final int FULL_NOTE_DURATION_PERCENT = 100;
    private static final PlaybackSettings DEFAULT_PLAYBACK_SETTINGS = new PlaybackSettings(
        DEFAULT_MIDI_INSTRUMENT, NORMAL_TEMPO_PERCENT, FULL_NOTE_DURATION_PERCENT, false
    );

    /**
     * A tie whose anchor is the last note of {@code firstLine} and whose end is the first
     * element of {@code secondLine} — the shape a cross-line tie always has (#493) — with an
     * extra, untied crotchet on either side so a site's handling of the tie can be checked
     * against a note that is deliberately not part of it.
     * <p>
     * The unrelated note in the second line deliberately sits at the same index as the anchor
     * does in the first, so a site that read an index resolved in the far line would land on a
     * real but wrong element rather than harmlessly off the end.
     */
    private record CrossLineFixture(
        Song song,
        Line firstLine,
        Line secondLine,
        StaffElement unrelatedInFirstLine,
        StaffElement anchor,
        StaffElement end,
        StaffElement unrelatedInSecondLine,
        Tie tie
    ) {

        static CrossLineFixture create() {
            var song = new Song();
            var firstLine = song.getLine(0);
            var secondLine = new Line(song);
            var unrelatedInFirstLine = crotchet();
            var anchor = crotchet();
            var end = crotchet();
            var unrelatedInSecondLine = crotchet();
            var tie = new Tie(anchor, end);

            song.withoutMutationTracking(() -> {
                firstLine.addElement(unrelatedInFirstLine);
                firstLine.addElement(anchor);
                song.addLine(secondLine);
                secondLine.addElement(end);
                secondLine.addElement(unrelatedInSecondLine);
                firstLine.addTie(tie);
            });

            return new CrossLineFixture(
                song, firstLine, secondLine, unrelatedInFirstLine, anchor, end,
                unrelatedInSecondLine, tie
            );
        }
    }

    // ======================================================================
    // LineInvariants.isElementInPlayingTie — the playing-tie highlight
    // ======================================================================

    private static LineInvariants.Builder seededInvariantsBuilder() {
        return LineInvariants.builder(new Song(), DocumentFonts.defaultFonts())
            .setLayoutResult(LayoutResult.builder().build())
            .setLyricRenderMetrics(new LyricRenderMetrics(LYRIC_FONT, LYRIC_FONT, 0, 0, 0));
    }

    @Test
    void testPlayingTieHighlightFiresForTheAnchorHalfInTheFirstLine() {
        var fixture = CrossLineFixture.create();
        var invariants = seededInvariantsBuilder()
            .setCurrentLine(fixture.firstLine())
            .setPlayingNoteIndex(ANCHOR_INDEX)
            .build();

        // Old behavior: comparing the tie's raw anchor/end pair gives anchorIndex <= i &&
        // endIndex >= i with the end resolved in the *other* line, which is unsatisfiable — the
        // highlight would silently never fire. Receiver-relative resolution asks firstLine for
        // both bounds, giving At(1)/AFTER_LINE, so the anchor's own index is covered.
        assertThat(invariants.isElementInPlayingTie(ANCHOR_INDEX))
            .as("the anchor is under its own half of the cross-line tie")
            .isTrue();
        assertThat(invariants.isElementInPlayingTie(FIRST_LINE_UNRELATED_INDEX))
            .as("the unrelated note before the anchor is not part of the tie")
            .isFalse();
    }

    @Test
    void testPlayingTieHighlightFiresForTheEndHalfInTheSecondLine() {
        var fixture = CrossLineFixture.create();
        var invariants = seededInvariantsBuilder()
            .setCurrentLine(fixture.secondLine())
            .setPlayingNoteIndex(END_INDEX)
            .build();

        // Asking secondLine for the same tie's bounds gives BEFORE_LINE/At(0): the far
        // (anchor) side is unpositioned here, and the end's own index is covered.
        assertThat(invariants.isElementInPlayingTie(END_INDEX))
            .as("the end is under its own half of the cross-line tie")
            .isTrue();
        assertThat(invariants.isElementInPlayingTie(SECOND_LINE_UNRELATED_INDEX))
            .as("the unrelated note after the end is not part of the tie")
            .isFalse();
    }

    // ======================================================================
    // TieRenderer.determineTieColor — each line colors only its own half
    // ======================================================================

    @Test
    void testTieColorComesFromTheSelectedEndpointInTheLineDrawingIt() {
        var fixture = CrossLineFixture.create();
        var anchorBuilder = seededInvariantsBuilder().setSelectionColor(SELECTION_COLOR);
        RenderContextTestHelper.enableSelection(anchorBuilder, fixture.firstLine(), ANCHOR_INDEX);

        var endBuilder = seededInvariantsBuilder().setSelectionColor(SELECTION_COLOR);
        RenderContextTestHelper.enableSelection(endBuilder, fixture.secondLine(), END_INDEX);

        // Each line owns one endpoint, and selecting it colors that line's half.
        assertAll(
            () -> assertThat(TIE_RENDERER.determineTieColor(fixture.tie(), anchorBuilder.build()))
                .as("the anchor line colors its half from the anchor it owns")
                .isEqualTo(SELECTION_COLOR),
            () -> assertThat(TIE_RENDERER.determineTieColor(fixture.tie(), endBuilder.build()))
                .as("the end line colors its half from the end it owns")
                .isEqualTo(SELECTION_COLOR)
        );
    }

    @Test
    void testTieColorIgnoresAnEndpointThatIsNotInTheLineBeingDrawn() {
        var fixture = CrossLineFixture.create();

        // Select the note sitting at the *anchor's* index but in the second line — a different
        // note entirely, which the tie does not touch. Reading the tie's raw anchor index would
        // ask the second line for the color of its element 1 and paint the half selected on the
        // strength of an unrelated note. Resolving against the line being drawn yields
        // BEFORE_LINE for the anchor there, which names no element and so contributes no color.
        var builder = seededInvariantsBuilder().setSelectionColor(SELECTION_COLOR);
        RenderContextTestHelper.enableSelection(
            builder, fixture.secondLine(), SECOND_LINE_UNRELATED_INDEX);

        assertThat(SECOND_LINE_UNRELATED_INDEX)
            .as("the unrelated note must share the anchor's index for this to be a real trap")
            .isEqualTo(ANCHOR_INDEX);
        assertThat(TIE_RENDERER.determineTieColor(fixture.tie(), builder.build()))
            .as("a note the tie does not touch must not color it")
            .isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }

    // ======================================================================
    // LineTrackBuilder — note-on/off suppression across the tie
    // ======================================================================

    private static Track buildMidiTrack(Line line) throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, PPQ);
        var track = sequence.createTrack();
        new LineTrackBuilder(line).addToTrack(
            track, 0, 0, new Tempo(), DEFAULT_PLAYBACK_SETTINGS);
        return track;
    }

    private static List<MidiEvent> eventsByCommand(Track track, int command) {
        var events = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var event = track.get(i);

            if (event.getMessage() instanceof ShortMessage message
                    && message.getCommand() == command) {
                events.add(event);
            }
        }

        return events;
    }

    @Test
    void testMidiSuppressesNoteOnForTheContinuationNoteButStillReleasesIt() throws InvalidMidiDataException {
        var fixture = CrossLineFixture.create();
        var track = buildMidiTrack(fixture.secondLine());

        // Old behavior: comparing tieSpan.getAnchorElementIndex() (resolved via the anchor's
        // own line, the first line — always 1 here) against this line's elementIndex would
        // never match the continuation's own index (0), so this particular pairing happened to
        // suppress correctly by luck of the two raw indices differing. Receiver-relative
        // resolution removes the luck: asking secondLine for the anchor bound gives BEFORE_LINE,
        // which never equals an in-line index regardless of what the raw anchor index is.
        assertThat(eventsByCommand(track, ShortMessage.NOTE_ON))
            .as("only the unrelated note strikes; the continuation's onset already sounded "
                + "as the anchor in the first line")
            .hasSize(1);
        assertThat(eventsByCommand(track, ShortMessage.NOTE_OFF))
            .as("the continuation still releases at its own end index, plus the unrelated note")
            .hasSize(2);
    }

    @Test
    void testMidiDoesNotSuppressNoteOnForAnUnrelatedNoteAtTheSameIndexInTheOtherLine() throws InvalidMidiDataException {
        var fixture = CrossLineFixture.create();
        var track = buildMidiTrack(fixture.firstLine());

        // The unrelated note sits at index 0 of the first line — the same numeric index as the
        // continuation note in the second line — but it is not part of the tie. The anchor
        // (index 1) strikes but must not release here: the tie sustains into the next line.
        assertThat(eventsByCommand(track, ShortMessage.NOTE_ON))
            .as("the unrelated note at index 0 strikes normally, and so does the anchor")
            .hasSize(2);
        assertThat(eventsByCommand(track, ShortMessage.NOTE_OFF))
            .as("only the unrelated note releases; the anchor sustains past this line's edge")
            .hasSize(1);
    }

    // ======================================================================
    // MusicEditOperations.flipStemDirection — the tie-partner walk
    // ======================================================================

    /**
     * Runs a stem-direction flip with {@link MessageCenter#post} stubbed out. The production
     * {@link songscribe.ui.selection.SelectionCoordinator} subscriber that reflects a
     * {@code SongDidChangeNotification} back onto the current selection assumes a fully wired
     * UI singleton graph that these tests do not build; silencing the bus keeps the test focused
     * on the tie-partner walk itself, which is what this test verifies directly.
     */
    private static void flipStemDirectionSilently(MusicEditOperations operations) {
        try (var _ = mockStatic(MessageCenter.class)) {
            operations.flipStemDirection();
        }
    }

    @Test
    void testStemDirectionFlipsTheAnchorHalfWithoutReachingTheOtherLine() {
        var fixture = CrossLineFixture.create();
        var originalAnchorDirection = fixture.anchor().getDirection();
        var originalUnrelatedInFirstLineDirection = fixture.unrelatedInFirstLine().getDirection();
        var originalEndDirection = fixture.end().getDirection();
        var originalUnrelatedInSecondLineDirection =
            fixture.unrelatedInSecondLine().getDirection();

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(fixture.firstLine());
        var operations = new MusicEditOperations(fixture.song(), coordinator);
        ReflectionTestHelper.selectNote(coordinator, ANCHOR_INDEX);
        flipStemDirectionSilently(operations);

        // Old behavior: the tie-partner walk reads tieStart/tieEnd off the tie's raw anchor/end
        // pair, so for a cross-line tie the range is inverted and the walk loops zero times —
        // here that happens not to matter, since the anchor's own selection already flips it
        // directly. What must hold either way is that resolving the tie's far bound (AFTER_LINE
        // here) does not misdirect the walk into some other element of this line, and that the
        // far half — genuinely in the other line — is never reached from here.
        assertThat(fixture.anchor().getDirection())
            .as("the anchor half of the cross-line tie flips like any selected note")
            .isEqualTo(originalAnchorDirection.opposite());
        assertThat(fixture.unrelatedInFirstLine().getDirection())
            .as("the unrelated, unselected note in the same line is untouched")
            .isEqualTo(originalUnrelatedInFirstLineDirection);
        assertThat(fixture.end().getDirection())
            .as("the tie's far half is in the other line and is not reached from here")
            .isEqualTo(originalEndDirection);
        assertThat(fixture.unrelatedInSecondLine().getDirection())
            .isEqualTo(originalUnrelatedInSecondLineDirection);
    }

    @Test
    void testStemDirectionFlipsTheEndHalfWithoutReachingTheOtherLine() {
        var fixture = CrossLineFixture.create();
        var originalEndDirection = fixture.end().getDirection();
        var originalUnrelatedInSecondLineDirection =
            fixture.unrelatedInSecondLine().getDirection();
        var originalAnchorDirection = fixture.anchor().getDirection();
        var originalUnrelatedInFirstLineDirection = fixture.unrelatedInFirstLine().getDirection();

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(fixture.secondLine());
        var operations = new MusicEditOperations(fixture.song(), coordinator);
        ReflectionTestHelper.selectNote(coordinator, END_INDEX);
        flipStemDirectionSilently(operations);

        assertThat(fixture.end().getDirection())
            .as("the end half of the cross-line tie flips like any selected note")
            .isEqualTo(originalEndDirection.opposite());
        assertThat(fixture.unrelatedInSecondLine().getDirection())
            .as("the unrelated, unselected note in the same line is untouched")
            .isEqualTo(originalUnrelatedInSecondLineDirection);
        assertThat(fixture.anchor().getDirection())
            .as("the tie's far half is in the other line and is not reached from here")
            .isEqualTo(originalAnchorDirection);
        assertThat(fixture.unrelatedInFirstLine().getDirection())
            .isEqualTo(originalUnrelatedInFirstLineDirection);
    }
}
