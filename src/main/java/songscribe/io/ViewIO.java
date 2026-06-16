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

import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;

public final class ViewIO {

    private static final String XML_TITLE_FONT = "titlefont";
    private static final String XML_TITLE_FONT_SIZE = "titlefontsize";
    private static final String XML_LYRICS_FONT = "lyricsfont";
    private static final String XML_LYRICS_FONT_SIZE = "lyricsfontsize";
    private static final String XML_GENERAL_FONT = "generalfont";
    private static final String XML_GENERAL_FONT_SIZE = "generalfontsize";
    private static final String XML_ANNOTATION_FONT = "annotationfont";
    private static final String XML_ANNOTATION_FONT_SIZE = "annotationfontsize";
    private static final String XML_BANGLA_FONT = "banglaFont";
    private static final String XML_BANGLA_FONT_SIZE = "banglaFontSize";
    private static final String XML_FOOTNOTE_FONT = "footnoteFont";
    private static final String XML_FOOTNOTE_FONT_SIZE = "footnoteFontSize";
    private static final String XML_SUB_ATTRIBUTION_FONT = "subAttributionFont";
    private static final String XML_SUB_ATTRIBUTION_FONT_SIZE = "subAttributionFontSize";
    private static final String XML_SUBTITLE_FONT = "subtitleFont";
    private static final String XML_SUBTITLE_FONT_SIZE = "subtitleFontSize";

    private record FontTags(FontKey key, String nameTag, String sizeTag) {}

    private static final FontTags[] FONT_TAGS = {
        new FontTags(FontKey.TITLE,           XML_TITLE_FONT,           XML_TITLE_FONT_SIZE),
        new FontTags(FontKey.SUBTITLE,        XML_SUBTITLE_FONT,        XML_SUBTITLE_FONT_SIZE),
        new FontTags(FontKey.LYRICS,          XML_LYRICS_FONT,          XML_LYRICS_FONT_SIZE),
        new FontTags(FontKey.ATTRIBUTION,     XML_GENERAL_FONT,         XML_GENERAL_FONT_SIZE),
        new FontTags(FontKey.SUB_ATTRIBUTION, XML_SUB_ATTRIBUTION_FONT, XML_SUB_ATTRIBUTION_FONT_SIZE),
        new FontTags(FontKey.ANNOTATION,      XML_ANNOTATION_FONT,      XML_ANNOTATION_FONT_SIZE),
        new FontTags(FontKey.BANGLA,          XML_BANGLA_FONT,          XML_BANGLA_FONT_SIZE),
        new FontTags(FontKey.FOOTNOTE,        XML_FOOTNOTE_FONT,        XML_FOOTNOTE_FONT_SIZE),
    };

    private ViewIO() {}

    public static void writeView(DocumentFontsHolder fonts, PrintWriter pw) {
        XML.setIndent(4);
        for (var t : FONT_TAGS) {
            var font = fonts.getFont(t.key);
            XML.writeValue(pw, t.nameTag, font.getPSName());
            XML.writeValue(pw, t.sizeTag, Integer.toString(font.getSize()));
        }
    }

    public static class ViewReader {

        private static final Logger LOG = LoggerFactory.getLogger(ViewReader.class);

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(40);
        private final Map<FontKey, StringFont> stringFonts = new EnumMap<>(FontKey.class);
        private final Map<String, Consumer<String>> setters = new HashMap<>();

        public ViewReader() {
            var defaults = DocumentFonts.defaultsFromPrefs();
            for (var t : FONT_TAGS) {
                var sf = StringFont.from(defaults, t.key);
                var sizeTag = t.sizeTag;
                stringFonts.put(t.key, sf);
                setters.put(t.nameTag, v -> sf.name = v);
                setters.put(sizeTag, v -> {
                    try {
                        sf.size = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        LOG.warn("Corrupt document: malformed font size for {}: '{}', using default", sizeTag, v);
                    }
                });
            }
        }

        public void startElement11(String qName) {
            lastTag = qName;
            value.delete(0, value.length());
        }

        public void endElement11(String qName) {
            //noinspection PointlessNullCheck
            if (lastTag != null && qName.equals(lastTag)) {
                var setter = setters.get(lastTag);
                if (setter != null) {
                    setter.accept(value.toString());
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

        /**
         * Builds a {@link DocumentFonts} from the parsed {@code <view>} block. Roles
         * absent from the block fall through to the {@link Prefs} defaults the
         * reader was constructed with — so partial blocks and v1.1+ files without
         * a full font set produce the same result as a missing {@code <view>}
         * (handled by {@code SongIO} returning {@link DocumentFonts#defaultsFromPrefs}).
         */
        public DocumentFonts getDocumentFonts() {
            var fonts = new DocumentFonts();
            stringFonts.forEach((key, sf) -> fonts.setFont(key, sf.name, sf.size));
            return fonts;
        }

        private static class StringFont {

            String name;
            int size;

            StringFont(String name, int size) {
                this.name = name;
                this.size = size;
            }

            static StringFont from(DocumentFonts defaults, FontKey key) {
                var font = defaults.getFont(key);
                return new StringFont(font.getPSName(), font.getSize());
            }

        }
    }
}
