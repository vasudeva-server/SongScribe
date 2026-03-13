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

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.util.MyFontUtils;

class CompositionDefaultsTest extends UnitTest {

    @Test
    void testAnnotationFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString("annotationFont"),
            prefs.getInt("annotationFontSize")
        );

        var composition = new Composition();
        assertThat(composition.getAnnotationFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(composition.getAnnotationFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }

    @Test
    void testAttributionFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString("attributionFont"),
            prefs.getInt("attributionFontSize")
        );

        var composition = new Composition();
        assertThat(composition.getAttributionFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(composition.getAttributionFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }

    @Test
    void testDefaultAttribution() {
        var composition = new Composition();
        assertThat(composition.getAttribution()).isEqualTo("Words and Music\nby Sri Chinmoy\n");
    }

    @Test
    void testDefaultKeySignature() {
        var composition = new Composition();
        assertThat(composition.getDefaultKeyAccidentalCount()).isEqualTo(5);
        assertThat(composition.getDefaultKeyType()).isEqualTo(KeyType.FLATS);
    }

    @Test
    void testDefaultTempo() {
        var composition = new Composition();
        var tempo = composition.getTempo();
        assertThat(tempo.getVisibleTempo()).isEqualTo(120);
        assertThat(tempo.getTempoType()).isEqualTo(Tempo.Type.CROTCHET);
        assertThat(tempo.getTempoDescription()).isEqualTo("Moderate");
    }

    @Test
    void testLyricsFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString("lyricsFont"),
            prefs.getInt("lyricsFontSize")
        );

        var composition = new Composition();
        assertThat(composition.getLyricsFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(composition.getLyricsFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }

    @Test
    void testNewCompositionHasOneLine() {
        var composition = new Composition();
        assertThat(composition.lineCount()).isEqualTo(1);
    }

    @Test
    void testTitleFontMatchesPrefs() {
        var prefs = Prefs.getInstance();
        var expectedFont = MyFontUtils.createFont(
            prefs.getString("titleFont"),
            prefs.getInt("titleFontSize")
        );

        var composition = new Composition();
        assertThat(composition.getTitleFont().getPSName())
            .isEqualTo(expectedFont.getPSName());
        assertThat(composition.getTitleFont().getSize())
            .isEqualTo(expectedFont.getSize());
    }
}
