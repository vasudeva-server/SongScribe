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
package songscribe.ui.dialog;

import javax.swing.JTextArea;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.KeyType;
import songscribe.ui.component.NonEmptyGuard;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SongSettingsDialog} helpers.
 *
 * <p>The static helper methods ({@code parseIntFieldOrNull},
 * {@code isTextTabUnchanged}, {@code extractLyricsTitle},
 * {@code validateLineWidthText}, {@code canonicalKeySelectionFrom},
 * {@code applyMusicTabChanges}) were inlined into private inner-class methods
 * during the attribution/song-settings redesign and are no longer directly
 * testable. Their behaviour is exercised indirectly via integration and e2e
 * tests.
 *
 * <p>Row 12 (TitleTab guard / isValidData): covered here via a direct call to
 * {@link NonEmptyGuard#verify} since the title-tab installs a
 * {@code NonEmptyGuard} and {@code isValidData} delegates to it.
 *
 * <p>Row 19: {@link SongSettingsDialog.KeyCellRenderer#SELECTIONS} — 15
 * entries in canonical order — is fully covered below.
 */
class SongSettingsDialogTest extends UnitTest {

    // ── Row 12: TitleTab NonEmptyGuard — title field validation ──

    @Nested
    class IsValidData {

        /**
         * Mirrors the guard installed by {@code TitleTab}: a 7-arg
         * {@link NonEmptyGuard} with a default-value option. Dialogs are
         * suppressed by {@link UnitTest#suppressDialogs}.
         */
        private NonEmptyGuard guardFor(JTextArea field) {
            return new NonEmptyGuard(
                field,
                field,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE,
                Strings.DOCUMENT_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_USE_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            );
        }

        @Test
        void testNonEmptyTitleReturnsTrueWithoutDialog() {
            var titleField = new JTextArea("My Song");
            var guard = guardFor(titleField);

            assertThat(guard.verify(titleField))
                .as("non-blank title field: verify → true")
                .isTrue();
        }

        @Test
        void testEmptyTitleReturnsFalse() {
            var titleField = new JTextArea("");
            var guard = guardFor(titleField);

            assertThat(guard.verify(titleField))
                .as("blank title field: verify → false")
                .isFalse();
        }

        @Test
        void testBlankWhitespaceTitleReturnsFalse() {
            var titleField = new JTextArea("   ");
            var guard = guardFor(titleField);

            assertThat(guard.verify(titleField))
                .as("whitespace-only title is blank: verify → false")
                .isFalse();
        }
    }

    // ── Row 19: KeyCellRenderer.SELECTIONS — 15 entries, canonical order ──

    @Nested
    class KeyCellRendererSelections {

        private static final int EXPECTED_SELECTION_COUNT = 15;
        private static final int MAX_ACCIDENTALS = 7;

        @Test
        void testSelectionsHasExactly15Entries() {
            assertThat(SongSettingsDialog.KeyCellRenderer.SELECTIONS)
                .as("1 no-accidentals + 7 flats + 7 sharps = 15 entries")
                .hasSize(EXPECTED_SELECTION_COUNT);
        }

        @Test
        void testFirstEntryIsNoAccidentals() {
            var first = SongSettingsDialog.KeyCellRenderer.SELECTIONS.getFirst();

            assertThat(first.keyType())
                .as("first entry has FLATS type (canonical no-accidentals)")
                .isEqualTo(KeyType.FLATS);
            assertThat(first.count())
                .as("first entry has 0 accidentals")
                .isEqualTo(0);
        }

        @Test
        void testFlatEntriesAreInOrderAfterNoAccidentals() {
            // Entries 1–7 must be FLATS with counts 1..7 in ascending order.
            var selections = SongSettingsDialog.KeyCellRenderer.SELECTIONS;

            for (var i = 1; i <= MAX_ACCIDENTALS; i++) {
                var sel = selections.get(i);
                assertThat(sel.keyType())
                    .as("entry %d should be FLATS", i)
                    .isEqualTo(KeyType.FLATS);
                assertThat(sel.count())
                    .as("entry %d should have %d flat(s)", i, i)
                    .isEqualTo(i);
            }
        }

        @Test
        void testSharpEntriesAreInOrderAfterFlats() {
            // Entries 8–14 must be SHARPS with counts 1..7 in ascending order.
            var selections = SongSettingsDialog.KeyCellRenderer.SELECTIONS;
            var sharpStart = MAX_ACCIDENTALS + 1;

            for (var i = 0; i < MAX_ACCIDENTALS; i++) {
                var sel = selections.get(sharpStart + i);
                assertThat(sel.keyType())
                    .as("entry %d should be SHARPS", sharpStart + i)
                    .isEqualTo(KeyType.SHARPS);
                assertThat(sel.count())
                    .as("entry %d should have %d sharp(s)", sharpStart + i, i + 1)
                    .isEqualTo(i + 1);
            }
        }
    }
}
