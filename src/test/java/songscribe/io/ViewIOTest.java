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
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.model.Song;
import songscribe.model.ElementType;
import songscribe.model.Line;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

@SuppressWarnings("SameReturnValue")
class ViewIOTest extends UnitTest {

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class FontXmlParsing {

        @Test
        void testLegacyDocumentWithoutFontXmlUsesPrefsDefaults() throws Exception {
            // Build XML without the <view> section to simulate a legacy document
            var xml = buildLegacyXml();

            var factory = SAXParserFactory.newInstance();
            var parser = factory.newSAXParser();
            var reader = new SongIO.DocumentReader();
            parser.parse(new InputSource(new StringReader(xml)), reader);
            reader.getSong();

            var fonts = reader.getDocumentFonts();
            var expectedTitle = MyFontUtils.createFont(Prefs.getString(PrefsKey.TITLE_FONT), Prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
            assertThat(fonts.getFont(FontKey.TITLE).getPSName()).isEqualTo(expectedTitle.getPSName());
            assertThat(fonts.getFont(FontKey.TITLE).getSize()).isEqualTo(Prefs.getInt(PrefsKey.TITLE_FONT_SIZE));

            var expectedLyrics = MyFontUtils.createFont(Prefs.getString(PrefsKey.LYRICS_FONT), Prefs.getInt(PrefsKey.LYRICS_FONT_SIZE));
            assertThat(fonts.getFont(FontKey.LYRICS).getPSName()).isEqualTo(expectedLyrics.getPSName());

            var expectedAnnotation = MyFontUtils.createFont(Prefs.getString(PrefsKey.ANNOTATION_FONT), Prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE));
            assertThat(fonts.getFont(FontKey.ANNOTATION).getPSName()).isEqualTo(expectedAnnotation.getPSName());

            var expectedBangla = MyFontUtils.createFont(Prefs.getString(PrefsKey.BANGLA_FONT), Prefs.getInt(PrefsKey.BANGLA_FONT_SIZE));
            assertThat(fonts.getFont(FontKey.BANGLA).getPSName()).isEqualTo(expectedBangla.getPSName());
            assertThat(fonts.getFont(FontKey.BANGLA).getSize()).isEqualTo(Prefs.getInt(PrefsKey.BANGLA_FONT_SIZE));

            var expectedFootnote = MyFontUtils.createFont(Prefs.getString(PrefsKey.FOOTNOTE_FONT), Prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE));
            assertThat(fonts.getFont(FontKey.FOOTNOTE).getPSName()).isEqualTo(expectedFootnote.getPSName());
            assertThat(fonts.getFont(FontKey.FOOTNOTE).getSize()).isEqualTo(Prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE));
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
            reader.getSong();

            // Should load without error and have valid fonts
            assertThat(reader.getDocumentFonts().getFont(FontKey.TITLE)).isNotNull();
            assertThat(reader.getDocumentFonts().getFont(FontKey.LYRICS)).isNotNull();
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
    @Nested
    class DocumentFontsLoad {

        private static final String CUSTOM_TITLE_FONT = "LatoPlus-Bold";
        private static final int CUSTOM_TITLE_FONT_SIZE = 30;

        @Test
        void testV10FallbackUsesDefaultsForAllRoles() throws Exception {
            var fonts = parseAndGetDocumentFonts(buildV10Xml());

            assertThat(fonts).isEqualTo(DocumentFonts.defaultsFromPrefs());
        }

        @Test
        void testPartialBlockOverridesOnlyPresentRoles() throws Exception {
            var fonts = parseAndGetDocumentFonts(buildPartialViewXml());

            var expectedTitle = MyFontUtils.createFont(CUSTOM_TITLE_FONT, CUSTOM_TITLE_FONT_SIZE);
            assertThat(fonts.getFont(FontKey.TITLE).getPSName()).isEqualTo(expectedTitle.getPSName());
            assertThat(fonts.getFont(FontKey.TITLE).getSize()).isEqualTo(CUSTOM_TITLE_FONT_SIZE);

            // Remaining roles fall through to prefs defaults.
            assertRoleMatchesPrefs(fonts, FontKey.LYRICS,      PrefsKey.LYRICS_FONT,      PrefsKey.LYRICS_FONT_SIZE);
            assertRoleMatchesPrefs(fonts, FontKey.ATTRIBUTION, PrefsKey.ATTRIBUTION_FONT, PrefsKey.ATTRIBUTION_FONT_SIZE);
            assertRoleMatchesPrefs(fonts, FontKey.ANNOTATION,  PrefsKey.ANNOTATION_FONT,  PrefsKey.ANNOTATION_FONT_SIZE);
            assertRoleMatchesPrefs(fonts, FontKey.BANGLA,      PrefsKey.BANGLA_FONT,      PrefsKey.BANGLA_FONT_SIZE);
            assertRoleMatchesPrefs(fonts, FontKey.FOOTNOTE,    PrefsKey.FOOTNOTE_FONT,    PrefsKey.FOOTNOTE_FONT_SIZE);
        }

        @Test
        void testNewDocumentInstallsPrefsDefaults() {
            assertThat(DocumentFonts.defaultsFromPrefs())
                .isEqualTo(DocumentFonts.defaultsFromPrefs());
            // New-document creation in ScoreView.init() installs exactly this object;
            // the bootstrap source is the single home for the role -> PrefsKey mapping.
        }

        private static DocumentFonts parseAndGetDocumentFonts(String xml) throws Exception {
            var factory = SAXParserFactory.newInstance();
            var parser = factory.newSAXParser();
            var reader = new SongIO.DocumentReader();
            parser.parse(new InputSource(new StringReader(xml)), reader);
            reader.getSong();
            return reader.getDocumentFonts();
        }

        private static void assertRoleMatchesPrefs(DocumentFonts fonts, FontKey key, PrefsKey nameKey, PrefsKey sizeKey) {
            var expected = MyFontUtils.createFont(Prefs.getString(nameKey), Prefs.getInt(sizeKey));
            assertThat(fonts.getFont(key).getPSName()).isEqualTo(expected.getPSName());
            assertThat(fonts.getFont(key).getSize()).isEqualTo(Prefs.getInt(sizeKey));
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

    private static String buildV10Xml() {
        // v1.0 document — no <view> block exists in this format.
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.0">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription>Moderate</tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <notes>
              </notes>
            </composition>
            """;
    }

    private static String buildPartialViewXml() {
        // v1.1 document whose <view> block specifies only the TITLE role.
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription>Moderate</tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
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
