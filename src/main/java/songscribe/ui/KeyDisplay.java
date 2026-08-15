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

package songscribe.ui;

import java.text.AttributedString;
import java.util.Map;

import songscribe.Strings;
import songscribe.dom.Key;
import songscribe.smufl.SMuFLGlyph;
import songscribe.ui.renderer.RenderingUtils;
import songscribe.util.MyFontUtils;

import static java.awt.font.TextAttribute.FONT;
import static java.awt.font.TextAttribute.TRACKING;

public final class KeyDisplay {

    private static final char FLAT_GLYPH = SMuFLGlyph.CSYM_ACCIDENTAL_FLAT.codepoint();
    private static final char SHARP_GLYPH = SMuFLGlyph.CSYM_ACCIDENTAL_SHARP.codepoint();

    // Both are indexed by accidental count. Index 0 is C in either, so NO_ACCIDENTALS — which is
    // neither flat nor sharp — is named correctly whichever table a caller reaches for.
    static final String[] FLAT_TONICS = {
        "C", "F", "B" + FLAT_GLYPH, "E" + FLAT_GLYPH,
        "A" + FLAT_GLYPH, "D" + FLAT_GLYPH, "G" + FLAT_GLYPH, "C" + FLAT_GLYPH,
    };

    static final String[] SHARP_TONICS = {
        "C", "G", "D", "A", "E", "B", "F" + SHARP_GLYPH, "C" + SHARP_GLYPH,
    };

    static final int MIN_FLAT_COUNT_WITH_ACCIDENTAL = 2;
    static final int MIN_SHARP_COUNT_WITH_ACCIDENTAL = 6;
    private static final float LETTER_GLYPH_GAP_PX = 1.5f;

    private KeyDisplay() {}

    /**
     * Returns the key's display name, e.g. {@code "D" + SHARP_GLYPH + " · 2 sharps"}.
     *
     * @param key the key to name
     * @return an {@link AttributedString} with the tonic's accidental, if any, rendered in the
     *         music font and the rest in the UI label font
     */
    public static AttributedString getDisplayName(Key key) {
        var tonic = tonicFor(key);
        var suffix = suffixFor(key);
        var full = tonic + suffix;
        var attributed = new AttributedString(full);

        if (full.isEmpty()) {
            return attributed;
        }

        var labelFont = MyFontUtils.getUIFont("Label.font");
        attributed.addAttribute(FONT, labelFont);

        if (tonicHasAccidental(key)) {
            var glyphIndex = tonic.length() - 1;
            var glyphFont = RenderingUtils.getMusicFont().deriveFont(labelFont.getSize2D());
            var tracking = LETTER_GLYPH_GAP_PX / labelFont.getSize2D();
            var letterWithGapFont = labelFont.deriveFont(Map.of(TRACKING, tracking));
            attributed.addAttribute(FONT, glyphFont, glyphIndex, glyphIndex + 1);
            attributed.addAttribute(FONT, letterWithGapFont, glyphIndex - 1, glyphIndex);
        }

        return attributed;
    }

    private static String tonicFor(Key key) {
        var tonics = key.isFlatKey() ? FLAT_TONICS : SHARP_TONICS;
        return tonics[key.accidentalCount()];
    }

    private static String suffixFor(Key key) {
        if (key == Key.NO_ACCIDENTALS) {
            return "";
        }

        var template = key.isFlatKey()
            ? Strings.MUSIC_KEY_DISPLAY_FLATS
            : Strings.MUSIC_KEY_DISPLAY_SHARPS;
        return ' ' + Strings.get(template, key.accidentalCount());
    }

    private static boolean tonicHasAccidental(Key key) {
        var count = key.accidentalCount();

        if (key.isFlatKey()) {
            return count >= MIN_FLAT_COUNT_WITH_ACCIDENTAL;
        }

        return count >= MIN_SHARP_COUNT_WITH_ACCIDENTAL;
    }
}
