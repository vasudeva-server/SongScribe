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

import java.awt.*;
import java.io.PrintWriter;

import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.ui.ProfileManager;
import songscribe.util.MyFontUtils;

public final class ViewIO {

    private static final String XML_TITLE_FONT = "titlefont";
    private static final String XML_TITLE_FONT_SIZE = "titlefontsize";
    private static final String XML_TITLE_FONT_STYLE = "titlefontstyle";
    private static final String XML_LYRICS_FONT = "lyricsfont";
    private static final String XML_LYRICS_FONT_SIZE = "lyricsfontsize";
    private static final String XML_LYRICS_FONT_STYLE = "lyricsfontstyle";
    private static final String XML_GENERAL_FONT = "generalfont";
    private static final String XML_GENERAL_FONT_SIZE = "generalfontsize";

    private ViewIO() {}

    public static void writeView(Composition c, PrintWriter pw) {
        XML.setIndent(4);
        XML.writeValue(pw, XML_TITLE_FONT, c.getTitleFont().getPSName());
        XML.writeValue(
            pw,
            XML_TITLE_FONT_SIZE,
            String.valueOf(c.getTitleFont().getSize())
        );
        XML.writeValue(pw, XML_TITLE_FONT_STYLE, ProfileManager.PLAIN);
        XML.writeValue(pw, XML_LYRICS_FONT, c.getLyricsFont().getPSName());
        XML.writeValue(
            pw,
            XML_LYRICS_FONT_SIZE,
            Integer.toString(c.getLyricsFont().getSize())
        );
        XML.writeValue(pw, XML_LYRICS_FONT_STYLE, ProfileManager.PLAIN);
        XML.writeValue(pw, XML_GENERAL_FONT, c.getAttributionFont().getPSName());
        XML.writeValue(
            pw,
            XML_GENERAL_FONT_SIZE,
            Integer.toString(c.getAttributionFont().getSize())
        );
    }

    public static class ViewReader {

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(40);
        private final StringFont title;
        private final StringFont lyrics;
        private final StringFont general;

        public ViewReader(ProfileManager pm) {
            title = new StringFont(
                pm.getDefaultProperty(ProfileManager.ProfileKey.TITLE_FONT),
                pm.getDefaultProperty(ProfileManager.ProfileKey.TITLE_FONT_SIZE)
            );
            lyrics = new StringFont(
                pm.getDefaultProperty(ProfileManager.ProfileKey.LYRICS_FONT),
                pm.getDefaultProperty(
                    ProfileManager.ProfileKey.LYRICS_FONT_SIZE
                )
            );
            general = new StringFont(
                pm.getDefaultProperty(ProfileManager.ProfileKey.ATTRIBUTION_FONT),
                pm.getDefaultProperty(ProfileManager.ProfileKey.ATTRIBUTION_FONT_SIZE)
            );
        }

        public void startElement11(String qName) {
            lastTag = qName;
            value.delete(0, value.length());
        }

        public void endElement11(String qName) {
            if (qName.equals(lastTag)) {
                var str = value.toString();

                switch (lastTag) {
                    case XML_TITLE_FONT -> title.name = str;
                    case XML_TITLE_FONT_SIZE -> title.size = str;
                    case XML_LYRICS_FONT -> lyrics.name = str;
                    case XML_LYRICS_FONT_SIZE -> lyrics.size = str;
                    case XML_GENERAL_FONT -> general.name = str;
                    case XML_GENERAL_FONT_SIZE -> general.size = str;
                }
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void characters(char[] ch, int start, int lenght) {
            if (lastTag != null) {
                value.append(ch, start, lenght);
            }
        }

        public void setAttributes(Composition c) {
            // We are not supporting custom fonts for now

            // c.setSongTitleFont(title.getFont());
            // c.setLyricsFont(lyrics.getFont());
            // c.setLyricsItalicFont(lyricsItalic.getFont());
            // c.setAttributionFont(general.getFont());
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
