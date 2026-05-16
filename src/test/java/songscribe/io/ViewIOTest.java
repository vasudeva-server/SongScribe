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

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

@SuppressWarnings("SameReturnValue")
class ViewIOTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AnnotationFontRoundTrip {

        @Test
        void testAnnotationFontRoundTripsCorrectly() throws Exception {
            var original = new Song();
            var customFont = MyFontUtils.createFont("LatoPlus-Bold", 22);
            original.setAnnotationFont(customFont.getPSName(), customFont.getSize());
            addNote(original);

            var reloaded = roundTrip(original);

            assertThat(reloaded.getAnnotationFontName())
                .isEqualTo(customFont.getPSName());
            assertThat(reloaded.getAnnotationFontSize())
                .isEqualTo(customFont.getSize());
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class BanglaAndFootnoteFontRoundTrip {

        @Test
        void testBanglaAndFootnoteFontsRoundTripCorrectly() throws Exception {
            var original = new Song();
            // Use non-default sizes to confirm the written values are read back,
            // not the prefs defaults.
            var customBanglaFont = MyFontUtils.createFont("TiroBangla-Regular", 24);
            var customFootnoteFont = MyFontUtils.createFont("SourceSans3-Regular", 20);
            original.setBanglaFont(customBanglaFont.getPSName(), customBanglaFont.getSize());
            original.setFootnoteFont(customFootnoteFont.getPSName(), customFootnoteFont.getSize());
            addNote(original);

            var reloaded = roundTrip(original);

            assertThat(reloaded.getBanglaFontName())
                .isEqualTo(customBanglaFont.getPSName());
            assertThat(reloaded.getBanglaFontSize())
                .isEqualTo(customBanglaFont.getSize());
            assertThat(reloaded.getFootnoteFontName())
                .isEqualTo(customFootnoteFont.getPSName());
            assertThat(reloaded.getFootnoteFontSize())
                .isEqualTo(customFootnoteFont.getSize());
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class FontXmlParsing {

        @Test
        void testDocumentWithFontXmlAppliesFonts() throws Exception {
            var original = new Song();
            var titleFont = MyFontUtils.createFont("LatoPlus-Bold", 24);
            var lyricsFont = MyFontUtils.createFont("LatoPlus-Regular", 14);
            var attributionFont = MyFontUtils.createFont("LatoPlus-Regular", 12);
            var annotationFont = MyFontUtils.createFont("LatoPlus-Bold", 18);
            original.setTitleFont(titleFont.getPSName(), titleFont.getSize());
            original.setLyricsFont(lyricsFont.getPSName(), lyricsFont.getSize());
            original.setAttributionFont(attributionFont.getPSName(), attributionFont.getSize());
            original.setAnnotationFont(annotationFont.getPSName(), annotationFont.getSize());
            addNote(original);

            var reloaded = roundTrip(original);

            assertThat(reloaded.getTitleFontName())
                .isEqualTo(titleFont.getPSName());
            assertThat(reloaded.getTitleFontSize())
                .isEqualTo(titleFont.getSize());
            assertThat(reloaded.getLyricsFontName())
                .isEqualTo(lyricsFont.getPSName());
            assertThat(reloaded.getLyricsFontSize())
                .isEqualTo(lyricsFont.getSize());
            assertThat(reloaded.getAttributionFontName())
                .isEqualTo(attributionFont.getPSName());
            assertThat(reloaded.getAttributionFontSize())
                .isEqualTo(attributionFont.getSize());
            assertThat(reloaded.getAnnotationFontName())
                .isEqualTo(annotationFont.getPSName());
            assertThat(reloaded.getAnnotationFontSize())
                .isEqualTo(annotationFont.getSize());
        }

        @Test
        void testLegacyDocumentWithoutFontXmlUsesPrefsDefaults() throws Exception {
            // Build XML without the <view> section to simulate a legacy document
            var xml = buildLegacyXml();

            var factory = SAXParserFactory.newInstance();
            var parser = factory.newSAXParser();
            var reader = new SongIO.DocumentReader();
            parser.parse(new InputSource(new StringReader(xml)), reader);
            var song = reader.getSong();

            var prefs = Prefs.getInstance();
            assertThat(song.getTitleFontName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.TITLE_FONT),
                    prefs.getInt(PrefsKey.TITLE_FONT_SIZE)
                ).getPSName());
            assertThat(song.getTitleFontSize())
                .isEqualTo(prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
            assertThat(song.getLyricsFontName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.LYRICS_FONT),
                    prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)
                ).getPSName());
            assertThat(song.getAnnotationFontName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.ANNOTATION_FONT),
                    prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)
                ).getPSName());
            assertThat(song.getBanglaFontName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.BANGLA_FONT),
                    prefs.getInt(PrefsKey.BANGLA_FONT_SIZE)
                ).getPSName());
            assertThat(song.getBanglaFontSize())
                .isEqualTo(prefs.getInt(PrefsKey.BANGLA_FONT_SIZE));
            assertThat(song.getFootnoteFontName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.FOOTNOTE_FONT),
                    prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE)
                ).getPSName());
            assertThat(song.getFootnoteFontSize())
                .isEqualTo(prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE));
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class LegacyFontStyleElements {

        @Test
        void testDocumentWithFontStyleElementsLoadsWithoutError() throws Exception {
            // Build XML that includes the old <titlefontstyle> and <lyricsfontstyle> elements
            var xml = buildXmlWithFontStyleElements();

            var factory = SAXParserFactory.newInstance();
            var parser = factory.newSAXParser();
            var reader = new SongIO.DocumentReader();
            parser.parse(new InputSource(new StringReader(xml)), reader);
            var song = reader.getSong();

            // Should load without error and have valid fonts
            assertThat(song.getTitleFontName()).isNotNull();
            assertThat(song.getLyricsFontName()).isNotNull();
        }
    }

    // -- Helpers --

    private static void addNote(Song song) {
        var line = new Line(song);
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(0);
        song.withoutMutationTracking(() -> line.addElement(note));
        song.addLine(line);
    }

    private static String buildLegacyXml() {
        // Minimal v1.1 document without a <view> section
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>5</keys>
              <keytype>FLATS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription>Moderate</tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>5</keyCount>
                  <keyType>FLATS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <upper>true</upper>
                    </note>
                  </notes>
                </line>
              </lines>
              <view>
              </view>
            </composition>
            """;
    }

    private static String buildXmlWithFontStyleElements() {
        // v1.1 document with old <titlefontstyle> and <lyricsfontstyle> elements
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>5</keys>
              <keytype>FLATS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription>Moderate</tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>5</keyCount>
                  <keyType>FLATS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <upper>true</upper>
                    </note>
                  </notes>
                </line>
              </lines>
              <view>
                <titlefont>LatoPlus-Bold</titlefont>
                <titlefontsize>30</titlefontsize>
                <titlefontstyle>Plain</titlefontstyle>
                <lyricsfont>LatoPlus-Regular</lyricsfont>
                <lyricsfontsize>17</lyricsfontsize>
                <lyricsfontstyle>Plain</lyricsfontstyle>
                <generalfont>LatoPlus-Regular</generalfont>
                <generalfontsize>15</generalfontsize>
              </view>
            </composition>
            """;
    }
}
