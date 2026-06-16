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
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

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
        void testAllRolesMappedToCorrectPrefsKey() {
            // Build fonts via defaultsFromPrefs() and verify that each FontKey reads
            // the expected PrefsKey pair. This catches a cut-paste bug where, e.g.,
            // TITLE uses LYRICS_FONT_SIZE or LYRICS uses TITLE_FONT — bugs the size-only
            // test cannot catch when sizes happen to differ only slightly.
            var fonts = DocumentFonts.defaultsFromPrefs();

            // Each role must produce the exact same font that setFont(key, psName, size)
            // would produce from the corresponding prefs pair — verified role by role.
            var expected = new DocumentFonts();
            expected.setFont(FontKey.TITLE,           Prefs.getString(PrefsKey.TITLE_FONT),           Prefs.getInt(PrefsKey.TITLE_FONT_SIZE));
            expected.setFont(FontKey.SUBTITLE,        Prefs.getString(PrefsKey.SUBTITLE_FONT),        Prefs.getInt(PrefsKey.SUBTITLE_FONT_SIZE));
            expected.setFont(FontKey.LYRICS,          Prefs.getString(PrefsKey.LYRICS_FONT),          Prefs.getInt(PrefsKey.LYRICS_FONT_SIZE));
            expected.setFont(FontKey.ATTRIBUTION,     Prefs.getString(PrefsKey.ATTRIBUTION_FONT),     Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE));
            expected.setFont(FontKey.SUB_ATTRIBUTION, Prefs.getString(PrefsKey.SUB_ATTRIBUTION_FONT), Prefs.getInt(PrefsKey.SUB_ATTRIBUTION_FONT_SIZE));
            expected.setFont(FontKey.ANNOTATION,      Prefs.getString(PrefsKey.ANNOTATION_FONT),      Prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE));
            expected.setFont(FontKey.FOOTNOTE,        Prefs.getString(PrefsKey.FOOTNOTE_FONT),        Prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE));
            expected.setFont(FontKey.BANGLA,          Prefs.getString(PrefsKey.BANGLA_FONT),          Prefs.getInt(PrefsKey.BANGLA_FONT_SIZE));

            assertThat(fonts).isEqualTo(expected);
        }
    }

    /**
     * T4: The built-in subtitle default is {@code SourceSans3SongScribe-Medium} at 24 pt,
     * the value an old file that lacks a {@code <subtitlefont>} tag falls back to. This
     * pins the value declared in {@code defaults.json} via
     * {@link DocumentFonts#defaultsFromSystemDefaults()}, which reads the built-in
     * defaults only — independent of any user-preference override — so a regression in
     * {@code defaults.json} or the {@code PrefsKey} mapping is caught on every machine.
     */
    @Nested
    class SubtitleDefault {

        private static final int SUBTITLE_DEFAULT_SIZE = 24;
        private static final String SUBTITLE_DEFAULT_PS_NAME = "SourceSans3SongScribe-Medium";

        @BeforeAll
        static void installFonts() {
            // Source Sans 3 SongScribe must be registered for setFont(key, psName, size)
            // to resolve the Medium face by PostScript name instead of falling back.
            MyFontUtils.resetFontCache();
            SourceSans3Font.install();
        }

        @Test
        void testSubtitleDefaultsToMedium24() {
            // Read the built-in (non-overridable) default, the value used when an old
            // file has no <subtitlefont> tag. Assert it is exactly Medium 24.
            var fonts = DocumentFonts.defaultsFromSystemDefaults();
            var subtitleFont = fonts.getFont(FontKey.SUBTITLE);

            assertThat(subtitleFont.getSize())
                .as("SUBTITLE built-in default size must be 24")
                .isEqualTo(SUBTITLE_DEFAULT_SIZE);
            assertThat(subtitleFont.getPSName())
                .as("SUBTITLE built-in default must be the Medium weight")
                .isEqualTo(SUBTITLE_DEFAULT_PS_NAME);
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
            assertThat(fullyPopulated()).isNotEqualTo("not a DocumentFonts");
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
            // Reset the lazy font cache so we can install bundled fonts before the first
            // load, then install Source Sans 3 SongScribe so that setFont(key, psName,
            // size) resolves real PS names from the bundled font files rather than falling
            // back to a UI font.
            MyFontUtils.resetFontCache();
            SourceSans3Font.install();
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
            // An old Source Sans Pro SongScribe PS name is no longer registered, so it
            // resolves through the weight-matched fallback. The result must still be the
            // Source Sans 3 SongScribe family, not a fallback system font.
            var fonts = new DocumentFonts();
            fonts.setFont(key, "SourceSansProSongScribe-Regular", BASE_SIZE);
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
                () -> assertFontResourceExists(SourceSans3Font.STYLE_REGULAR),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_ITALIC),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_MEDIUM),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_MEDIUM_ITALIC),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_SEMIBOLD),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_SEMIBOLD_ITALIC),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_BOLD),
                () -> assertFontResourceExists(SourceSans3Font.STYLE_BOLD_ITALIC)
            );
        }

        /**
         * Asserts the bundled font file is present on the classpath at
         * {@code /fonts/<filename>}. Opens the stream directly so the failure message
         * names the missing resource, and lets any {@link IOException} from closing
         * propagate rather than masking a genuine I/O failure as "not found".
         */
        private void assertFontResourceExists(String filename) throws IOException {
            var resourcePath = "/fonts/" + filename;
            try (var stream = SourceSans3Font.class.getResourceAsStream(resourcePath)) {
                assertThat(stream).as("font resource %s must exist", resourcePath).isNotNull();
            }
        }
    }
}
