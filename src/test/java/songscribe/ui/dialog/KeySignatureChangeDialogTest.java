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

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.prefs.Prefs;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KeySignatureChangeDialog}: getData() control population and setData()
 * committing the chosen key to the selected line (including the null-guard on keysCombo).
 */
class KeySignatureChangeDialogTest extends MainFrameMockTest {

    private static final int SELECTED_LINE_INDEX = 2;
    private static final int INDEX_OF_LINE = 2; // 0-based; label shows INDEX_OF_LINE + 1
    private static final int ACCIDENTAL_COUNT = 4;

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private KeySignatureChangeDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();
        dialog = new KeySignatureChangeDialog(mainFrame());
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    // ── Row 22: getData() — label, combo, spinner pre-populated from song/line ──

    @Test
    void testGetDataPrePopulatesLabelFromLineIndexPlusOne() {
        setupScoreAndLine(KeyType.FLATS, ACCIDENTAL_COUNT);

        dialog.getData();

        // indexOfLine returns INDEX_OF_LINE (0-based), label must show INDEX_OF_LINE + 1
        assertThat(dialog.indexOfSelectedElementLabel.getText())
            .as("label shows 1-based line ordinal")
            .isEqualTo(Integer.toString(INDEX_OF_LINE + 1));
    }

    @Test
    void testGetDataSetsComboToLineKeyType() {
        setupScoreAndLine(KeyType.SHARPS, ACCIDENTAL_COUNT);

        dialog.getData();

        assertThat(dialog.keysCombo.getSelectedItem())
            .as("keysCombo pre-set to line's key type")
            .isEqualTo(KeyType.SHARPS);
    }

    @Test
    void testGetDataSetsSpinnerToLineAccidentalCount() {
        setupScoreAndLine(KeyType.FLATS, ACCIDENTAL_COUNT);

        dialog.getData();

        assertThat(dialog.keysSpinner.getValue())
            .as("keysSpinner pre-set to line's accidental count")
            .isEqualTo(ACCIDENTAL_COUNT);
    }

    // ── Row 23: setData() — changes nothing when keysCombo selection is null ──

    @Test
    void testSetDataChangesNothingWhenKeysComboIsNull() {
        var song = setupScoreAndLine(KeyType.FLATS, ACCIDENTAL_COUNT);
        // Force combo to have no selection
        dialog.keysCombo.setSelectedItem(null);

        dialog.setData();

        verify(song, never()).withModification(any(String.class), any(Runnable.class));
    }

    // ── Row 24: setData() — establishes the chosen key on the selected line ──

    @Test
    void testSetDataEstablishesTheSelectedKeyOnTheSelectedLine() {
        var song = setupScoreAndLine(KeyType.FLATS, ACCIDENTAL_COUNT);
        var newAccidentalCount = 2;

        dialog.keysCombo.setSelectedItem(KeyType.SHARPS);
        dialog.keysSpinner.setValue(newAccidentalCount);
        dialog.setData();

        verify(song).withModification(
            eq(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY)), any(Runnable.class));
        assertThat(song.getLine(SELECTED_LINE_INDEX).getKey())
            .as("the selected line establishes the key the dialog submitted")
            .isEqualTo(new Key(KeyType.SHARPS, newAccidentalCount));
    }

    // ── Helpers ──

    /**
     * Configures the mock score/song so that {@code getSelectedLine()} returns
     * {@link #SELECTED_LINE_INDEX} and {@code getSong().getLine()} returns a
     * detached line pre-set to {@code keyType} and {@code accidentalCount}.
     *
     * @return the mock Song (for verification)
     */
    private Song setupScoreAndLine(KeyType keyType, int accidentalCount) {
        var line = detachedLine();
        line.setKey(new Key(keyType, accidentalCount));

        var song = mock(Song.class);
        when(song.getLine(SELECTED_LINE_INDEX)).thenReturn(line);
        when(song.indexOfLine(line)).thenReturn(INDEX_OF_LINE);
        when(song.isMutationTrackingSuspended()).thenReturn(true);
        doAnswer(answerVoid(Runnable::run))
            .when(song).withModification(any(Runnable.class));
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(song).withModification(any(String.class), any(Runnable.class));

        var score = mockEnv().score();
        when(score.getSelectedLine()).thenReturn(SELECTED_LINE_INDEX);
        when(score.getSong()).thenReturn(song);

        return song;
    }
}
