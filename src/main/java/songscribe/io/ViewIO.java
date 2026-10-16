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

    public static class ViewReader {

        private static final Logger LOG = LoggerFactory.getLogger(ViewReader.class);

        private final LeafTagText leaf = new LeafTagText();
        private final Map<FontKey, StringFont> stringFonts = new EnumMap<>(FontKey.class);
        private final Map<String, Consumer<String>> setters = new HashMap<>();

        public ViewReader() {
            var defaults = DocumentFonts.defaultFonts();
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
            leaf.open(qName);
        }

        public void endElement11(String qName) {
            leaf.close(qName, (tag, text) -> {
                var setter = setters.get(tag);

                if (setter != null) {
                    setter.accept(text);
                }
            });
        }

        public void characters(char[] ch, int start, int length) {
            leaf.append(ch, start, length);
        }

        /**
         * Roles absent from the block fall through to {@link DocumentFonts#defaultFonts()}.
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
