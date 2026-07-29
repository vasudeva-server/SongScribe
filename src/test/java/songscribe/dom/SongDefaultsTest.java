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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class SongDefaultsTest extends UnitTest {

    // -----------------------------------------------------------------------
    // clearTempoIfOrphaned
    // -----------------------------------------------------------------------

    /**
     * Adds a crotchet with a TempoChangeAttachment to the given line,
     * which must already belong to song and have mutation tracking suspended.
     */
    private static StaffElement addNoteWithTempo(Song song, Line line, int bpm) {
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        var attachmentTempo = new Tempo();
        attachmentTempo.setVisibleTempo(bpm);
        note.addAttachment(new TempoChangeAttachment(note, attachmentTempo));
        return note;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ClearTempoIfOrphaned {

        // Branch 1: element is first of first line → song tempo is cleared,
        // regardless of whether other per-note changes exist.
        @Test
        void testClearsSongTempoWhenElementIsFirstOfFirstLine() {
            var song = new Song();
            song.setTempo(new Tempo());
            // addNoteWithTempo inserts before the terminal, so the note lands at index 0.
            song.withoutMutationTracking(() -> {
                var firstLine = song.getLine(0);
                var note = addNoteWithTempo(song, firstLine, 100);
                // note is at index 0 of the first line (inserted before the terminal).
                song.clearTempoIfOrphaned(note);
            });

            assertThat(song.getTempo()).isNull();
        }

        // Branch 2: element is NOT first-of-first-line AND no per-note tempo changes
        // exist anywhere → song tempo is cleared.
        // (Simulates the state after the caller has already removed the attachment.)
        @Test
        void testClearsSongTempoWhenNoPerNoteChangesRemain() {
            var song = new Song();
            song.setTempo(new Tempo());
            song.withoutMutationTracking(() -> {
                var secondLine = new Line(song);
                song.addLine(secondLine);

                // Note with no TempoChangeAttachment — no per-note tempo changes anywhere.
                var note = ElementType.CROTCHET.newInstance();
                secondLine.addElement(note);

                // isFirstElement=false (line index 1), hasAnyTempoChange()=false → clears.
                song.clearTempoIfOrphaned(note);
            });

            assertThat(song.getTempo()).isNull();
        }

        // Branch 3: element is NOT first-of-first-line AND other per-note tempo
        // changes still exist → song tempo is NOT cleared.
        @Test
        void testKeepsSongTempoWhenOtherPerNoteChangesRemain() {
            var song = new Song();
            song.setTempo(new Tempo());
            song.withoutMutationTracking(() -> {
                var firstLine = song.getLine(0);
                // Add a note with a tempo change on the first line.
                addNoteWithTempo(song, firstLine, 120);

                var secondLine = new Line(song);
                song.addLine(secondLine);

                // Add a plain note (no attachment) to the second line.
                var plainNote = ElementType.CROTCHET.newInstance();
                secondLine.addElement(plainNote);

                // isFirstElement=false (line 1), hasAnyTempoChange()=true (firstLine note) → kept.
                song.clearTempoIfOrphaned(plainNote);
            });

            assertThat(song.getTempo()).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // applyLineDefaults
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyLineDefaults {

        @Test
        void testDefaultKeyAppliedWhenCountZeroAndTypeNull() {
            var song = new Song();
            var line = new Line(song);
            song.withoutMutationTracking(() -> {
                line.setKeyAccidentalCount(0);
                line.setKeyType(null);
                song.addLine(line);
            });

            assertThat(line.getKeyAccidentalCount()).isEqualTo(song.getDefaultKeyAccidentalCount());
            assertThat(line.getKeyType()).isEqualTo(song.getDefaultKeyType());
        }

        @Test
        void testDefaultKeyNotAppliedWhenCountNonZero() {
            var song = new Song();
            var line = new Line(song);
            song.withoutMutationTracking(() -> {
                line.setKeyAccidentalCount(2);
                line.setKeyType(KeyType.SHARPS);
                song.addLine(line);
            });

            assertThat(line.getKeyAccidentalCount()).isEqualTo(2);
            assertThat(line.getKeyType()).isEqualTo(KeyType.SHARPS);
        }

    }

    // -----------------------------------------------------------------------
    // Standard defaults
    // -----------------------------------------------------------------------

    @Test
    void testDefaultAttribution() {
        var song = new Song();
        assertThat(song.getComposer()).isEqualTo(Song.SRI_CHINMOY);
        assertThat(song.getLyricist()).isEqualTo(Song.SRI_CHINMOY);
        assertThat(song.getLyricsSource()).isEqualTo(Song.LyricsSource.LYRICIST);
        assertThat(song.isArrangement()).isFalse();
    }

    @Test
    void testDefaultKeySignature() {
        var song = new Song();
        assertThat(song.getDefaultKeyAccidentalCount()).isEqualTo(5);
        assertThat(song.getDefaultKeyType()).isEqualTo(KeyType.FLATS);
    }

    @Test
    void testDefaultTempo() {
        var song = new Song();
        assertThat(song.getTempo()).isNull();
        var tempo = song.getEffectiveTempo();
        assertThat(tempo.getVisibleTempo()).isEqualTo(Tempo.DEFAULT_BPM);
        assertThat(tempo.getTempoType()).isEqualTo(Tempo.DEFAULT_TYPE);
        assertThat(tempo.getTempoDescription()).isEqualTo(Tempo.DEFAULT_DESCRIPTION);
        assertThat(tempo.shouldShowTempo()).isEqualTo(Tempo.DEFAULT_SHOW_TEMPO);
    }

    // -----------------------------------------------------------------------
    // getTempoAt
    // -----------------------------------------------------------------------

    @Test
    void testGetTempoAtReturnsMostRecentTempoChangeWalkingBackward() {
        var song = new Song();

        // Build a 2-line song, each line carrying one per-note tempo change.
        // Line 0: [noteA(tempo=80)]  (its terminal barline migrates to line 1 on add)
        // Line 1: [noteB(tempo=100), plainNote, terminal]
        //
        // Each assertion below walks backward *within a single line* to the most
        // recent change: the line-1 query skips the trailing plainNote/terminal and
        // lands on noteB (100); the line-0 query lands on noteA (80). The
        // walk-back-to-an-earlier-line path is covered separately by
        // testGetTempoAtWalksBackToEarlierLineWhenLineHasNoChange.
        //
        // We use withoutMutationTracking so setup doesn't fire global notifications.
        final int LINE_0_TEMPO_BPM = 80;
        final int LINE_1_TEMPO_BPM = 100;

        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            addNoteWithTempo(song, line0, LINE_0_TEMPO_BPM); // index 0 on line 0

            var line1 = new Line(song);
            song.addLine(line1);
            addNoteWithTempo(song, line1, LINE_1_TEMPO_BPM); // index 0 on line 1
            // Plain note (no attachment) at index 1 on line 1.
            line1.addElement(ElementType.CROTCHET.newInstance());
        });

        // Walked back from (line=1, note=1): plain note has none, noteB(index 0) has 100.
        var line1NoteCount = song.getLine(1).elementCount();
        assertThat(song.getTempoAt(1, line1NoteCount - 1))
            .extracting(Tempo::getVisibleTempo)
            .isEqualTo(LINE_1_TEMPO_BPM);

        // Walked back from (line=0, note=0): noteA has 80.
        assertThat(song.getTempoAt(0, 0))
            .extracting(Tempo::getVisibleTempo)
            .isEqualTo(LINE_0_TEMPO_BPM);
    }

    @Test
    void testGetTempoAtFallsBackToEffectiveTempoWhenNoChangeFound() {
        var song = new Song();
        // No per-note tempo changes and no explicit song tempo; falls back to the default.
        assertThat(song.getTempoAt(0, 0))
            .extracting(Tempo::getVisibleTempo)
            .isEqualTo(Tempo.DEFAULT_BPM);

        // With an explicit song tempo set, the returned object IS that same tempo.
        song.setTempo(new Tempo());
        assertThat(song.getTempoAt(0, 0)).isSameAs(song.getTempo());
    }

    // Exercises the cross-line continuation: when the queried line carries no tempo
    // change, the search resets lastLine and walks into the previous line. Line 1
    // has only a plain note, so getTempoAt(1, 0) must walk back to line 0's noteA.
    @Test
    void testGetTempoAtWalksBackToEarlierLineWhenLineHasNoChange() {
        var song = new Song();
        final int LINE_0_TEMPO_BPM = 80;

        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            addNoteWithTempo(song, line0, LINE_0_TEMPO_BPM); // tempo change on line 0

            // Second line carries no tempo change anywhere.
            var line1 = new Line(song);
            song.addLine(line1);
            line1.addElement(ElementType.CROTCHET.newInstance());
        });

        // Walk from (line=1, note=0): plain note has none → cross to line 0 → noteA (80).
        assertThat(song.getTempoAt(1, 0))
            .extracting(Tempo::getVisibleTempo)
            .isEqualTo(LINE_0_TEMPO_BPM);
    }

    // getTempoAt reports the tempo in effect AT the queried position: the backward
    // walk starts at noteIndex, so a tempo change at a LATER index on the same line
    // must be ignored. Without this, querying note 0 would wrongly pick up note 1's
    // change.
    @Test
    void testGetTempoAtIgnoresLaterChangeOnSameLine() {
        var song = new Song();
        final int EARLY_TEMPO_BPM = 80;
        final int LATER_TEMPO_BPM = 100;

        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            addNoteWithTempo(song, line0, EARLY_TEMPO_BPM); // index 0
            addNoteWithTempo(song, line0, LATER_TEMPO_BPM); // index 1 (later position)
        });

        // Querying index 0 must see the early change, never the later one at index 1.
        assertThat(song.getTempoAt(0, 0))
            .extracting(Tempo::getVisibleTempo)
            .isEqualTo(EARLY_TEMPO_BPM);
    }

    // -----------------------------------------------------------------------
    // hasAnyTempoChange
    // -----------------------------------------------------------------------

    @Test
    void testHasAnyTempoChangeFalseOnFreshSong() {
        var song = new Song();
        assertThat(song.hasAnyTempoChange()).isFalse();
    }

    @Test
    void testHasAnyTempoChangeTrueAfterAttachingTempoChange() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            addNoteWithTempo(song, line0, 90);
        });

        assertThat(song.hasAnyTempoChange()).isTrue();
    }

    @Test
    void testNewSongHasOneLine() {
        var song = new Song();
        assertThat(song.lineCount()).isEqualTo(1);
    }

    // T50: New song — initial line has exactly one element: FINAL_DOUBLE_BARLINE.
    @Test
    void testNewSongInitialLineHasFinalBarline() {
        var song = new Song();
        var line = song.getLine(0);
        assertThat(line.elementCount()).isEqualTo(1);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    // T51: New song — not marked modified after construction.
    @Test
    void testNewSongIsNotModified() {
        var song = new Song();
        assertThat(song.isModified()).isFalse();
    }

    /**
     * A song built without the running application — by a test, the corpus generator, or
     * any headless tool — still has to be layoutable. The UI installs a preference-aware
     * width provider at startup; with nothing installed, the fallback stands in. If it
     * were zero instead, no content would fit on the staff and every line would fail
     * layout, so the document would open as a "Line Too Full" warning rather than a song.
     */
    @Test
    void testNewSongGetsTheFallbackLineWidthWhenNoProviderIsInstalled() {
        assertThat(new Song().getLineWidthSs())
            .as("a song built outside the app must be born layoutable, never zero-width")
            .isEqualTo(Song.FALLBACK_LINE_WIDTH_SS);
    }

    // -----------------------------------------------------------------------
    // Active verse — the one language a song shows at a time
    // -----------------------------------------------------------------------

    // Verse numbering is 1-based, so these are the two values just below the first real verse.
    private static final int VERSE_ZERO = 0;
    private static final int NEGATIVE_VERSE = -1;
    private static final int SECOND_VERSE = 2;

    @Test
    void testNewSongOpensOnItsFirstVerse() {
        assertThat(new Song().getActiveVerse()).isEqualTo(Lyric.FIRST_VERSE);
    }

    // Verse 0 names no verse at all. Accepting it would leave layout looking up a verse no lyric
    // carries, so every syllable in the song would read as absent and the score would show no words.
    @Test
    void testSetActiveVerseRejectsVerseZero() {
        var song = new Song();

        assertThatThrownBy(() -> song.setActiveVerse(VERSE_ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(String.valueOf(VERSE_ZERO));
        assertThat(song.getActiveVerse())
            .as("a rejected verse must leave the song on the one it was showing")
            .isEqualTo(Lyric.FIRST_VERSE);
    }

    @Test
    void testSetActiveVerseRejectsANegativeVerse() {
        var song = new Song();

        assertThatThrownBy(() -> song.setActiveVerse(NEGATIVE_VERSE))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(song.getActiveVerse()).isEqualTo(Lyric.FIRST_VERSE);
    }

    // The guard rejects below the first verse, not at or below it: verse 1 is the default and has
    // to stay selectable, including after switching away and back.
    @Test
    void testSetActiveVerseAcceptsTheFirstVerseAndAnyVerseAboveIt() {
        var song = new Song();

        song.setActiveVerse(SECOND_VERSE);
        assertThat(song.getActiveVerse()).isEqualTo(SECOND_VERSE);

        song.setActiveVerse(Lyric.FIRST_VERSE);
        assertThat(song.getActiveVerse()).isEqualTo(Lyric.FIRST_VERSE);
    }

}
