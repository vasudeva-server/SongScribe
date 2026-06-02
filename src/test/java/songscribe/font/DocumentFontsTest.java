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

import org.junit.jupiter.api.BeforeAll;
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
            // Verify both the size and the font family for each role. Checking only
            // the size allows a mutant that swaps the PrefsKey font-name constant (e.g.
            // TITLE_FONT -> LYRICS_FONT) to pass undetected.
            var fonts = DocumentFonts.defaultsFromPrefs();
            assertAll(
                () -> assertThat(fonts.getFont(FontKey.TITLE).getSize())
                    .isEqualTo(Prefs.getInt(PrefsKey.TITLE_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.LYRICS).getSize())
                    .isEqualTo(Prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.ATTRIBUTION).getSize())
                    .isEqualTo(Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.ANNOTATION).getSize())
                    .isEqualTo(Prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.FOOTNOTE).getSize())
                    .isEqualTo(Prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE)),
                () -> assertThat(fonts.getFont(FontKey.BANGLA).getSize())
                    .isEqualTo(Prefs.getInt(PrefsKey.BANGLA_FONT_SIZE)),
                // Verify the correct prefs font-name key is used for each role.
                // Swapping, say, TITLE_FONT and LYRICS_FONT would still produce the right
                // size if the sizes happen to match, but the font family name would differ.
                () -> assertThat(fonts.getFont(FontKey.TITLE).getFamily())
                    .isEqualTo(fontFamilyFor(PrefsKey.TITLE_FONT)),
                () -> assertThat(fonts.getFont(FontKey.LYRICS).getFamily())
                    .isEqualTo(fontFamilyFor(PrefsKey.LYRICS_FONT)),
                () -> assertThat(fonts.getFont(FontKey.ATTRIBUTION).getFamily())
                    .isEqualTo(fontFamilyFor(PrefsKey.ATTRIBUTION_FONT)),
                () -> assertThat(fonts.getFont(FontKey.ANNOTATION).getFamily())
                    .isEqualTo(fontFamilyFor(PrefsKey.ANNOTATION_FONT)),
                () -> assertThat(fonts.getFont(FontKey.FOOTNOTE).getFamily())
                    .isEqualTo(fontFamilyFor(PrefsKey.FOOTNOTE_FONT)),
                () -> assertThat(fonts.getFont(FontKey.BANGLA).getFamily())
                    .isEqualTo(fontFamilyFor(PrefsKey.BANGLA_FONT))
            );
        }

        @Test
        void testAllRolesMappedToCorrectPrefsKey() {
            // Build fonts via defaultsFromPrefs() and verify that each FontKey reads
            // the expected PrefsKey pair. This catches a cut-paste bug where, e.g.,
            // TITLE uses LYRICS_FONT_SIZE or LYRICS uses TITLE_FONT — bugs the size-only
            // test cannot catch when sizes happen to differ only slightly.
            var fonts = DocumentFonts.defaultsFromPrefs();

            // Each role must produce the exact same font that setFont(key, psName, size)
            // would produce from the corresponding prefs pair — verified role by role.
            var expected = new DocumentFonts();
            expected.setFont(FontKey.TITLE,       Prefs.getString(PrefsKey.TITLE_FONT),       Prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
            expected.setFont(FontKey.LYRICS,      Prefs.getString(PrefsKey.LYRICS_FONT),      Prefs.getInt(PrefsKey.LYRICS_FONT_SIZE));
            expected.setFont(FontKey.ATTRIBUTION, Prefs.getString(PrefsKey.ATTRIBUTION_FONT), Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE));
            expected.setFont(FontKey.ANNOTATION,  Prefs.getString(PrefsKey.ANNOTATION_FONT),  Prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE));
            expected.setFont(FontKey.FOOTNOTE,    Prefs.getString(PrefsKey.FOOTNOTE_FONT),    Prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE));
            expected.setFont(FontKey.BANGLA,      Prefs.getString(PrefsKey.BANGLA_FONT),      Prefs.getInt(PrefsKey.BANGLA_FONT_SIZE));

            assertThat(fonts).isEqualTo(expected);
        }

        /**
         * Returns the font family name that {@code MyFontUtils.createFont} resolves for
         * the given prefs font-name key. This mirrors exactly what
         * {@link DocumentFonts#defaultsFromPrefs} does so the assertion is meaningful
         * even if SourceSans3 is not installed (in which case both sides fall back to the
         * same label font).
         */
        private String fontFamilyFor(PrefsKey fontKey) {
            // Create a throwaway DocumentFonts to resolve the PS name to a font.
            var holder = new DocumentFonts();
            holder.setFont(FontKey.TITLE, Prefs.getString(fontKey), BASE_SIZE);
            return holder.getFont(FontKey.TITLE).getFamily();
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
        void testNotEqualToNull() {
            // equals(null) must return false, not throw NullPointerException.
            // Use AssertJ to call equals(null) internally rather than at this call site.
            assertThat(fullyPopulated()).isNotEqualTo(null);
        }

        @Test
        void testNotEqualToDifferentType() {
            // equals() must return false for an object of a different class.
            assertThat(fullyPopulated().equals("not a DocumentFonts")).isFalse();
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

        @BeforeAll
        static void installFonts() {
            // Install SourceSans3 so that setFont(key, psName, size) can resolve
            // real PS names from the bundled font files, not fall back to a UI font.
            SourceSans3Font.installBasic();
        }

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

        @ParameterizedTest
        @EnumSource(FontKey.class)
        void testSetFontByPsNameResolvesCorrectFamily(FontKey key) {
            // When the PS name is a registered SourceSans3 name the resolved font's
            // family must be "Source Sans 3", not a fallback system font.
            var fonts = new DocumentFonts();
            fonts.setFont(key, "SourceSans3-Regular", BASE_SIZE);
            var font = fonts.getFont(key);
            assertThat(font.getSize()).isEqualTo(BASE_SIZE);
            assertThat(font.getFamily()).isEqualTo(SourceSans3Font.FAMILY);
        }
    }

    @Nested
    class SourceSans3FontConstants {

        @Test
        void testStyleConstantsMatchBundledFontFiles() {
            // Each STYLE_* constant is the file name used to load the font from the
            // classpath at /fonts/<name>. A typo in any constant silently falls back to
            // a system font at runtime; this test verifies the resource actually exists.
            assertAll(
                () -> assertThat(fontResourceExists(SourceSans3Font.STYLE_REGULAR))
                    .as("STYLE_REGULAR font resource must exist").isTrue(),
                () -> assertThat(fontResourceExists(SourceSans3Font.STYLE_ITALIC))
                    .as("STYLE_ITALIC font resource must exist").isTrue(),
                () -> assertThat(fontResourceExists(SourceSans3Font.STYLE_SEMIBOLD))
                    .as("STYLE_SEMIBOLD font resource must exist").isTrue(),
                () -> assertThat(fontResourceExists(SourceSans3Font.STYLE_SEMIBOLD_ITALIC))
                    .as("STYLE_SEMIBOLD_ITALIC font resource must exist").isTrue(),
                () -> assertThat(fontResourceExists(SourceSans3Font.STYLE_BOLD))
                    .as("STYLE_BOLD font resource must exist").isTrue(),
                () -> assertThat(fontResourceExists(SourceSans3Font.STYLE_BOLD_ITALIC))
                    .as("STYLE_BOLD_ITALIC font resource must exist").isTrue()
            );
        }

        private boolean fontResourceExists(String filename) {
            try (var stream = SourceSans3Font.class.getResourceAsStream("/fonts/" + filename)) {
                return stream != null;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
