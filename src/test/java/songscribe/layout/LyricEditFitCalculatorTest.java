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
import static songscribe.dom.StaffElementFactory.crotchet;

import java.awt.Font;
import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.font.SourceSans3Font;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.util.MyFontUtils;

class LyricEditFitCalculatorTest extends UnitTest {

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    /**
     * Hyphen glyph wide enough that the reserved hyphen cell ({@code 1.75 ×} this) alone dwarfs
     * {@link #NARROW_LINE_SS}, while the space width stays zero. Isolates the syllabic differential:
     * a hyphenated (BEGIN) candidate overflows where a word-final (SINGLE) one comfortably fits.
     */
    private static final double WIDE_HYPHEN_WIDTH_SS = 100.0;
    private static final LyricRenderMetrics HYPHEN_HEAVY_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, WIDE_HYPHEN_WIDTH_SS, 0.0, 0.0);

    /** Wide enough that even a long syllable comfortably fits. */
    private static final double WIDE_LINE_SS = 500;

    /** Narrow enough that a wide syllable overflows but a bare line still fits. */
    private static final double NARROW_LINE_SS = 40;

    private static final int VERSE = 1;
    private static final int FIRST = 0;
    private static final int SECOND = 1;

    /** Character count of a syllable wide enough to overflow {@link #NARROW_LINE_SS}. */
    private static final int WIDE_LYRIC_CHARS = 300;
    private static final String WIDE_LYRIC = "w".repeat(WIDE_LYRIC_CHARS);
    private static final String SHORT_LYRIC = "la";

    private static Line lineWithCrotchets(int count) {
        var line = detachedLine();

        for (var i = 0; i < count; i++) {
            line.addElement(crotchet());
        }

        return line;
    }

    private static void setLyric(Line line, int index, String text) {
        line.getElement(index).setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, text, Lyric.Extend.NONE);
    }

    private static Lyric candidate(String text, Lyric.Syllabic syllabic, boolean compound, Lyric.Extend extend) {
        return new Lyric(VERSE, text, extend, syllabic, compound);
    }

    /** Resolves a {@code .musicxml} fixture on the classpath, unlike {@link #fixtureFile} which is {@code .mssw}-only. */
    private static File requireMusicXmlFixtureFile(String name) throws URISyntaxException {
        var url = LyricEditFitCalculatorTest.class.getClassLoader().getResource("fixtures/" + name + ".musicxml");

        if (url == null) {
            throw new IllegalArgumentException("Fixture not found: " + name);
        }

        return new File(url.toURI());
    }

    @BeforeAll
    static void installLyricsFont() {
        // The fixture's real geometry, produced by the actual app, is only tight enough to
        // pin the one-character/two-character boundary under the actual lyric font metrics.
        //
        // Reset the lazy cache before installing: an earlier test class in the same JVM may
        // already have resolved "Source Sans 3 SongScribe" while it was unregistered and cached
        // the substituted UI font, which the fixture's lyric-font declaration would then pick up
        // — silently measuring the line under the wrong metrics.
        MyFontUtils.resetFontCache();
        SourceSans3Font.install();
    }

    @Test
    void testEmptyLineFits() {
        assertThat(LyricEditFitCalculator.lineFits(detachedLine(), METRICS, NARROW_LINE_SS)).isTrue();
    }

    @Test
    void testBareLineFits() {
        assertThat(LyricEditFitCalculator.lineFits(lineWithCrotchets(2), METRICS, NARROW_LINE_SS)).isTrue();
    }

    @Test
    void testWideSyllableOverflowsNarrowLine() {
        var line = lineWithCrotchets(2);
        setLyric(line, FIRST, WIDE_LYRIC);

        assertThat(LyricEditFitCalculator.lineFits(line, METRICS, NARROW_LINE_SS)).isFalse();
    }

    @Test
    void testShortLyricEditFits() {
        var line = lineWithCrotchets(2);

        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, FIRST, candidate(SHORT_LYRIC, Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            METRICS, WIDE_LINE_SS)).isTrue();
    }

    @Test
    void testWideLyricEditIsRefusedOnNarrowLine() {
        var line = lineWithCrotchets(2);

        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, FIRST, candidate(WIDE_LYRIC, Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            METRICS, NARROW_LINE_SS)).isFalse();
    }

    @Test
    void testWideLyricEditFitsOnWideLine() {
        var line = lineWithCrotchets(2);

        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, FIRST, candidate(WIDE_LYRIC, Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            METRICS, WIDE_LINE_SS)).isTrue();
    }

    @Test
    void testHyphenatedCandidateReservesMoreThanWordFinal() {
        var line = lineWithCrotchets(2);

        // Same text, same width, same fixture — only the syllabic differs. A hyphenated (BEGIN)
        // syllable reserves the wide hyphen cell to its neighbour, a word-final (SINGLE) syllable
        // only the (zero) space, so the continues-status alone flips the fit verdict.
        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, FIRST, candidate(SHORT_LYRIC, Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            HYPHEN_HEAVY_METRICS, NARROW_LINE_SS)).isTrue();
        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, FIRST, candidate(SHORT_LYRIC, Lyric.Syllabic.BEGIN, false, Lyric.Extend.NONE),
            HYPHEN_HEAVY_METRICS, NARROW_LINE_SS)).isFalse();
    }

    @Test
    void testProbeRestoresExistingLyric() {
        var line = lineWithCrotchets(2);
        setLyric(line, FIRST, SHORT_LYRIC);

        LyricEditFitCalculator.lyricEditFits(
            line, FIRST, candidate(WIDE_LYRIC, Lyric.Syllabic.BEGIN, true, Lyric.Extend.START),
            METRICS, NARROW_LINE_SS);

        var restored = line.getElement(FIRST).getLyricForVerse(Lyric.FIRST_VERSE);

        assertThat(restored).as("probe did not restore the original lyric").isNotNull();

        assertThat(restored.text()).isEqualTo(SHORT_LYRIC);
        assertThat(restored.syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
        assertThat(restored.extend()).isEqualTo(Lyric.Extend.NONE);
        assertThat(restored.compound()).isFalse();
    }

    @Test
    void testProbeRestoresAbsentLyric() {
        var line = lineWithCrotchets(2);

        LyricEditFitCalculator.lyricEditFits(
            line, SECOND, candidate(SHORT_LYRIC, Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            METRICS, WIDE_LINE_SS);

        assertThat(line.getElement(SECOND).getLyricForVerse(Lyric.FIRST_VERSE)).isNull();
    }

    @Test
    void testProbeRestoresOriginalWhenCandidateApplicationThrows() {
        var line = lineWithCrotchets(2);
        setLyric(line, FIRST, SHORT_LYRIC);

        // A carrier extender may not carry text, so applying this candidate throws mid-probe. The
        // finally block must still restore the original lyric rather than leave the slot cleared.
        var illegalCandidate = new Lyric(VERSE, WIDE_LYRIC, Lyric.Extend.STOP, null, false);

        assertThatThrownBy(() -> LyricEditFitCalculator.lyricEditFits(
            line, FIRST, illegalCandidate, METRICS, WIDE_LINE_SS))
            .isInstanceOf(IllegalArgumentException.class);

        var restored = line.getElement(FIRST).getLyricForVerse(Lyric.FIRST_VERSE);

        assertThat(restored).as("probe did not restore the original lyric after the throw").isNotNull();

        assertThat(restored.text()).isEqualTo(SHORT_LYRIC);
    }

    /**
     * The {@code spring-spacing-infeasible-lyric} fixture is a golden case whose last (lyric-less)
     * note sits close to the edge of the staff margin once the preceding lyric-heavy syllables
     * ("Boundlessness", "really", "long", "lyrics") have claimed their spacing. Two characters on
     * that last note still fit; three do not.
     */
    @Test
    void testLastNoteRejectsAThreeCharacterLyricOnTightLine() throws Exception {
        var result = MusicXmlReader.read(requireMusicXmlFixtureFile("spring-spacing-infeasible-lyric"));
        var song = result.song();
        var lyricsFont = result.fonts().getLyricsFont();
        var lyricRenderMetrics = LyricRenderMetrics.forFont(lyricsFont);
        var line = song.getLines().getFirst();
        var lastNoteIndex = lastPitchedNoteIndex(line);
        var staffRightMarginSs = song.getLineWidthSs();

        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, lastNoteIndex, candidate("ab", Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            lyricRenderMetrics, staffRightMarginSs))
            .as("a two-character lyric on the last note must still fit")
            .isTrue();

        assertThat(LyricEditFitCalculator.lyricEditFits(
            line, lastNoteIndex, candidate("abc", Lyric.Syllabic.SINGLE, false, Lyric.Extend.NONE),
            lyricRenderMetrics, staffRightMarginSs))
            .as("a three-character lyric on the last note must be refused")
            .isFalse();
    }

    /** Finds the last pitched-note element, skipping the trailing barline element. */
    private static int lastPitchedNoteIndex(Line line) {
        for (var index = line.elementCount() - 1; index >= 0; index--) {
            if (line.getElement(index).getType().isPitchedNote()) {
                return index;
            }
        }

        throw new IllegalArgumentException("line has no pitched note");
    }
}
