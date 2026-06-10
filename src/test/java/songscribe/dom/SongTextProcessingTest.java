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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.SongMetadata;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

/**
 * Tests for Song text-processing methods: normalizeTitle and processText (exercised
 * through the public setters that delegate to them).
 */
class SongTextProcessingTest extends UnitTest {

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    @AfterEach
    void tearDown() {
        // Restore the STRIP_SHORT_A pref to its default so other tests are not affected.
        Prefs.reset(PrefsKey.STRIP_SHORT_A);
    }

    // -----------------------------------------------------------------------
    // normalizeTitle (tested via setTitle / getTitle)
    // -----------------------------------------------------------------------

    // normalizeTitle applies three transformations in sequence:
    //   1. stripLinefeeds  — replaces '\n' with a space
    //   2. collapseMultipleSpaces — collapses runs of spaces that follow a non-space char
    //   3. processText     — if STRIP_SHORT_A: replaces ă/Ă with a/A; else: trims
    //
    // A single input can exercise all three branches at once:
    //   "hello\nworld  ă" → strip-LF → "hello world  ă"
    //                     → collapse  → "hello world ă"
    //                     → processText (STRIP_SHORT_A=true, has ă) → "hello world a"

    @Test
    void testNormalizeTitleStripsLinefeedsCollapsesSpacesAndReplacesShortA() {
        // Ensure STRIP_SHORT_A is true so all three transformations are exercised.
        Prefs.put(PrefsKey.STRIP_SHORT_A, true);

        // "hello\nworld  ă": has linefeed, double-space before ă, and a short-ă char.
        // Normalization is performed by the SongMetadata compact constructor.
        var current = song.getMetadata();
        song.setMetadata(new SongMetadata(
            "hello\nworld  ă",
            current.number(), current.place(), current.year(),
            current.month(), current.day(),
            current.composer(), current.lyricist(),
            current.lyricsSource(), current.arrangement(),
            current.unofficialTranslation()
        ));

        assertThat(song.getTitle()).isEqualTo("hello world a");
    }

    // -----------------------------------------------------------------------
    // processText (tested via setUnderLyrics / getUnderLyrics)
    // -----------------------------------------------------------------------

    // Branch 1: STRIP_SHORT_A=true AND text contains ă/Ă → replace, no trim.
    @Test
    void testProcessTextReplacesShortAWhenPrefIsTrue() {
        Prefs.put(PrefsKey.STRIP_SHORT_A, true);

        // Text has both lowercase ă and uppercase Ă with leading/trailing whitespace.
        // When replacement fires, trim is skipped — whitespace is preserved.
        song.setUnderLyrics(" ăĂ ");

        assertThat(song.getUnderLyrics()).isEqualTo(" aA ");
    }

    // Branch 2: STRIP_SHORT_A=false (or text has no ă) → return text.trim().
    @Test
    void testProcessTextTrimsWhenPrefIsFalse() {
        Prefs.put(PrefsKey.STRIP_SHORT_A, false);

        song.setUnderLyrics("  trim me  ");

        assertThat(song.getUnderLyrics()).isEqualTo("trim me");
    }

    // Branch 2 variant: STRIP_SHORT_A=true but text contains no ă → trim only.
    @Test
    void testProcessTextTrimsWhenStripPrefTrueButNoShortA() {
        Prefs.put(PrefsKey.STRIP_SHORT_A, true);

        song.setUnderLyrics("  no special chars  ");

        assertThat(song.getUnderLyrics()).isEqualTo("no special chars");
    }
}
