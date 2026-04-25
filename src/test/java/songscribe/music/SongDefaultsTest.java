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

import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

class SongDefaultsTest extends UnitTest {

    @Test
    void testAnnotationFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString(PrefsKey.ANNOTATION_FONT),
            prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)
        );

        var song = new Song();
        assertThat(song.getAnnotationFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(song.getAnnotationFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }

    @Test
    void testAttributionFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString(PrefsKey.ATTRIBUTION_FONT),
            prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)
        );

        var song = new Song();
        assertThat(song.getAttributionFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(song.getAttributionFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }

    @Test
    void testDefaultAttribution() {
        var song = new Song();
        assertThat(song.getAttribution())
            .isEqualTo(Strings.get(Strings.SONG_DEFAULT_ATTRIBUTION));
    }

    @Test
    void testDefaultKeySignature() {
        var song = new Song();
        assertThat(song.getDefaultKeyAccidentalCount()).isEqualTo(5);
        assertThat(song.getDefaultKeyType()).isEqualTo(KeyType.FLATS);
    }

    @Test
    void testDefaultTempo() {
        var song = new Song();
        var tempo = song.getTempo();
        assertThat(tempo.getVisibleTempo()).isEqualTo(120);
        assertThat(tempo.getTempoType()).isEqualTo(Duration.CROTCHET);
        assertThat(tempo.getTempoDescription()).isEqualTo("Moderate");
    }

    @Test
    void testLyricsFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString(PrefsKey.LYRICS_FONT),
            prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)
        );

        var song = new Song();
        assertThat(song.getLyricsFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(song.getLyricsFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }

    @Test
    void testNewSongHasOneLine() {
        var song = new Song();
        assertThat(song.lineCount()).isEqualTo(1);
    }

    // T50: New song — initial line has exactly one element: FINAL_DOUBLE_BARLINE.
    @Test
    void testNewSongInitialLineHasFinalBarline() {
        var song = new Song();
        var line = song.getLine(0);
        assertThat(line.elementCount()).isEqualTo(1);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    // T51: New song — not marked modified after construction.
    @Test
    void testNewSongIsNotModified() {
        var song = new Song();
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testTitleFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString(PrefsKey.TITLE_FONT),
            prefs.getInt(PrefsKey.TITLE_FONT_SIZE)
        );

        var song = new Song();
        assertThat(song.getTitleFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(song.getTitleFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }
}
