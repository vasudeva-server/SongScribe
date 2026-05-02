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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;

class LayoutResultTest extends UnitTest {

    // T3a: Builder.setClef() round-trips through getClef()
    @Test
    void testBuilderClefRoundTrip() {
        var clef = new Clef();
        var result = LayoutResult.builder()
            .setClef(clef)
            .build();

        assertThat(result.getClef()).isSameAs(clef);
    }

    // T3b: Builder.setKeySignature() round-trips through getKeySignature()
    @Test
    void testBuilderKeySignatureRoundTrip() {
        var keySig = new KeySignature(KeyType.FLATS, 2);
        var result = LayoutResult.builder()
            .setKeySignature(keySig)
            .build();

        assertThat(result.getKeySignature()).isSameAs(keySig);
    }

    // T3c: Builder without setClef/setKeySignature returns null for both
    @Test
    void testBuilderDefaultsToNullHeaderElements() {
        var result = LayoutResult.builder().build();

        assertThat(result.getClef()).isNull();
        assertThat(result.getKeySignature()).isNull();
    }

    // T1: getLyricAnchor returns box-anchored geometry when verse-1 box exists
    @Test
    void testGetLyricAnchorBoxAnchored() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(3.0, 2.0, 1, "do");
        var layoutResult = LayoutResult.builder().addLyricBox(element, box).build();
        var metrics = testSongMetrics();
        var anchor = layoutResult.getLyricAnchor(element, metrics);

        assertThat(anchor.centerXSs()).isCloseTo(4.0, within(TOLERANCE));  // 3.0 + 2.0/2
        assertThat(anchor.baselineYSs()).isCloseTo(metrics.verseYSsInLine(1), within(TOLERANCE));
    }

    // T2: getLyricAnchor returns column-anchored geometry when no boxes
    @Test
    void testGetLyricAnchorColumnAnchored() {
        var element = ElementType.CROTCHET.newInstance();
        var column = testColumnAt(element, 5.0);
        var layoutResult = LayoutResult.builder().putElementColumn(element, column).build();
        var metrics = testSongMetrics();
        var anchor = layoutResult.getLyricAnchor(element, metrics);

        assertThat(anchor.centerXSs()).isCloseTo(5.0 + Engraving.NOTE_HEAD_WIDTH_SS / 2.0, within(TOLERANCE));
        assertThat(anchor.baselineYSs()).isCloseTo(metrics.verseYSsInLine(1), within(TOLERANCE));
    }

    // T3: getLyricAnchor Y matches verseYSsInLine(1) exactly
    @Test
    void testGetLyricAnchorYMatchesVerseBaseline() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.0, 1, "re");
        var layoutResult = LayoutResult.builder().addLyricBox(element, box).build();
        // maxAboveStaffSs=1, STAFF_HEIGHT_SS=4, maxBelowContentSs=0.5,
        // staffToLyricsGapSs=0.25, lyricsLineHeightSs=2.0 → verseYSsInLine(1) = 5+0.5+0.25+0 = 5.75
        var metrics = testSongMetrics();
        var anchor = layoutResult.getLyricAnchor(element, metrics);

        assertThat(anchor.baselineYSs()).isCloseTo(5.75, within(TOLERANCE));
    }

    // T4: getLyricAnchor throws IllegalStateException when neither boxes nor column exist
    @Test
    void testGetLyricAnchorThrowsWhenNoBoxOrColumn() {
        var element = ElementType.CROTCHET.newInstance();
        var layoutResult = LayoutResult.builder().build();
        var metrics = testSongMetrics();

        assertThatThrownBy(() -> layoutResult.getLyricAnchor(element, metrics))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testHitTestLyricHitsInsideBounds() {
        var song = new songscribe.music.Song();
        var line = song.getLine(0);
        var element = ElementType.CROTCHET.newInstance();
        song.withoutMutationTracking(() -> line.addElement(0, element));
        var box = new LyricBoxLayout(3.0, 2.0, 1, "do");
        var baselineYSs = 1.0 + StaffExtents.STAFF_HEIGHT_SS + 0.5
            + SongLayoutMetricsBuilder.LYRICS_ROW_MARGIN_SS
            + ScaleContext.getInstance().fontAscentSs(song.getLyricsFont());
        var layoutResult = LayoutResult.builder()
            .setAboveStaffSs(1.0)
            .setBelowContentSs(0.5)
            .addLyricBox(element, box)
            .build();

        var hit = layoutResult.hitTestLyric(
            line,
            new Point2D.Double(
                ScaleContext.getInstance().toRoundedPixels(4.0),
                ScaleContext.getInstance().toRoundedPixels(baselineYSs)
            )
        );

        var nonNullHit = Objects.requireNonNull(hit);
        assertThat(nonNullHit.element()).isSameAs(element);
        assertThat(nonNullHit.verse()).isEqualTo(1);
    }

    @Test
    void testHitTestLyricMissesOutsideBounds() {
        var song = new songscribe.music.Song();
        var line = song.getLine(0);
        var element = ElementType.CROTCHET.newInstance();
        song.withoutMutationTracking(() -> line.addElement(0, element));
        var box = new LyricBoxLayout(3.0, 2.0, 1, "do");
        var layoutResult = LayoutResult.builder()
            .setAboveStaffSs(1.0)
            .setBelowContentSs(0.5)
            .addLyricBox(element, box)
            .build();

        var hit = layoutResult.hitTestLyric(
            line,
            new Point2D.Double(
                ScaleContext.getInstance().toRoundedPixels(5.5),
                ScaleContext.getInstance().toRoundedPixels(7.0)
            )
        );

        assertThat(hit).isNull();
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private static final double TOLERANCE = 0.0001;

    private static SongLayoutMetrics testSongMetrics() {
        // maxAboveStaffSs=1, maxBelowStaffSs=1, maxBelowContentSs=0.5,
        // staffToLyricsGapSs=0.25, lyricsLineHeightSs=2.0, verseCount=1
        // verseYSsInLine(1) = (1 + 4) + 0.5 + 0.25 + 0*2.0 = 5.75
        return new SongLayoutMetrics(1.0, 1.0, 0.5, 0.25, 2.0, 1, 2.0, 9.75);
    }

    private static ElementColumn testColumnAt(StaffElement element, double xSs) {
        var column = new ElementColumn(
            element, Collections.emptyList(), 0.0, Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }
}
