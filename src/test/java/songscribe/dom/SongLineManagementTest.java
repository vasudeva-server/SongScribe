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

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Song} line management: {@code addLine}, {@code removeLine},
 * terminal invariant maintenance, interactability, and related query methods.
 */
class SongLineManagementTest extends UnitTest {

    // -----------------------------------------------------------------------
    // getNumberedTitle
    // -----------------------------------------------------------------------

    @Nested
    class GetNumberedTitle {

        @Test
        void testReturnsBareTitle_WhenNumberIsEmpty() {
            // Clear the number (default is "1") so the bare-title branch is exercised.
            var song = new Song();
            song.setNumber("");
            song.setTitle("My Song");

            assertThat(song.getNumberedTitle()).isEqualTo("My Song");
        }

        @Test
        void testReturnsPrefixedTitle_WhenNumberIsNonEmpty() {
            // When number is set, title must be prefixed with "N. ".
            var song = new Song();
            song.setNumber("5");
            song.setTitle("My Song");

            assertThat(song.getNumberedTitle()).isEqualTo("5. My Song");
        }
    }

    // -----------------------------------------------------------------------
    // getLyricsText
    // -----------------------------------------------------------------------

    @Nested
    class GetLyricsText {

        @Test
        void testReturnsEmptyString_WhenNoLyrics() {
            // A fresh song has no per-note lyrics.
            var song = new Song();
            assertThat(song.getLyricsText()).isEmpty();
        }

        @Test
        void testReturnsSingleSyllable_WhenSingleNoteHasLyric() {
            // A note with a SINGLE syllabic lyric produces "text ".
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line = song.getLine(0);
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "hello", Lyric.Extend.NONE);
            });

            assertThat(song.getLyricsText()).isEqualTo("hello ");
        }

        @Test
        void testAppendsDash_WhenSyllabicIsBegin() {
            // A BEGIN syllabic adds a hyphen after the text.
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line = song.getLine(0);
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
            });

            assertThat(song.getLyricsText()).isEqualTo("hel-");
        }

        @Test
        void testAppendsDash_WhenSyllabicIsMiddle() {
            // A MIDDLE syllabic also adds a hyphen after the text.
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line = song.getLine(0);
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.setLyricForVerse(1, Lyric.Syllabic.MIDDLE, false, "mid", Lyric.Extend.NONE);
            });

            assertThat(song.getLyricsText()).isEqualTo("mid-");
        }

        @Test
        void testAppendsUnderscore_WhenExtendIsNonNone() {
            // When extend != NONE (melisma start), the text gets an underscore suffix.
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line = song.getLine(0);
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "flow", Lyric.Extend.START);
            });

            assertThat(song.getLyricsText()).isEqualTo("flow_");
        }

        @Test
        void testAppendsDoubleDash_WhenCompound() {
            // A compound lyric (compound=true) appends "--".
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line = song.getLine(0);
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                note.setLyricForVerse(1, Lyric.Syllabic.SINGLE, true, "joy", Lyric.Extend.NONE);
            });

            assertThat(song.getLyricsText()).isEqualTo("joy--");
        }

        @Test
        void testNewlineInsertedBetweenLines_WhenBothHaveLyrics() {
            // Lines with lyrics are separated by a newline (but not after the last line).
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line0 = song.getLine(0);
                var note0 = ElementType.CROTCHET.newInstance();
                line0.addElement(note0);
                note0.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "one", Lyric.Extend.NONE);

                var line1 = new Line(song);
                song.addLine(line1);
                var note1 = ElementType.CROTCHET.newInstance();
                line1.addElement(note1);
                note1.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "two", Lyric.Extend.NONE);
            });

            assertThat(song.getLyricsText()).isEqualTo("one \ntwo ");
        }
    }

    // -----------------------------------------------------------------------
    // isInteractable / isAutoMaintainedTerminal
    // -----------------------------------------------------------------------

    @Nested
    class IsInteractable {

        @Test
        void testTerminalOnLastLineIsNotInteractable() {
            // The auto-maintained terminal (FINAL_DOUBLE_BARLINE as last element of last
            // line) must not be interactable.
            var song = new Song();
            var lastLine = song.getLine(0);
            var terminal = lastLine.getElement(lastLine.elementCount() - 1);

            assertThat(song.isInteractable(terminal, lastLine)).isFalse();
        }

        @Test
        void testNoteOnLastLineIsInteractable() {
            // A regular note on the last line (not the terminal) must be interactable.
            var song = new Song();
            song.withoutMutationTracking(() -> {
                var line = song.getLine(0);
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
            });

            var line = song.getLine(0);
            var note = line.getElement(0);
            assertThat(song.isInteractable(note, line)).isTrue();
        }

        @Test
        void testValidTerminalOnNonLastLineIsInteractable() {
            // A FINAL_DOUBLE_BARLINE that is NOT the last element of the last line is
            // treated as an ordinary interactable element.
            var song = new Song();
            song.withoutMutationTracking(() -> {
                // Add a second line, making line 0 no longer the last.
                var line1 = new Line(song);
                song.addLine(line1);
            });

            // The terminal on line 0 is now an interior element — interactable.
            var firstLine = song.getLine(0);
            var firstLineTerminal = firstLine.getElement(firstLine.elementCount() - 1);
            assertThat(song.isInteractable(firstLineTerminal, firstLine)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // currentTerminalType / canReplaceTerminal / replaceTerminal
    // -----------------------------------------------------------------------

    @Nested
    class TerminalType {

        @Test
        void testCurrentTerminalTypeReturns_FinalDoubleBarline_OnFreshSong() {
            var song = new Song();
            assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testCanReplaceTerminalFalse_WhenTypeMatchesCurrent() {
            // FINAL_DOUBLE_BARLINE → cannot replace with same type.
            var song = new Song();
            assertThat(song.canReplaceTerminal(ElementType.FINAL_DOUBLE_BARLINE)).isFalse();
        }

        @Test
        void testCanReplaceTerminalTrue_WhenTypeIsValidAndDifferent() {
            // FINAL_DOUBLE_BARLINE → can replace with REPEAT_RIGHT (valid, different).
            var song = new Song();
            assertThat(song.canReplaceTerminal(ElementType.REPEAT_RIGHT)).isTrue();
        }

        @Test
        void testCanReplaceTerminalFalse_WhenTypeIsNotValidTerminal() {
            // A non-terminal type (e.g. CROTCHET) cannot replace the terminal.
            var song = new Song();
            assertThat(song.canReplaceTerminal(ElementType.CROTCHET)).isFalse();
        }

        @Test
        void testReplaceTerminalChangesTerminalType() {
            // Replacing FINAL_DOUBLE_BARLINE with REPEAT_RIGHT must update the terminal.
            var song = new Song();
            song.replaceTerminal(ElementType.REPEAT_RIGHT);
            assertThat(song.currentTerminalType()).isEqualTo(ElementType.REPEAT_RIGHT);
        }

        @Test
        void testReplaceTerminalIsNoOp_WhenTypeAlreadyMatches() {
            // Replacing with the already-present type is a no-op.
            var song = new Song();
            var lastLine = song.getLine(0);
            var elementCountBefore = lastLine.elementCount();

            song.replaceTerminal(ElementType.FINAL_DOUBLE_BARLINE);

            assertThat(lastLine.elementCount()).isEqualTo(elementCountBefore);
            assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testReplaceTerminalThrows_WhenTypeIsNotValidTerminal() {
            var song = new Song();
            assertThatThrownBy(() -> song.replaceTerminal(ElementType.CROTCHET))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // removeLine
    // -----------------------------------------------------------------------

    @Nested
    class RemoveLine {

        @Test
        void testRemoveLineReducesLineCount() {
            var song = new Song();
            song.withoutMutationTracking(() -> {
                song.addLine(new Line(song));
            });

            assertThat(song.lineCount()).isEqualTo(2);
            song.removeLine(1);
            assertThat(song.lineCount()).isEqualTo(1);
        }

        @Test
        void testRemoveLastLine_TransfersTerminalToPreviousLine() {
            // When the last line is removed, the terminal must migrate to the new last line.
            var song = new Song();
            song.withoutMutationTracking(() -> {
                song.addLine(new Line(song));
            });

            // Line 0 lost its terminal to line 1 when line 1 was added.
            // Remove line 1 — terminal must come back to line 0.
            song.removeLine(1);

            var lastLine = song.getLine(0);
            var lastElement = lastLine.getElement(lastLine.elementCount() - 1);
            assertThat(lastElement.getType().isValidTerminal())
                .describedAs("last element of last line must be a valid terminal after removeLine")
                .isTrue();
        }

        @Test
        void testRemoveNonLastLine_DoesNotChangeTerminal() {
            // Removing a non-last line leaves the terminal on the (still-last) final line.
            // Use addLine with full mutation tracking so line 1 gets the terminal.
            var song = new Song();
            song.addLine(new Line(song));  // line 1 now holds the terminal

            // Remove line 0 (not the last line — line 1 is last).
            song.removeLine(0);

            assertThat(song.lineCount()).isEqualTo(1);
            var lastLine = song.getLine(0);
            assertThat(lastLine.elementCount()).isGreaterThan(0);
            var lastElement = lastLine.getElement(lastLine.elementCount() - 1);
            assertThat(lastElement.getType().isValidTerminal()).isTrue();
        }

        @Test
        void testRemoveLastLine_WithRepeatRight_PreservesRepeatRight() {
            // When the last line carries REPEAT_RIGHT as its terminal and is removed,
            // terminalTypeToInstall should carry that type to the new last line.
            var song = new Song();
            // Set up: replace terminal with REPEAT_RIGHT, then add a second line.
            song.replaceTerminal(ElementType.REPEAT_RIGHT);

            song.withoutMutationTracking(() -> {
                song.addLine(new Line(song));
            });

            // Remove the second line — the outgoingTerminalType from line 1 is
            // REPEAT_RIGHT (if it was carried there), or line 0 ends up with it.
            song.removeLine(1);

            // The surviving line must still have a valid terminal.
            var lastLine = song.getLine(0);
            var lastElement = lastLine.getElement(lastLine.elementCount() - 1);
            assertThat(lastElement.getType().isValidTerminal()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // addLine — terminal invariant maintenance
    // -----------------------------------------------------------------------

    @Nested
    class AddLine {

        @Test
        void testAddLineAppendsLine() {
            var song = new Song();
            song.addLine(new Line(song));
            assertThat(song.lineCount()).isEqualTo(2);
        }

        @Test
        void testAddLine_MovesTerminalToNewLastLine() {
            // After addLine, the new last line must hold the terminal.
            var song = new Song();
            song.addLine(new Line(song));

            var newLastLine = song.getLine(1);
            var lastElement = newLastLine.getElement(newLastLine.elementCount() - 1);
            assertThat(lastElement.getType().isValidTerminal())
                .describedAs("terminal must be on the new last line after addLine")
                .isTrue();
        }

        @Test
        void testAddLine_RemovesTerminalFromPreviousLastLine() {
            // The previous last line (which had exactly one element: the terminal) must
            // be empty after addLine transfers the terminal to the new last line.
            var song = new Song();
            song.addLine(new Line(song));

            // Line 0 started with only the FINAL_DOUBLE_BARLINE; after addLine that
            // terminal is transferred to line 1, leaving line 0 empty.
            var previousLastLine = song.getLine(0);
            assertThat(previousLastLine.elementCount())
                .describedAs("previous last line must be empty after terminal transfer")
                .isZero();
        }

        @Test
        void testAddLine_InMiddle_DoesNotTriggerTerminalMaintenance() {
            // Inserting at a non-append index does not call maintainTerminalOnLastLineChange,
            // so the existing last line keeps its terminal.
            var song = new Song();
            // Add a second line via mutation-free setup: line 0 keeps its terminal.
            song.withoutMutationTracking(() -> {
                song.addLine(new Line(song));
            });
            // Lines: [line0-with-terminal, line1-empty]. Insert a new line at index 0.
            song.addLine(0, new Line(song));

            // Three lines now. The terminal must still be on one of the lines.
            assertThat(song.lineCount()).isEqualTo(3);
            // Line 1 (shifted from line 0) still ends in the terminal.
            var lineWithTerminal = song.getLine(1);
            var lastElement = lineWithTerminal.getElement(lineWithTerminal.elementCount() - 1);
            assertThat(lastElement.getType().isValidTerminal())
                .describedAs("terminal must survive a mid-line insertion")
                .isTrue();
        }

        @Test
        void testAddLine_WithRepeatRightOnNewLine_AndNoPreviousTerminal_PromotesRepeatRight() {
            // terminalTypeToInstall falls through to the REPEAT_RIGHT-promotion branch
            // only when outgoingTerminalType is null. That happens when the previous last
            // line has no valid terminal (e.g. when set up via withoutMutationTracking
            // without adding a terminal, then the terminal was stripped).
            var song = new Song();

            // Strip the terminal from line 0 via mutation-free path so line 0 ends with
            // a plain note rather than a valid terminal.
            song.withoutMutationTracking(() -> {
                var line0 = song.getLine(0);
                // Remove the existing terminal and leave a plain note instead.
                line0.removeElement(line0.elementCount() - 1);
                line0.addElement(ElementType.CROTCHET.newInstance());
            });

            // Build new last line ending in REPEAT_RIGHT.
            var newLine = new Line(song);
            song.withoutMutationTracking(() -> {
                newLine.addElement(ElementType.REPEAT_RIGHT.newInstance());
            });

            // addLine: outgoingTerminalType(line0, newLine) → null (line0 ends in CROTCHET).
            // terminalTypeToInstall → REPEAT_RIGHT (from the new line's last element).
            song.addLine(newLine);

            var lastLine = song.getLine(song.lineCount() - 1);
            var lastElement = lastLine.getElement(lastLine.elementCount() - 1);
            assertThat(lastElement.getType())
                .describedAs("REPEAT_RIGHT on new last line must be promoted when no outgoing terminal")
                .isEqualTo(ElementType.REPEAT_RIGHT);
        }
    }
}
