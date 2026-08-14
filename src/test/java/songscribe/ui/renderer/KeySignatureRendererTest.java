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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import module java.desktop;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.KeyChange;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.dom.StaffElementFactory;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;

/**
 * Exercises the contract of {@link KeySignatureRenderer}, which paints a key signature in a
 * staff header and a cautionary key change at the end of a line.
 *
 * <p><b>What is drawn belongs to {@link KeyChange} and is not retested here.</b> The
 * cancellation policy — which accidentals a change from one key to another produces, in what
 * order, at what staff positions — is {@code KeyChangeTest}'s. What this class asserts is that
 * the renderer paints exactly the run it is handed, spaced by exactly the gaps and advances
 * that run carries, and puts it in the right place.
 *
 * <p><b>The drawing invariant</b> — for a run of accidentals, the pen offset from the first
 * glyph to the {@code i}th is the sum of the intervening advances and leading gaps that
 * {@link KeyChange#advanceSs} and {@link KeyChange.DrawnAccidental#leadingGapSs} report. One
 * property covering what used to be three cases: accidentals nesting at their glyph width, the
 * cancellation held apart from the key that follows it, and naturals kerned apart from each
 * other. Driven from a table with a row per distinct run shape — one group, two groups,
 * naturals only, and the widest run either side can produce.
 *
 * <p><b>Nothing to draw</b> — a header for C major and a change between two equal keys both
 * touch the graphics context not at all, which is asserted for every key
 * {@link Key#allSignatures()} names rather than for one example.
 *
 * <p><b>Position</b> — the two sides of the boundary
 * {@link LayoutResult#overflowsStaffWidth()} creates: on a line that fits, the run's right edge
 * lands on the line width less {@link KeyChange#RIGHT_MARGIN_SS}; on a line that overflows, the
 * run's first glyph starts one line rest past the rightmost element edge, and the margin is not
 * consulted at all.
 *
 * <p>Staff-position placement (the {@code y} of each glyph) has no case here: it is
 * {@code KeyChange}'s table, read back unchanged, and the vertical mapping through
 * {@code Staff.spToSs} is that class's promise rather than this one's.
 */
class KeySignatureRendererTest extends UnitTest {

    private static final KeySignatureRenderer RENDERER = KeySignatureRenderer.getInstance();

    // Arbitrary line width for key-change tests
    private static final double LINE_WIDTH_SS = 80.0;

    // Arbitrary X the key signature is laid out from
    private static final double KEY_SIGNATURE_X_SS = 4.0;

    // A staff too narrow for the note count below, so its line is laid out overflowing
    private static final double NARROW_STAFF_RIGHT_MARGIN_SS = 20.0;
    private static final int OVERFLOWING_NOTE_COUNT = 20;

    /** Drawing X is rounded to float, so compare in staff spaces with a slack of this much. */
    private static final double TOLERANCE_SS = 1e-4;

    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);
    private static final Key TWO_SHARPS = new Key(KeyType.SHARPS, 2);
    private static final Key THREE_SHARPS = new Key(KeyType.SHARPS, 3);
    private static final Key FOUR_SHARPS = new Key(KeyType.SHARPS, 4);
    private static final Key TWO_FLATS = new Key(KeyType.FLATS, 2);
    private static final Key FIVE_FLATS = new Key(KeyType.FLATS, 5);
    private static final Key ALL_SHARPS = new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT);
    private static final Key ALL_FLATS = new Key(KeyType.FLATS, Key.MAX_ACCIDENTAL_COUNT);

    /** A change from {@code previous} to {@code next}, named for the run shape it produces. */
    private record RunCase(String description, Key previous, Key next) {}

    static Stream<RunCase> runCases() {
        return Stream.of(
            new RunCase("one group: same type, more accidentals", TWO_SHARPS, FOUR_SHARPS),
            new RunCase("one group: same type, fewer accidentals", FOUR_SHARPS, TWO_SHARPS),
            new RunCase("one group: out of C major", C_MAJOR, FIVE_FLATS),
            new RunCase("naturals only: into C major", THREE_SHARPS, C_MAJOR),
            new RunCase("two groups: sharps to flats", THREE_SHARPS, TWO_FLATS),
            new RunCase("two groups at their widest", ALL_FLATS, ALL_SHARPS)
        );
    }

    static Stream<Key> allKeys() {
        return Key.allSignatures().stream();
    }

    static Stream<Key> keysWithAccidentals() {
        return allKeys().filter(key -> key.keyType() != KeyType.NONE);
    }

    /**
     * Builds a real {@link Graphics2D} from a headless buffered image so that
     * font/color/transform operations inside the renderer do not throw.
     */
    private static Graphics2D realG2() {
        var img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    private static LineInvariants invariants() {
        return RenderContextTestHelper.newContext(new Song()).build();
    }

    // ==========================================================================
    // Nothing to draw
    // ==========================================================================

    @Test
    void testRenderDrawsNothingForCMajor() {
        var g2 = mock(Graphics2D.class);

        RENDERER.render(invariants(), ElementFrame.LINE_LEVEL, new KeySignature(C_MAJOR), g2);

        verifyNoInteractions(g2);
    }

    @ParameterizedTest
    @MethodSource("allKeys")
    void testRenderKeyChangeDrawsNothingWhenTheKeyDoesNotChange(Key key) {
        var g2 = mock(Graphics2D.class);

        RENDERER.renderKeyChange(g2, key, key, LINE_WIDTH_SS, invariants());

        verifyNoInteractions(g2);
    }

    // ==========================================================================
    // The drawing invariant — the run is painted with the layout it carries
    // ==========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("runCases")
    void testTheDrawnRunFollowsTheLayoutTheKeyChangeCarries(RunCase testCase) {
        var accidentals = KeyChange.accidentals(testCase.previous(), testCase.next());
        var g2spy = spy(realG2());

        RENDERER.renderKeyChange(
            g2spy, testCase.previous(), testCase.next(), LINE_WIDTH_SS, invariants());

        var drawn = capturedDraws(g2spy, accidentals.size());

        assertThat(drawn.glyphs())
            .describedAs("glyphs painted, in order")
            .containsExactlyElementsOf(
                accidentals.stream().map(a -> a.glyph().asString()).toList());

        var expectedOffsetSs = 0.0;

        for (var i = 1; i < accidentals.size(); i++) {
            expectedOffsetSs +=
                KeyChange.advanceSs(accidentals, i - 1) + accidentals.get(i).leadingGapSs();

            assertThat(drawn.xValues().get(i) - drawn.xValues().getFirst())
                .describedAs("pen offset from accidental 0 to %d", i)
                .isCloseTo(expectedOffsetSs, within(TOLERANCE_SS));
        }
    }

    @ParameterizedTest
    @MethodSource("keysWithAccidentals")
    void testTheHeaderRunStartsAtTheKeySignaturesOwnX(Key key) {
        var accidentals = KeyChange.signatureAccidentals(key);
        var g2spy = spy(realG2());
        var keySig = new KeySignature(key);
        keySig.setPosition(KEY_SIGNATURE_X_SS, 0);

        RENDERER.render(invariants(), ElementFrame.LINE_LEVEL, keySig, g2spy);

        assertThat(capturedDraws(g2spy, accidentals.size()).xValues().getFirst())
            .isCloseTo(KEY_SIGNATURE_X_SS, within(TOLERANCE_SS));
    }

    // ==========================================================================
    // Position — the two sides of the overflow boundary
    // ==========================================================================

    @Test
    void testOnALineThatFitsTheRunEndsAtTheLineWidthLessTheRightMargin() {
        var accidentals = KeyChange.accidentals(THREE_SHARPS, TWO_FLATS);
        var g2spy = spy(realG2());

        RENDERER.renderKeyChange(g2spy, THREE_SHARPS, TWO_FLATS, LINE_WIDTH_SS, invariants());

        var xValues = capturedDraws(g2spy, accidentals.size()).xValues();
        var lastRightEdgeSs =
            xValues.getLast() + KeyChange.advanceSs(accidentals, accidentals.size() - 1);

        assertThat(lastRightEdgeSs)
            .isCloseTo(LINE_WIDTH_SS - KeyChange.RIGHT_MARGIN_SS, within(TOLERANCE_SS));
    }

    /**
     * An overflowing line has already passed the margin, so the run is placed off the content
     * instead: one line rest past the rightmost element edge, wherever that has landed. The
     * arrangement checks that the content really did pass the margin, since a run that had not
     * would be right-alignable and the branch would never be reached.
     */
    @Test
    void testOnAnOverflowingLineTheRunStartsOneLineRestPastTheContent() {
        var song = minimalSongMock();
        var layoutResult = overflowingLayout(song);
        var contentRightEdgeSs = rightmostColumnEdgeSs(layoutResult);

        assertThat(contentRightEdgeSs)
            .describedAs("the overflowing content must end past the staff margin")
            .isGreaterThan(NARROW_STAFF_RIGHT_MARGIN_SS);

        var invariants = RenderContextTestHelper.newContext(song)
            .setLayoutResult(layoutResult)
            .build();
        var g2spy = spy(realG2());

        RENDERER.renderKeyChange(
            g2spy, THREE_SHARPS, TWO_FLATS, NARROW_STAFF_RIGHT_MARGIN_SS, invariants);

        var accidentals = KeyChange.accidentals(THREE_SHARPS, TWO_FLATS);
        var expectedXSs = contentRightEdgeSs + song.getDefaultRestLengthSs();

        assertThat(capturedDraws(g2spy, accidentals.size()).xValues().getFirst())
            .isCloseTo(expectedXSs, within(TOLERANCE_SS));
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    /**
     * Lays out a line of {@code song} holding more notes than a
     * {@value #NARROW_STAFF_RIGHT_MARGIN_SS} staff space staff can hold, and asserts that it
     * really does overflow — the flag is the state under test, and a line that quietly fit
     * would leave the branch unexercised.
     */
    private static LayoutResult overflowingLayout(Song song) {
        var line = detachedLine(song);

        for (var i = 0; i < OVERFLOWING_NOTE_COUNT; i++) {
            line.addElement(StaffElementFactory.crotchet());
        }

        var engine = new LayoutEngine(
            LyricRenderMetrics.forFont(DocumentFonts.defaultFonts().getFont(FontKey.LYRICS)),
            NARROW_STAFF_RIGHT_MARGIN_SS,
            DocumentFonts.defaultFonts());
        var layoutResult = engine.layout(line, false);

        assertThat(layoutResult.overflowsStaffWidth())
            .describedAs("%d notes must not fit a %s staff space staff",
                OVERFLOWING_NOTE_COUNT, NARROW_STAFF_RIGHT_MARGIN_SS)
            .isTrue();

        return layoutResult;
    }

    private static double rightmostColumnEdgeSs(LayoutResult layoutResult) {
        return layoutResult.getElementColumns().values().stream()
            .mapToDouble(ElementColumn::getRightEdgeXSs)
            .max()
            .orElseThrow();
    }

    /** The glyphs and pen X positions of exactly {@code expectedCount} draws, in draw order. */
    private record Draws(List<String> glyphs, List<Double> xValues) {}

    private static Draws capturedDraws(Graphics2D g2spy, int expectedCount) {
        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        var xCaptor = ArgumentCaptor.forClass(Float.class);

        verify(g2spy, times(expectedCount))
            .drawString(glyphCaptor.capture(), xCaptor.capture(), anyFloat());

        return new Draws(
            glyphCaptor.getAllValues(),
            xCaptor.getAllValues().stream().map(Float::doubleValue).toList());
    }
}
