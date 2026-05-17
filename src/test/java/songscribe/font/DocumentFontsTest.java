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

package songscribe.font;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.awt.Font;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

class DocumentFontsTest extends UnitTest {

    private static final int BASE_SIZE = 12;
    private static final int ALT_SIZE = 14;
    private static final String BASE_NAME = "Dialog";
    private static final String ALT_NAME = "SansSerif";
    private static final Font BASE_FONT = new Font(BASE_NAME, Font.PLAIN, BASE_SIZE);
    private static final Font ALT_SIZE_FONT = new Font(BASE_NAME, Font.PLAIN, ALT_SIZE);
    private static final Font ALT_NAME_FONT = new Font(ALT_NAME, Font.PLAIN, BASE_SIZE);

    /** Returns a {@link DocumentFonts} with {@link #BASE_FONT} set for every role. */
    private static DocumentFonts fullyPopulated() {
        var fonts = new DocumentFonts();

        for (var key : FontKey.values()) {
            fonts.setFont(key, BASE_FONT);
        }

        return fonts;
    }

    @Nested
    class CopyConstructor {

        @Test
        void testMutatingCopyDoesNotAffectOriginal() {
            var original = fullyPopulated();
            var copy = new DocumentFonts(original);
            copy.setFont(FontKey.TITLE, ALT_SIZE_FONT);
            assertThat(original.getFont(FontKey.TITLE)).isEqualTo(BASE_FONT);
        }

        @Test
        void testMutatingOriginalDoesNotAffectCopy() {
            var original = fullyPopulated();
            var copy = new DocumentFonts(original);
            original.setFont(FontKey.TITLE, ALT_SIZE_FONT);
            assertThat(copy.getFont(FontKey.TITLE)).isEqualTo(BASE_FONT);
        }
    }

    @Nested
    class DefaultsFromPrefs {

        @Test
        void testAllRolesPopulated() {
            var prefs = Prefs.getInstance();
            var fonts = DocumentFonts.defaultsFromPrefs();
            assertAll(
                () -> assertThat(fonts.getFont(FontKey.TITLE).getSize()).isEqualTo(prefs.getInt(PrefsKey.TITLE_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.LYRICS).getSize()).isEqualTo(prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.ATTRIBUTION).getSize()).isEqualTo(prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.ANNOTATION).getSize()).isEqualTo(prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.FOOTNOTE).getSize()).isEqualTo(prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.BANGLA).getSize()).isEqualTo(prefs.getInt(PrefsKey.BANGLA_FONT_SIZE))
            );
        }
    }

    @Nested
    class Equals {

        @Test
        void testEqualIdenticalContent() {
            assertThat(fullyPopulated()).isEqualTo(fullyPopulated());
        }

        @Test
        void testEqualReflexive() {
            var fonts = fullyPopulated();
            assertThat(fonts).isEqualTo(fonts);
        }

        @ParameterizedTest
        @EnumSource(FontKey.class)
        void testNotEqualWhenNameDiffers(FontKey key) {
            var a = fullyPopulated();
            var b = fullyPopulated();
            b.setFont(key, ALT_NAME_FONT);
            assertThat(a).isNotEqualTo(b);
            assertThat(b).isNotEqualTo(a);
        }

        @ParameterizedTest
        @EnumSource(FontKey.class)
        void testNotEqualWhenSizeDiffers(FontKey key) {
            var a = fullyPopulated();
            var b = fullyPopulated();
            b.setFont(key, ALT_SIZE_FONT);
            assertThat(a).isNotEqualTo(b);
            assertThat(b).isNotEqualTo(a);
        }

        @Test
        void testHashCodeConsistentWithEquals() {
            var a = fullyPopulated();
            var b = fullyPopulated();
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    class GetSet {

        @ParameterizedTest
        @EnumSource(FontKey.class)
        void testGetFontRoundTrip(FontKey key) {
            var fonts = new DocumentFonts();
            fonts.setFont(key, BASE_FONT);
            assertThat(fonts.getFont(key)).isEqualTo(BASE_FONT);
        }

        @ParameterizedTest
        @EnumSource(FontKey.class)
        void testGetFontThrowsWhenNotSet(FontKey key) {
            var fonts = new DocumentFonts();
            assertThatThrownBy(() -> fonts.getFont(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key.name());
        }

        @ParameterizedTest
        @EnumSource(FontKey.class)
        void testSetFontByNameRoundTripSize(FontKey key) {
            var fonts = new DocumentFonts();
            fonts.setFont(key, BASE_NAME, BASE_SIZE);
            var font = fonts.getFont(key);
            assertThat(font.getSize()).isEqualTo(BASE_SIZE);
            assertThat(font.getPSName()).isNotEmpty();
        }
    }
}
