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

class ViewIOTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AnnotationFontRoundTrip {

        @Test
        void testAnnotationFontRoundTripsCorrectly() throws Exception {
            var original = new Song();
            var customFont = MyFontUtils.createFont("LatoPlus-Bold", 22);
            original.setAnnotationFont(customFont);
            addNote(original);

            var reloaded = roundTrip(original);

            assertThat(reloaded.getAnnotationFont().getPSName())
                .isEqualTo(customFont.getPSName());
            assertThat(reloaded.getAnnotationFont().getSize())
                .isEqualTo(customFont.getSize());
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FontXmlParsing {

        @Test
        void testDocumentWithFontXmlAppliesFonts() throws Exception {
            var original = new Song();
            var titleFont = MyFontUtils.createFont("LatoPlus-Bold", 24);
            var lyricsFont = MyFontUtils.createFont("LatoPlus-Regular", 14);
            var attributionFont = MyFontUtils.createFont("LatoPlus-Regular", 12);
            var annotationFont = MyFontUtils.createFont("LatoPlus-Bold", 18);
            original.setTitleFont(titleFont);
            original.setLyricsFont(lyricsFont);
            original.setAttributionFont(attributionFont);
            original.setAnnotationFont(annotationFont);
            addNote(original);

            var reloaded = roundTrip(original);

            assertThat(reloaded.getTitleFont().getPSName())
                .isEqualTo(titleFont.getPSName());
            assertThat(reloaded.getTitleFont().getSize())
                .isEqualTo(titleFont.getSize());
            assertThat(reloaded.getLyricsFont().getPSName())
                .isEqualTo(lyricsFont.getPSName());
            assertThat(reloaded.getLyricsFont().getSize())
                .isEqualTo(lyricsFont.getSize());
            assertThat(reloaded.getAttributionFont().getPSName())
                .isEqualTo(attributionFont.getPSName());
            assertThat(reloaded.getAttributionFont().getSize())
                .isEqualTo(attributionFont.getSize());
            assertThat(reloaded.getAnnotationFont().getPSName())
                .isEqualTo(annotationFont.getPSName());
            assertThat(reloaded.getAnnotationFont().getSize())
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
            assertThat(song.getTitleFont().getPSName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.TITLE_FONT),
                    prefs.getInt(PrefsKey.TITLE_FONT_SIZE)
                ).getPSName());
            assertThat(song.getTitleFont().getSize())
                .isEqualTo(prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
            assertThat(song.getLyricsFont().getPSName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.LYRICS_FONT),
                    prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)
                ).getPSName());
            assertThat(song.getAnnotationFont().getPSName())
                .isEqualTo(MyFontUtils.createFont(
                    prefs.getString(PrefsKey.ANNOTATION_FONT),
                    prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)
                ).getPSName());
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
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
            assertThat(song.getTitleFont()).isNotNull();
            assertThat(song.getLyricsFont()).isNotNull();
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
