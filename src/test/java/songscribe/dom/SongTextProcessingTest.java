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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.SongMetadata;

/**
 * Tests for Song text-processing methods: normalizeTitle and processText (exercised
 * through the public setters that delegate to them).
 * <p>
 * processText now applies a single unconditional pipeline: trim → collapse
 * multiple spaces → typographic substitution → short-A stripping when the
 * caller requests it. The old STRIP_SHORT_A preference gate has been removed;
 * short-A stripping is decided by the caller (title/subtitle/underlyrics strip;
 * translation/footnotes do not).
 */
class SongTextProcessingTest extends UnitTest {

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    // -----------------------------------------------------------------------
    // normalizeTitle (tested via setTitle / getTitle)
    // -----------------------------------------------------------------------

    // normalizeTitle applies two transformations in sequence:
    //   1. stripLinefeeds    — replaces '\n' with a space
    //   2. processText(true) — trims, collapses runs of multiple spaces, applies
    //                          typographic substitution, replaces ă/Ă with a/A
    //
    // A single input exercises all stages at once:
    //   "hello\nworld  ă" → strip-LF   → "hello world  ă"
    //                     → processText → "hello world a"

    @Test
    void testNormalizeTitleStripsLinefeedsCollapsesSpacesAndReplacesShortA() {
        // "hello\nworld  ă": has linefeed, double-space before ă, and a short-ă char.
        // Normalization is performed by the SongMetadata compact constructor.
        var current = song.getMetadata();

        song.setMetadata(new SongMetadata(
            "hello\nworld  ă",
            current.number(), current.place(), current.year(),
            current.month(), current.day(),
            current.composer(), current.lyricist(),
            current.lyricsSource(), current.arrangement(),
            current.unofficialTranslation(), current.subtitle(), "", 0, 0
        ));

        assertThat(song.getTitle()).isEqualTo("hello world a");
    }

    // -----------------------------------------------------------------------
    // processText (tested via setUnderLyrics / getUnderLyrics)
    // -----------------------------------------------------------------------

    // processText always: trims, collapses runs of multiple spaces, applies
    // typographic substitution, then replaces ă/Ă with a/A when stripShortA=true
    // (the case for underlyrics).

    @Test
    void testProcessTextReplacesShortAAndTrims() {
        // Text has both lowercase ă and uppercase Ă with leading/trailing whitespace.
        // trim is always applied, so the result has no surrounding whitespace.
        song.setUnderLyrics(" ăĂ ");

        assertThat(song.getUnderLyrics()).isEqualTo("aA");
    }

    @Test
    void testProcessTextTrimsTextWithoutShortA() {
        song.setUnderLyrics("  trim me  ");

        assertThat(song.getUnderLyrics()).isEqualTo("trim me");
    }
}
