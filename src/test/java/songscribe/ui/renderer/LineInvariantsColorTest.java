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

package songscribe.ui.renderer;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.SongFactory;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.lint.LintFinding;
import songscribe.lint.LintMark;
import songscribe.lint.LintReport;
import songscribe.lint.LintRule;
import songscribe.lint.Violation;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The color {@link LineInvariants#getElementColor} gives an element that the lint report flags:
 * the lint color, unless the element is selected, in which case the selection color, unless it is
 * playing, in which case the playing color.
 */
class LineInvariantsColorTest extends UnitTest {

    private static final int FLAGGED_ELEMENT_INDEX = 0;

    private static final int LINE_INDEX = 0;

    private static final String FLAGGED_NOTATION = "0";

    private static final String VIOLATION_TEXT = "flagged for the test";

    private enum Selection { SELECTED, NOT_SELECTED }

    private enum Playback { PLAYING, NOT_PLAYING }

    private record ColorCase(String description, Selection selection, Playback playback, Supplier<Color> expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    @BeforeAll
    void installTheme() throws Exception {
        installFlatLafDefaults();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("colorCases")
    void testAFlaggedElementIsColoredByTheFirstStepThatHolds(ColorCase testCase) {
        assertThat(List.of(ScoreView.getLintColor(), ScoreView.getSelectionColor(), ScoreView.getPlayingNoteColor()))
            .as("the three colors can be told apart")
            .doesNotHaveDuplicates();

        var invariants = invariantsWithFlaggedElement(testCase.selection(), testCase.playback());

        assertThat(invariants.getElementColor(FLAGGED_ELEMENT_INDEX)).isEqualTo(testCase.expected().get());
    }

    static Stream<ColorCase> colorCases() {
        return Stream.of(
            new ColorCase("a flagged element that is not selected gets the lint color",
                Selection.NOT_SELECTED, Playback.NOT_PLAYING, ScoreView::getLintColor),
            new ColorCase("the same element selected gets the selection color",
                Selection.SELECTED, Playback.NOT_PLAYING, ScoreView::getSelectionColor),
            new ColorCase("playing wins over both",
                Selection.SELECTED, Playback.PLAYING, ScoreView::getPlayingNoteColor)
        );
    }

    private static LineInvariants invariantsWithFlaggedElement(Selection selection, Playback playback) {
        var song = SongFactory.compactSong(Key.NO_ACCIDENTALS, FLAGGED_NOTATION);
        var line = song.getLine(LINE_INDEX);
        var flagged = line.getElement(FLAGGED_ELEMENT_INDEX);
        var violation = new Violation(Strings.LINT_REPEATS_UNBALANCED, VIOLATION_TEXT, new LintMark.Flagged(flagged));
        var report = new LintReport(List.of(new LintFinding(LintRule.BALANCED_REPEATS, violation)));

        var builder = LineInvariants.builder(song, mock(DocumentFontsHolder.class), ViewScale.IDENTITY)
            .setCurrentLine(line)
            .setLineIndex(LINE_INDEX)
            .setLayoutResult(mock(LayoutResult.class))
            .setLyricRenderMetrics(mock(LyricRenderMetrics.class))
            .setLintReport(report);

        if (selection == Selection.SELECTED) {
            builder.setSelectionProvider(selectionCoveringEveryElement());
        }

        if (playback == Playback.PLAYING) {
            builder.setPlaying(new LineInvariants.Playing(FLAGGED_ELEMENT_INDEX, null));
        }

        return builder.build();
    }

    private static LineComponent.SelectionProvider selectionCoveringEveryElement() {
        var provider = mock(LineComponent.SelectionProvider.class);
        when(provider.isElementRangeSelected(anyInt(), anyInt())).thenReturn(true);

        return provider;
    }
}
