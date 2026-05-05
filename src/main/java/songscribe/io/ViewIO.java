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
package songscribe.io;

import module java.desktop;

import java.io.PrintWriter;

import org.jspecify.annotations.Nullable;

import songscribe.music.Song;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

public final class ViewIO {

    private static final String XML_TITLE_FONT = "titlefont";
    private static final String XML_TITLE_FONT_SIZE = "titlefontsize";
    private static final String XML_LYRICS_FONT = "lyricsfont";
    private static final String XML_LYRICS_FONT_SIZE = "lyricsfontsize";
    private static final String XML_GENERAL_FONT = "generalfont";
    private static final String XML_GENERAL_FONT_SIZE = "generalfontsize";
    private static final String XML_ANNOTATION_FONT = "annotationfont";
    private static final String XML_ANNOTATION_FONT_SIZE = "annotationfontsize";

    private ViewIO() {}

    public static void writeView(Song c, PrintWriter pw) {
        XML.setIndent(4);
        var titleFont = c.getTitleFont();
        var lyricsFont = c.getLyricsFont();
        var attributionFont = c.getAttributionFont();
        var annotationFont = c.getAnnotationFont();
        XML.writeValue(pw, XML_TITLE_FONT, titleFont.getPSName());
        XML.writeValue(pw, XML_TITLE_FONT_SIZE, String.valueOf(titleFont.getSize()));
        XML.writeValue(pw, XML_LYRICS_FONT, lyricsFont.getPSName());
        XML.writeValue(pw, XML_LYRICS_FONT_SIZE, Integer.toString(lyricsFont.getSize()));
        XML.writeValue(pw, XML_GENERAL_FONT, attributionFont.getPSName());
        XML.writeValue(pw, XML_GENERAL_FONT_SIZE, Integer.toString(attributionFont.getSize()));
        XML.writeValue(pw, XML_ANNOTATION_FONT, annotationFont.getPSName());
        XML.writeValue(pw, XML_ANNOTATION_FONT_SIZE, Integer.toString(annotationFont.getSize()));
    }

    public static class ViewReader {

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(40);
        private final StringFont title;
        private final StringFont lyrics;
        private final StringFont general;
        private final StringFont annotation;

        public ViewReader() {
            var prefs = Prefs.getInstance();
            title = new StringFont(
                prefs.getString(PrefsKey.TITLE_FONT),
                Integer.toString(prefs.getInt(PrefsKey.TITLE_FONT_SIZE))
            );
            lyrics = new StringFont(
                prefs.getString(PrefsKey.LYRICS_FONT),
                Integer.toString(prefs.getInt(PrefsKey.LYRICS_FONT_SIZE))
            );
            general = new StringFont(
                prefs.getString(PrefsKey.ATTRIBUTION_FONT),
                Integer.toString(prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE))
            );
            annotation = new StringFont(
                prefs.getString(PrefsKey.ANNOTATION_FONT),
                Integer.toString(prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE))
            );
        }

        public void startElement11(String qName) {
            lastTag = qName;
            value.delete(0, value.length());
        }

        public void endElement11(String qName) {
            //noinspection PointlessNullCheck
            if (lastTag != null && qName.equals(lastTag)) {
                var str = value.toString();

                switch (lastTag) {
                    case XML_TITLE_FONT -> title.name = str;
                    case XML_TITLE_FONT_SIZE -> title.size = str;
                    case XML_LYRICS_FONT -> lyrics.name = str;
                    case XML_LYRICS_FONT_SIZE -> lyrics.size = str;
                    case XML_GENERAL_FONT -> general.name = str;
                    case XML_GENERAL_FONT_SIZE -> general.size = str;
                    case XML_ANNOTATION_FONT -> annotation.name = str;
                    case XML_ANNOTATION_FONT_SIZE -> annotation.size = str;
                }
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void characters(char[] ch, int start, int length) {
            if (lastTag != null) {
                value.append(ch, start, length);
            }
        }

        public Font getTitleFont() {
            return title.getFont();
        }

        public Font getLyricsFont() {
            return lyrics.getFont();
        }

        public Font getAttributionFont() {
            return general.getFont();
        }

        public Font getAnnotationFont() {
            return annotation.getFont();
        }

        private static class StringFont {

            String name, size;

            StringFont(String name, String size) {
                this.name = name;
                this.size = size;
            }

            Font getFont() {
                return MyFontUtils.createFont(name, Integer.parseInt(size));
            }
        }
    }
}
