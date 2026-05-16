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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.notification.FontDidChangeNotification;
import songscribe.util.MyFontUtils;

/**
 * Verifies that document-font name+size fields are authoritative and consistent
 * after each write path (setter, fontDidChange, loadFrom).
 */
class SongFontNameSizeTest extends UnitTest {

    private static final int TEST_FONT_SIZE = 20;
    private static final String TEST_FONT_NAME = "LatoPlus-Bold";

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    // --- setXFont paths ---

    @Test
    void testSetTitleFontMaintainsInvariant() {
        song.setTitleFont(TEST_FONT_NAME, TEST_FONT_SIZE);
        assertThat(song.getTitleFontName()).isEqualTo(TEST_FONT_NAME);
        assertThat(song.getTitleFontSize()).isEqualTo(TEST_FONT_SIZE);
    }

    @Test
    void testSetLyricsFontMaintainsInvariant() {
        song.setLyricsFont(TEST_FONT_NAME, TEST_FONT_SIZE);
        assertThat(song.getLyricsFontName()).isEqualTo(TEST_FONT_NAME);
        assertThat(song.getLyricsFontSize()).isEqualTo(TEST_FONT_SIZE);
    }

    @Test
    void testSetAttributionFontMaintainsInvariant() {
        song.setAttributionFont(TEST_FONT_NAME, TEST_FONT_SIZE);
        assertThat(song.getAttributionFontName()).isEqualTo(TEST_FONT_NAME);
        assertThat(song.getAttributionFontSize()).isEqualTo(TEST_FONT_SIZE);
    }

    @Test
    void testSetAnnotationFontMaintainsInvariant() {
        song.setAnnotationFont(TEST_FONT_NAME, TEST_FONT_SIZE);
        assertThat(song.getAnnotationFontName()).isEqualTo(TEST_FONT_NAME);
        assertThat(song.getAnnotationFontSize()).isEqualTo(TEST_FONT_SIZE);
    }

    // --- fontDidChange path ---

    @Test
    void testFontDidChangeMaintainsInvariantForAllFonts() {
        var titleFont = MyFontUtils.createFont(TEST_FONT_NAME, TEST_FONT_SIZE);
        var lyricsFont = MyFontUtils.createFont(TEST_FONT_NAME, TEST_FONT_SIZE + 1);
        var attributionFont = MyFontUtils.createFont(TEST_FONT_NAME, TEST_FONT_SIZE + 2);
        var annotationFont = MyFontUtils.createFont(TEST_FONT_NAME, TEST_FONT_SIZE + 3);

        song.fontDidChange(new FontDidChangeNotification(titleFont, lyricsFont, attributionFont, annotationFont, null, null));

        // fontDidChange decomposes Font via getPSName()/getSize(), so size must match.
        assertThat(song.getTitleFontSize()).isEqualTo(TEST_FONT_SIZE);
        assertThat(song.getLyricsFontSize()).isEqualTo(TEST_FONT_SIZE + 1);
        assertThat(song.getAttributionFontSize()).isEqualTo(TEST_FONT_SIZE + 2);
        assertThat(song.getAnnotationFontSize()).isEqualTo(TEST_FONT_SIZE + 3);
    }

    // --- loadFrom path ---

    @Test
    void testLoadFixtureMaintainsInvariant() throws Exception {
        var loaded = loadFixture("full-line");
        // Verify name+size fields are populated after load.
        assertThat(loaded.getTitleFontName()).isNotNull();
        assertThat(loaded.getTitleFontSize()).isGreaterThan(0);
        assertThat(loaded.getLyricsFontName()).isNotNull();
        assertThat(loaded.getLyricsFontSize()).isGreaterThan(0);
        assertThat(loaded.getAttributionFontName()).isNotNull();
        assertThat(loaded.getAttributionFontSize()).isGreaterThan(0);
        assertThat(loaded.getAnnotationFontName()).isNotNull();
        assertThat(loaded.getAnnotationFontSize()).isGreaterThan(0);
    }

    @Test
    void testDefaultSongMaintainsInvariant() {
        // Default song initializes from prefs via initFontsFromPrefs.
        assertThat(song.getTitleFontName()).isNotNull();
        assertThat(song.getTitleFontSize()).isGreaterThan(0);
        assertThat(song.getLyricsFontName()).isNotNull();
        assertThat(song.getLyricsFontSize()).isGreaterThan(0);
        assertThat(song.getAttributionFontName()).isNotNull();
        assertThat(song.getAttributionFontSize()).isGreaterThan(0);
        assertThat(song.getAnnotationFontName()).isNotNull();
        assertThat(song.getAnnotationFontSize()).isGreaterThan(0);
    }
}
