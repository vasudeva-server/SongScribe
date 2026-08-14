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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.singleBarline;

import java.awt.Font;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeyChange;
import songscribe.dom.KeySignatureElement;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;

/**
 * Exercises the contract of {@link KeyEditFitCalculator}: that each query answers for the
 * <em>whole</em> edit, and that the verdict is one the committed layout keeps.
 *
 * <p><b>The four places a key change claims space</b> are covered one at a time, each isolated by a
 * fixture in which it is the only cost, and each pinned at the right <em>magnitude</em> — the
 * boundary margin is found by bisection rather than written down, so a line that fits before the
 * edit is shown to stop fitting after it, and to fit again once the margin grows by exactly what
 * the edit costs. The cautionary at the previous line's end, the re-keyed line's own header, the
 * inheritance chain past it, and the spliced key signature column each get their own case.
 *
 * <p><b>The inheritance chain</b> is covered at its stopping rule as well as along its length: a
 * line that establishes its own key ends the walk, so a line past it that could not hold the new
 * key does not refuse the edit.
 *
 * <p><b>The editor's barline</b> — inserted when the chosen position does not already open a
 * measure — is covered on both sides of the position invariant, each measured against the boundary
 * of the same splice with the barline omitted. The index precondition is covered at both bounds.
 *
 * <p><b>Agreement</b> — that a verdict of "fits" is one the committed layout keeps, asserted as a
 * property over a set of margins spanning both verdicts rather than by pinning widths.
 *
 * <p><b>The divergence from {@link LyricEditFitCalculator}</b> — that an already-overflowing line
 * refuses a key edit even when the edit draws nothing at all. That is the decision the class doc
 * records, pinned so matching the lyric precedent cannot quietly reverse it.
 */
class KeyEditFitCalculatorTest extends UnitTest {

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** Zero-width lyric metrics: every fixture here is lyric-less, so no syllable floor can bind. */
    private static final LyricRenderMetrics METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /** Wide enough that every fixture here fits, whatever key or key signature is added. */
    private static final double WIDE_MARGIN_SS = 400.0;

    /** Enough bisection steps to place a boundary margin well inside {@link #TOLERANCE_SS}. */
    private static final int BISECTION_STEPS = 60;
    private static final double TOLERANCE_SS = 1e-6;

    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);
    private static final Key ONE_SHARP = new Key(KeyType.SHARPS, 1);

    /** The widest signature there is, so a header or cautionary drawn from it is the costliest. */
    private static final Key WIDE_KEY = new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT);

    private static final int LINE_NOTE_COUNT = 4;

    /** An insertion index whose preceding element is a note, so the editor must add a barline. */
    private static final int INDEX_AFTER_A_NOTE = 2;

    /** More notes than {@link #CROWDED_MARGIN_SS} can hold even fully compressed. */
    private static final int CROWDED_NOTE_COUNT = 24;
    private static final double CROWDED_MARGIN_SS = 20.0;

    // ==========================================================================
    // Fixtures
    // ==========================================================================

    /** A real song of {@code lineCount} lines, so the inheritance chain is the document's own. */
    private static Song songOf(int lineCount) {
        var song = new Song();

        for (var index = 1; index < lineCount; index++) {
            song.addLine(new Line(song));
        }

        setKey(song.getLine(0), C_MAJOR);

        return song;
    }

    private static void setKey(Line line, Key key) {
        line.getSong().withModification(() -> line.setKey(key));
    }

    private static void fillWithCrotchets(Line line, int count) {
        line.getSong().withoutMutationTracking(() -> {
            for (var index = 0; index < count; index++) {
                line.addElement(crotchet());
            }
        });
    }

    private static void setLineWidth(Song song, double lineWidthSs) {
        song.withModification(() -> song.setLineWidthSs(lineWidthSs));
    }

    /** A detached line of {@code count} crotchets, backed by a song mock whose width is stubbable. */
    private static Line detachedLineWithCrotchets(int count) {
        var line = detachedLine();

        for (var index = 0; index < count; index++) {
            line.addElement(crotchet());
        }

        return line;
    }

    /** Runs a key-change query at {@code marginSs} by giving the line's song that width. */
    private static boolean lineKeyChangeFitsAt(Line line, Key key, double marginSs) {
        var song = line.getSong();

        if (song.isMutationTrackingSuspended()) {
            when(song.getLineWidthSs()).thenReturn(marginSs);
        } else {
            setLineWidth(song, marginSs);
        }

        return KeyEditFitCalculator.lineKeyChangeFits(line, key, METRICS);
    }

    /**
     * Bisects for the narrowest margin {@code fits} accepts. The predicate is monotone in the
     * margin — a wider line never stops fitting — so the boundary is well defined; asserting both
     * ends first is what makes a fixture that never flips fail as itself rather than as a wrong
     * boundary.
     */
    private static double narrowestFittingMarginSs(DoublePredicate fits) {
        assertThat(fits.test(WIDE_MARGIN_SS)).as("the search needs an upper bound that fits").isTrue();
        assertThat(fits.test(0.0)).as("the search needs a lower bound that does not fit").isFalse();

        var doesNotFitSs = 0.0;
        var fitsSs = WIDE_MARGIN_SS;

        for (var step = 0; step < BISECTION_STEPS; step++) {
            var midSs = (doesNotFitSs + fitsSs) / 2;

            if (fits.test(midSs)) {
                fitsSs = midSs;
            } else {
                doesNotFitSs = midSs;
            }
        }

        return fitsSs;
    }

    // ==========================================================================
    // lineKeyChangeFits — the header of the line that takes the key
    // ==========================================================================

    /**
     * A wider key signature in the header pushes the whole chain right, so a line sitting exactly
     * at its own boundary can no longer take one — and takes it again once the margin grows by
     * exactly how far the header moved the first element. The fixture is a one-line song, so the
     * header is the only thing the change can cost.
     */
    @Test
    void testAWiderHeaderCostsTheLineExactlyHowFarItMovesTheFirstElement() {
        var song = songOf(1);
        var line = song.getLine(0);
        fillWithCrotchets(line, LINE_NOTE_COUNT);

        var boundarySs = narrowestFittingMarginSs(marginSs -> lineKeyChangeFitsAt(line, C_MAJOR, marginSs));
        var headerGrowthSs = HorizontalSpacingCalculator.calculateFirstElementXSs(WIDE_KEY)
            - HorizontalSpacingCalculator.calculateFirstElementXSs(C_MAJOR);

        assertThat(lineKeyChangeFitsAt(line, WIDE_KEY, boundarySs))
            .as("at the boundary the line has no room for a wider header")
            .isFalse();
        assertThat(lineKeyChangeFitsAt(line, WIDE_KEY, boundarySs + headerGrowthSs + TOLERANCE_SS))
            .as("widened by how far the header moves the first element, the same line takes it")
            .isTrue();
    }

    // ==========================================================================
    // lineKeyChangeFits — the cautionary on the line before
    // ==========================================================================

    /**
     * Changing a line's key puts a cautionary at the end of the line before it, drawn into that
     * line's trailing gap. The reservation is the larger of the line rest and the cautionary's run,
     * so the boundary moves by the difference between them, not by the whole run. The changed line
     * is left empty, so the cautionary is the only thing the change can cost.
     */
    @Test
    void testACautionaryCostsThePreviousLineTheRunItAddsPastTheLineRest() {
        var song = songOf(2);
        var previousLine = song.getLine(0);
        var line = song.getLine(1);
        fillWithCrotchets(previousLine, LINE_NOTE_COUNT);

        var boundarySs = narrowestFittingMarginSs(marginSs -> lineKeyChangeFitsAt(line, C_MAJOR, marginSs));
        var cautionaryRunSs =
            KeyChange.widthSs(previousLine.keyAtEndOfLine(), WIDE_KEY) + KeyChange.RIGHT_MARGIN_SS;
        var lineRestSs = song.getDefaultRestLengthSs();

        assertThat(cautionaryRunSs)
            .as("the cautionary must exceed the line rest, or it costs the previous line nothing")
            .isGreaterThan(lineRestSs);
        assertThat(lineKeyChangeFitsAt(line, WIDE_KEY, boundarySs))
            .as("at the boundary the previous line has no room for the cautionary")
            .isFalse();
        assertThat(lineKeyChangeFitsAt(line, WIDE_KEY, boundarySs + (cautionaryRunSs - lineRestSs) + TOLERANCE_SS))
            .as("widened by what the cautionary claims past the line rest, the change is accepted")
            .isTrue();
    }

    // ==========================================================================
    // lineKeyChangeFits — the inheritance chain past the changed line
    // ==========================================================================

    /**
     * @param description         the row's display name
     * @param middleLineKey       the key the middle line establishes, or null to leave it inheriting
     * @param changeReachesTheEnd whether the change re-keys the last line, and so must be refused
     *                            because that line cannot hold the wider header
     */
    record ChainCase(String description, @Nullable Key middleLineKey, boolean changeReachesTheEnd) {}

    static Stream<ChainCase> chainCases() {
        return Stream.of(
            new ChainCase("an inheriting middle line passes the change along", null, true),
            new ChainCase("a middle line with its own key stops the change", ONE_SHARP, false));
    }

    /**
     * A change to one line's key re-headers every line that inherits from it, so a line further
     * down that cannot hold the wider header refuses the change — and a line between them that
     * establishes its own key ends the chain, leaving that line untouched.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("chainCases")
    void testAKeyChangeIsRefusedByEveryLineItRekeys(ChainCase testCase) {
        var song = songOf(3);
        var lastLine = song.getLine(2);
        fillWithCrotchets(lastLine, LINE_NOTE_COUNT);

        if (testCase.middleLineKey() != null) {
            setKey(song.getLine(1), testCase.middleLineKey());
        }

        // The narrowest margin the last line holds its own header in. A wider one would not
        // separate the two rows, since both would fit whatever the header.
        var boundarySs = narrowestFittingMarginSs(
            marginSs -> lineKeyChangeFitsAt(lastLine, lastLine.getRunningKey(), marginSs));

        assertThat(lineKeyChangeFitsAt(song.getLine(0), WIDE_KEY, boundarySs + TOLERANCE_SS))
            .isEqualTo(!testCase.changeReachesTheEnd());
    }

    // ==========================================================================
    // lineKeyChangeFits — the already-overflowing divergence
    // ==========================================================================

    /**
     * The deliberate divergence from {@link LyricEditFitCalculator}, decided by the domain owner:
     * key edits get no already-overflowing escape. Even a change that draws nothing new at all —
     * re-stating the key the line is already in — is refused on a line that already overflows, so
     * a reader matching the lyric precedent cannot quietly reverse the decision.
     */
    @Test
    void testAnAlreadyOverflowingLineRefusesEvenAKeyChangeThatDrawsNothing() {
        var line = detachedLineWithCrotchets(CROWDED_NOTE_COUNT);
        var noChangeKey = line.getRunningKey();

        assertThat(lineKeyChangeFitsAt(line, noChangeKey, WIDE_MARGIN_SS))
            .as("with room the fixture accepts this edit, so the refusal below is the overflow")
            .isTrue();
        assertThat(lineKeyChangeFitsAt(line, noChangeKey, CROWDED_MARGIN_SS))
            .as("an already-overflowing line refuses key edits, narrowing ones included")
            .isFalse();
    }

    // ==========================================================================
    // keySignatureFits
    // ==========================================================================

    /**
     * @param line                      the fixture, built fresh per row
     * @param fitsAtTheBarelessBoundary whether the real check still accepts at the margin the same
     *                                  splice needs without a barline — true exactly when the
     *                                  position already opens a measure and no barline is added
     */
    record BarlineInclusionCase(
        String description, Supplier<Line> line, boolean fitsAtTheBarelessBoundary) {}

    static Stream<BarlineInclusionCase> barlineInclusionCases() {
        return Stream.of(
            new BarlineInclusionCase(
                "a position after a note is measured with the barline the editor adds",
                () -> detachedLineWithCrotchets(LINE_NOTE_COUNT),
                false),
            new BarlineInclusionCase(
                "a position that already opens a measure adds no barline",
                KeyEditFitCalculatorTest::lineWithBarlineBeforeTheInsertionPoint,
                true));
    }

    /** {@link #detachedLineWithCrotchets} with a barline at {@link #INDEX_AFTER_A_NOTE} − 1. */
    private static Line lineWithBarlineBeforeTheInsertionPoint() {
        var line = detachedLineWithCrotchets(LINE_NOTE_COUNT);
        line.setElement(INDEX_AFTER_A_NOTE - 1, singleBarline());

        return line;
    }

    /**
     * The narrowest margin the same splice fits in with the barline left out — what a check that
     * forgot the editor's barline would measure.
     */
    private static double barelessBoundaryMarginSs(Line line, int insertionIndex, Key key) {
        var withoutBarline = InsertionSpacingCalculator.calculateFragmentInsertion(
            line,
            List.of(KeySignatureElement.forMeasurement(key, line.keyAt(insertionIndex - 1))),
            insertionIndex,
            null,
            null,
            METRICS);

        return narrowestFittingMarginSs(withoutBarline::fitsWithinLine);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("barlineInclusionCases")
    void testKeySignatureFitsMeasuresTheBarlineTheEditorInsertsAlongsideIt(BarlineInclusionCase testCase) {
        var line = testCase.line().get();
        var boundarySs = barelessBoundaryMarginSs(line, INDEX_AFTER_A_NOTE, WIDE_KEY);

        when(line.getSong().getLineWidthSs()).thenReturn(boundarySs + TOLERANCE_SS);

        assertThat(KeyEditFitCalculator.keySignatureFits(line, INDEX_AFTER_A_NOTE, WIDE_KEY, METRICS))
            .isEqualTo(testCase.fitsAtTheBarelessBoundary());
    }

    /** A key signature is never the first element on a line, and never lands past its last. */
    @ParameterizedTest
    @MethodSource("outOfBoundsInsertionIndices")
    void testKeySignatureFitsRejectsAnIndexTheInvariantForbids(int insertionIndex) {
        var line = detachedLineWithCrotchets(LINE_NOTE_COUNT);

        assertThatThrownBy(() -> KeyEditFitCalculator.keySignatureFits(line, insertionIndex, WIDE_KEY, METRICS))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    static Stream<Integer> outOfBoundsInsertionIndices() {
        return Stream.of(0, LINE_NOTE_COUNT + 1);
    }

    // ==========================================================================
    // The pre-check and the committed layout agree
    // ==========================================================================

    /** Margins spanning both verdicts, so the property below is never vacuous. */
    static Stream<Double> agreementMarginsSs() {
        return Stream.of(12.0, 16.0, 20.0, 26.0, 34.0, 50.0);
    }

    /**
     * The agreement guarantee itself: every margin the pre-check accepts is one the committed
     * layout places without overflowing. Asserted as a property over a set of margins rather than
     * by pinning widths, and the set is checked to span both verdicts so a run in which nothing is
     * accepted (or nothing refused) fails rather than passing empty.
     */
    @Test
    void testEveryAcceptedKeySignatureLaysOutWithoutOverflowing() {
        var acceptedCount = 0;
        var refusedCount = 0;

        for (var marginSs : agreementMarginsSs().toList()) {
            var line = detachedLineWithCrotchets(LINE_NOTE_COUNT);

            when(line.getSong().getLineWidthSs()).thenReturn(marginSs);

            if (!KeyEditFitCalculator.keySignatureFits(line, INDEX_AFTER_A_NOTE, WIDE_KEY, METRICS)) {
                refusedCount++;
                continue;
            }

            acceptedCount++;
            commitKeySignature(line, INDEX_AFTER_A_NOTE, WIDE_KEY);

            var layout = new LayoutEngine(METRICS, marginSs, DocumentFonts.defaultFonts()).layout(line, false);

            assertThat(layout.overflowsStaffWidth())
                .as("the pre-check accepted this edit at %s ss, so the layout must place it", marginSs)
                .isFalse();
        }

        assertThat(acceptedCount).as("the margins must include some the check accepts").isPositive();
        assertThat(refusedCount).as("the margins must include some the check refuses").isPositive();
    }

    /** Applies the edit the pre-check measured: the key signature, behind the barline it needs. */
    private static void commitKeySignature(Line line, int insertionIndex, Key key) {
        var precedingType = line.getElement(insertionIndex - 1).getType();
        var index = insertionIndex;

        if (!precedingType.isBarLine() && !precedingType.isRepeat()) {
            line.addElement(index, ElementType.SINGLE_BARLINE.newInstance());
            index++;
        }

        line.addElement(index, new KeySignatureElement(key));
    }
}
