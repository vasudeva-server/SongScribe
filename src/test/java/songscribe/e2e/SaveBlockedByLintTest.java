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

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.lint.LintRule;
import songscribe.lint.Linter;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The path from an edit to a refused save: a real click inserts a left repeat that nothing closes,
 * the linter reports it, and Save then writes no file and leaves the window's current file as it
 * was. Which songs lint blocks is unit-tested with the checks.
 */
class SaveBlockedByLintTest extends E2ETest {

    private static final String UNTOUCHED_CONTENT = "the file Save must not write";

    private static final int LEFT_REPEAT_INDEX = 0;

    private static final int MIDDLE_STAFF_LINE = 0;

    private static final int FIRST_LINE = 0;

    @BeforeAll
    void suppressTheRefusalAlert() {
        OptionDialogs.setSuppressDialogs(true);
    }

    @AfterAll
    void restoreDialogs() {
        OptionDialogs.setSuppressDialogs(false);
        GuiActionRunner.execute(() -> MainFrame.getInstance().setCurrentFile(null));
    }

    @Test
    void testSaveOnASongWithALintErrorWritesNoFileAndKeepsTheCurrentFile(@TempDir Path directory) throws IOException {
        resetSong();

        var file = directory.resolve("blocked.musicxml");
        Files.writeString(file, UNTOUCHED_CONTENT);
        GuiActionRunner.execute(() -> MainFrame.getInstance().setCurrentFile(file.toFile()));

        triggerAction(Actions.REPEAT_ACTIONS[LEFT_REPEAT_INDEX]);
        clickAt(insertionPoint(FIRST_LINE, MIDDLE_STAFF_LINE));
        performLayout(FIRST_LINE);

        assertThat(Linter.hasFlag(LintRule.Flag.BLOCKS_SAVE))
            .as("the left repeat the click inserted has no right repeat to close it")
            .isTrue();

        var saved = GuiActionRunner.execute(() -> MainFrame.getInstance().save());

        assertThat(saved).as("Save refuses").isFalse();
        assertThat(Files.readString(file)).as("the file is not written").isEqualTo(UNTOUCHED_CONTENT);
        assertThat(MainFrame.getInstance().getCurrentFile()).as("the current file is unchanged")
            .isEqualTo(file.toFile());
    }
}
