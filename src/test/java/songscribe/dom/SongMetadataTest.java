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
package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

/**
 * Unit tests for {@link SongMetadata} compact-constructor normalization,
 * {@link SongMetadata#connectorFor}, and {@link Song#showTranslation}.
 * <p>
 * The normalization pipeline (from the class-level comment) is:
 * <pre>
 *   title    : stripLinefeeds -> collapseMultipleSpaces -> processText
 *   place    : trim
 *   year     : trim
 *   number   : trim
 *   composer : coercePerson  (trim; empty -> SRI_CHINMOY)
 *   lyricist : coercePerson  (trim; empty -> SRI_CHINMOY)
 *   month / day / lyricsSource / arrangement / unofficialTranslation : as-is
 * </pre>
 */
class SongMetadataTest extends UnitTest {

    @AfterEach
    void restorePrefs() {
        Prefs.reset(PrefsKey.STRIP_SHORT_A);
    }

    // -----------------------------------------------------------------------
    // Convenience factory
    // -----------------------------------------------------------------------

    /** Builds a {@link SongMetadata} with only title set; all other fields at canonical defaults. */
    private static SongMetadata withTitle(String title) {
        return new SongMetadata(
            title, "", "", "", 0, 0,
            Song.SRI_CHINMOY, Song.SRI_CHINMOY,
            Song.LyricsSource.LYRICIST, false, false
        );
    }

    /** Builds a {@link SongMetadata} with only place set; all other fields at canonical defaults. */
    private static SongMetadata withPlace(String place) {
        return new SongMetadata(
            "", "", place, "", 0, 0,
            Song.SRI_CHINMOY, Song.SRI_CHINMOY,
            Song.LyricsSource.LYRICIST, false, false
        );
    }

    /** Builds a {@link SongMetadata} with only year set; all other fields at canonical defaults. */
    private static SongMetadata withYear(String year) {
        return new SongMetadata(
            "", "", "", year, 0, 0,
            Song.SRI_CHINMOY, Song.SRI_CHINMOY,
            Song.LyricsSource.LYRICIST, false, false
        );
    }

    /** Builds a {@link SongMetadata} with only number set; all other fields at canonical defaults. */
    private static SongMetadata withNumber(String number) {
        return new SongMetadata(
            "", number, "", "", 0, 0,
            Song.SRI_CHINMOY, Song.SRI_CHINMOY,
            Song.LyricsSource.LYRICIST, false, false
        );
    }

    /** Builds a {@link SongMetadata} with given composer and lyricist. */
    private static SongMetadata withPersons(String composer, String lyricist) {
        return new SongMetadata(
            "", "", "", "", 0, 0,
            composer, lyricist,
            Song.LyricsSource.LYRICIST, false, false
        );
    }

    // -----------------------------------------------------------------------
    // §1 Title normalization
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TitleNormalization {

        @Test
        void testStripLinefeeds() {
            // Linefeeds in the title are replaced with a space.
            var m = withTitle("Hello\nWorld");
            assertThat(m.title())
                .as("linefeed becomes space")
                .isEqualTo("Hello World");
        }

        @Test
        void testCollapseMultipleSpaces() {
            // Multiple consecutive spaces after a non-space character are collapsed to one.
            var m = withTitle("Hello  World");
            assertThat(m.title())
                .as("multiple spaces collapsed")
                .isEqualTo("Hello World");
        }

        @Test
        void testProcessTextTrimsWhenStripShortAFalse() {
            Prefs.put(PrefsKey.STRIP_SHORT_A, false);
            var m = withTitle("  trimmed  ");
            assertThat(m.title())
                .as("processText trims when STRIP_SHORT_A=false")
                .isEqualTo("trimmed");
        }

        @Test
        void testProcessTextReplacesShortAWhenPrefTrue() {
            Prefs.put(PrefsKey.STRIP_SHORT_A, true);
            // ă and Ă are replaced; whitespace is NOT trimmed when replacement fires.
            var m = withTitle("ăĂ");
            assertThat(m.title())
                .as("ă/Ă replaced with a/A when STRIP_SHORT_A=true")
                .isEqualTo("aA");
        }

        @Test
        void testProcessTextPreservesShortAWhenPrefFalse() {
            Prefs.put(PrefsKey.STRIP_SHORT_A, false);
            // With replacement off, ă/Ă must be preserved verbatim.
            var m = withTitle("ăĂ");
            assertThat(m.title())
                .as("ă/Ă preserved when STRIP_SHORT_A=false")
                .isEqualTo("ăĂ");
        }

        @Test
        void testAllThreePhasesApplied() {
            // Exercises all three pipeline stages with a single input:
            //   "hello\nworld  ă" → strip-LF → "hello world  ă"
            //                     → collapse  → "hello world ă"
            //                     → processText (STRIP_SHORT_A=true) → "hello world a"
            Prefs.put(PrefsKey.STRIP_SHORT_A, true);
            var m = withTitle("hello\nworld  ă");
            assertThat(m.title())
                .as("full pipeline: strip-LF + collapse + replaceShortA")
                .isEqualTo("hello world a");
        }
    }

    // -----------------------------------------------------------------------
    // §2 Scalar field trimming (place, year, number)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ScalarFieldTrimming {

        @Test
        void testPlaceTrimmed() {
            var m = withPlace("  New York  ");
            assertThat(m.place()).as("place is trimmed").isEqualTo("New York");
        }

        @Test
        void testYearTrimmed() {
            var m = withYear("  1984  ");
            assertThat(m.year()).as("year is trimmed").isEqualTo("1984");
        }

        @Test
        void testNumberTrimmed() {
            var m = withNumber("  42  ");
            assertThat(m.number()).as("number is trimmed").isEqualTo("42");
        }

        @Test
        void testPlaceEmptyAfterTrimRemainsEmpty() {
            var m = withPlace("   ");
            assertThat(m.place()).as("all-whitespace place normalizes to empty").isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // §3 coercePerson: empty → SRI_CHINMOY
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CoercePerson {

        @Test
        void testEmptyComposerCoercedToSriChinmoy() {
            var m = withPersons("", Song.SRI_CHINMOY);
            assertThat(m.composer())
                .as("empty composer coerced to SRI_CHINMOY")
                .isEqualTo(Song.SRI_CHINMOY);
        }

        @Test
        void testWhitespaceOnlyComposerCoercedToSriChinmoy() {
            var m = withPersons("   ", Song.SRI_CHINMOY);
            assertThat(m.composer())
                .as("all-whitespace composer coerced to SRI_CHINMOY")
                .isEqualTo(Song.SRI_CHINMOY);
        }

        @Test
        void testEmptyLyricistCoercedToSriChinmoy() {
            var m = withPersons(Song.SRI_CHINMOY, "");
            assertThat(m.lyricist())
                .as("empty lyricist coerced to SRI_CHINMOY")
                .isEqualTo(Song.SRI_CHINMOY);
        }

        @Test
        void testWhitespaceOnlyLyricistCoercedToSriChinmoy() {
            var m = withPersons(Song.SRI_CHINMOY, "   ");
            assertThat(m.lyricist())
                .as("all-whitespace lyricist coerced to SRI_CHINMOY")
                .isEqualTo(Song.SRI_CHINMOY);
        }

        @Test
        void testNonEmptyPersonPreserved() {
            var m = withPersons("Bach", "Mozart");
            assertThat(m.composer()).as("non-empty composer preserved").isEqualTo("Bach");
            assertThat(m.lyricist()).as("non-empty lyricist preserved").isEqualTo("Mozart");
        }

        @Test
        void testPersonNameTrimmedBeforeCoercion() {
            var m = withPersons("  John  ", "  Jane  ");
            assertThat(m.composer()).as("composer whitespace trimmed").isEqualTo("John");
            assertThat(m.lyricist()).as("lyricist whitespace trimmed").isEqualTo("Jane");
        }
    }

    // -----------------------------------------------------------------------
    // §4 Idempotency
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Idempotency {

        @Test
        void testNormalizingAlreadyNormalizedTitleIsNoOp() {
            // Start from a raw title that actually exercises the strip path
            // (contains ă) so the first pass transforms it; the second pass over
            // the already-normalized result must be a no-op (the ă→a replacement
            // must not re-fire or otherwise change the title).
            Prefs.put(PrefsKey.STRIP_SHORT_A, true);
            var first = withTitle("hello world ă");
            assertThat(first.title())
                .as("first pass strips ă to a")
                .isEqualTo("hello world a");
            var second = withTitle(first.title());
            assertThat(second.title())
                .as("normalizing an already-normalized title is a no-op")
                .isEqualTo(first.title());
        }

        @Test
        void testNormalizingAlreadyNormalizedPersonIsNoOp() {
            var first = withPersons("Bach", "Mozart");
            var second = withPersons(first.composer(), first.lyricist());
            assertThat(second.composer())
                .as("normalizing an already-normalized composer is a no-op")
                .isEqualTo(first.composer());
            assertThat(second.lyricist())
                .as("normalizing an already-normalized lyricist is a no-op")
                .isEqualTo(first.lyricist());
        }

        @Test
        void testNormalizingAlreadyNormalizedPlaceIsNoOp() {
            var first = withPlace("New York");
            var second = withPlace(first.place());
            assertThat(second.place())
                .as("normalizing an already-normalized place is a no-op")
                .isEqualTo(first.place());
        }

        @Test
        void testNormalizingDefaultRecordIsNoOp() {
            // The default SongMetadata from a new Song is already normalized.
            // Constructing a new record from its own fields must yield an equal record.
            var song = new Song();
            var original = song.getMetadata();
            var copy = new SongMetadata(
                original.title(), original.number(), original.place(), original.year(),
                original.month(), original.day(),
                original.composer(), original.lyricist(),
                original.lyricsSource(), original.arrangement(),
                original.unofficialTranslation()
            );
            assertThat(copy)
                .as("round-tripping default metadata through the constructor is a no-op")
                .isEqualTo(original);
        }
    }

    // -----------------------------------------------------------------------
    // §5 connectorFor
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ConnectorFor {

        @Test
        void testSriChinmoyReturnsBy() {
            // When the person is Sri Chinmoy, connectorFor always returns " by "
            // regardless of the lyricsSource.
            for (var source : Song.LyricsSource.values()) {
                var m = new SongMetadata(
                    "", "", "", "", 0, 0,
                    Song.SRI_CHINMOY, Song.SRI_CHINMOY,
                    source, false, false
                );
                assertThat(m.connectorFor(Song.SRI_CHINMOY))
                    .as("connectorFor SriChinmoy with source %s", source)
                    .isEqualTo(" by ");
            }
        }

        @Test
        void testNonSriChinmoyUsesLyricsSourceConnector() {
            // When the person is not Sri Chinmoy, the connector comes from lyricsSource.
            // LYRICIST's connector happens to equal the Sri Chinmoy connector
            // (" by "), so assert against the source connector to express that
            // the value is routed through lyricsSource. The TEXT and OTHER cases
            // below catch a mis-route that this coincidence would otherwise hide.
            var mLyricist = new SongMetadata(
                "", "", "", "", 0, 0,
                "Bach", Song.SRI_CHINMOY,
                Song.LyricsSource.LYRICIST, false, false
            );
            assertThat(mLyricist.connectorFor("Bach"))
                .as("LYRICIST connector comes from lyricsSource")
                .isEqualTo(Song.LyricsSource.LYRICIST.getConnector());

            var mText = new SongMetadata(
                "", "", "", "", 0, 0,
                "Bach", Song.SRI_CHINMOY,
                Song.LyricsSource.TEXT, false, false
            );
            assertThat(mText.connectorFor("Bach"))
                .as("TEXT connector")
                .isEqualTo(" from ");

            var mOther = new SongMetadata(
                "", "", "", "", 0, 0,
                "Bach", Song.SRI_CHINMOY,
                Song.LyricsSource.OTHER, false, false
            );
            assertThat(mOther.connectorFor("Bach"))
                .as("OTHER connector")
                .isEqualTo(": ");
        }
    }

    // -----------------------------------------------------------------------
    // §6 showTranslation (all four combos of unofficialTranslation × translatedLyrics)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ShowTranslation {

        /**
         * Builds a Song with the given unofficialTranslation flag and translatedLyrics string.
         * The flag is stored in the SongMetadata record; translatedLyrics is stored on Song.
         */
        private Song songWith(boolean unofficialTranslation, String translatedLyrics) {
            var song = new Song();
            var current = song.getMetadata();
            song.setMetadata(new SongMetadata(
                current.title(), current.number(), current.place(), current.year(),
                current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(),
                unofficialTranslation
            ));
            song.setTranslatedLyrics(translatedLyrics);
            return song;
        }

        /** official translation, empty translatedLyrics → false (no text to show). */
        @Test
        void testOfficialEmptyLyricsReturnsFalse() {
            var song = songWith(false, "");
            assertThat(song.showTranslation())
                .as("unofficialTranslation=false, translatedLyrics=empty → false")
                .isFalse();
        }

        /** official translation, non-empty translatedLyrics → true. */
        @Test
        void testOfficialNonEmptyLyricsReturnsTrue() {
            var song = songWith(false, "some translation");
            assertThat(song.showTranslation())
                .as("unofficialTranslation=false, translatedLyrics=non-empty → true")
                .isTrue();
        }

        /** unofficial translation, empty translatedLyrics → false. */
        @Test
        void testUnofficialEmptyLyricsReturnsFalse() {
            var song = songWith(true, "");
            assertThat(song.showTranslation())
                .as("unofficial=true, translatedLyrics=empty → false")
                .isFalse();
        }

        /** unofficial translation, non-empty translatedLyrics → false
         *  (unofficial translations are never credited). */
        @Test
        void testUnofficialNonEmptyLyricsReturnsFalse() {
            var song = songWith(true, "some translation");
            assertThat(song.showTranslation())
                .as("unofficial=true, translatedLyrics=non-empty → false")
                .isFalse();
        }
    }
}
