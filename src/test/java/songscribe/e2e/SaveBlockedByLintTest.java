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

package songscribe.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.WindowFinder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.Strings;
import songscribe.lint.LintRule;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SaveAction;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The path from an edit to the save confirmation: a real click inserts a note with no lyric, the
 * linter reports it, and the File menu's Save item then asks before it writes. "No"
 * writes nothing and leaves the window's current file as it was; "Yes" writes the file. Which
 * songs lint blocks is unit-tested with the checks.
 */
class SaveBlockedByLintTest extends E2ETest {

    private static final String UNTOUCHED_CONTENT = "the file Save must not write";

    /** What a written MusicXML file holds and {@link #UNTOUCHED_CONTENT} does not. */
    private static final String WRITTEN_FILE_MARKER = "score-partwise";

    private static final int MIDDLE_STAFF_LINE = 0;

    private static final int DIALOG_TIMEOUT_MS = 10_000;

    @AfterAll
    void clearTheCurrentFile() {
        GuiActionRunner.execute(() -> MainFrame.getInstance().setCurrentFile(null));
    }

    @Test
    void testNoOnTheConfirmationWritesNothingAndKeepsTheCurrentFile(@TempDir Path directory)
        throws IOException {
        var file = openAFileWithANoteWithNoLyric(directory);

        saveThroughTheMenuAnswering(UIManager.getString("OptionPane.noButtonText"));

        assertThat(Files.readString(file)).as("the file is not written").isEqualTo(UNTOUCHED_CONTENT);
        assertThat(MainFrame.getInstance().getCurrentFile()).as("the current file is unchanged")
            .isEqualTo(file.toFile());
        assertThat(song().isModified()).as("the song is still unsaved").isTrue();
    }

    @Test
    void testYesOnTheConfirmationWritesTheFile(@TempDir Path directory) throws IOException {
        var file = openAFileWithANoteWithNoLyric(directory);

        saveThroughTheMenuAnswering(UIManager.getString("OptionPane.yesButtonText"));

        assertThat(Files.readString(file)).as("the file is written").contains(WRITTEN_FILE_MARKER);
        assertThat(song().isModified()).as("the song is saved").isFalse();
    }

    /**
     * Makes {@code file}, holding {@link #UNTOUCHED_CONTENT}, the window's current file, then
     * inserts a note with no lyric, which the missing-lyric rule flags.
     */
    private Path openAFileWithANoteWithNoLyric(Path directory) throws IOException {
        resetSong();

        var file = directory.resolve("blocked.musicxml");
        Files.writeString(file, UNTOUCHED_CONTENT);
        GuiActionRunner.execute(() -> MainFrame.getInstance().setCurrentFile(file.toFile()));

        buildNotes(Actions.QUARTER_NOTE_ACTION, MIDDLE_STAFF_LINE);

        assertThat(GuiActionRunner.execute(() -> song().getLinter().hasFlag(LintRule.Flag.BLOCKS_EXPORT)))
            .as("the note the click inserted has no lyric")
            .isTrue();

        return file;
    }

    /**
     * Clicks the File menu's Save item and answers the confirmation it shows by pressing the
     * button labeled {@code buttonText}. The click is queued rather than run to completion because
     * the confirmation is modal: the event thread does not return from it until the dialog closes.
     */
    private void saveThroughTheMenuAnswering(@Nullable String buttonText) {
        assertThat(buttonText).isNotNull();

        var saveItem = GuiActionRunner.execute(
            () -> findSaveMenuItem(MainFrame.getInstance().getJMenuBar()));
        assertThat(saveItem).as("the File menu has a Save item").isNotNull();

        SwingUtilities.invokeLater(saveItem::doClick);

        var dialog = WindowFinder.findDialog(new GenericTypeMatcher<JDialog>(JDialog.class) {
                @Override
                protected boolean isMatching(JDialog candidate) {
                    return candidate.isShowing()
                        && Strings.get(Strings.CONFIRM_TITLE_SONG_HAS_ERRORS).equals(candidate.getTitle());
                }
            })
            .withTimeout(DIALOG_TIMEOUT_MS)
            .using(robot);

        dialog.button(new GenericTypeMatcher<JButton>(JButton.class) {
            @Override
            protected boolean isMatching(JButton candidate) {
                return buttonText.equals(candidate.getText());
            }
        }).click();

        robot.waitForIdle();
        pause();
    }

    private static @Nullable JMenuItem findSaveMenuItem(MenuElement parent) {
        for (var element : parent.getSubElements()) {
            if (element instanceof JMenuItem item && item.getAction() instanceof SaveAction) {
                return item;
            }

            var found = findSaveMenuItem(element);

            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
