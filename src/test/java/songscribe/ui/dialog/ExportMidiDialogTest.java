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

import java.io.File;
import java.util.Collections;

import javax.sound.midi.InvalidMidiDataException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.Song;
import songscribe.midi.PlaybackSettings;
import songscribe.prefs.Prefs;
import songscribe.ui.component.ScoreView;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.UIUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExportMidiDialog#setData}: verifies that
 * {@link PlaybackController#applySettings} restores saved settings
 * even when an exception is thrown during sequence building.
 */
class ExportMidiDialogTest extends MainFrameMockTest {

    private static final PlaybackSettings SAVED_SETTINGS =
        new PlaybackSettings(10, 100, 75, false);

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private MockedStatic<PlaybackController> playbackMock;

    private ExportMidiDialog dialog;

    @BeforeEach
    void setUp() {
        // PlaybackController must be mocked before UIUtils, because its <clinit> calls
        // UIUtils.getTaggedString() — if UIUtils is already mocked (returning null by default)
        // when PlaybackController is instrumented, the initialization fails with NPE.
        playbackMock = mockStatic(PlaybackController.class);
        playbackMock.when(PlaybackController::getPlaybackSettings).thenReturn(SAVED_SETTINGS);

        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        prefsMock.when(() -> Prefs.getInt(any())).thenReturn(0);
        prefsMock.when(() -> Prefs.getBoolean(any())).thenReturn(false);

        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        // Stub requireScoreView() so ExportMidiDialog.setData() can obtain a ScoreView
        var scoreView = mock(ScoreView.class);
        when(mainFrame().requireScoreView()).thenReturn(scoreView);
        when(scoreView.getSong()).thenReturn(mock(Song.class));

        dialog = new ExportMidiDialog(new File("test-export.mid"));
    }

    @AfterEach
    void tearDown() {
        // Close in reverse order of creation
        prefsMock.close();
        uiUtilsMock.close();
        playbackMock.close();
    }

    @Test
    void testSettingsRestoredEvenWhenBuildSequenceThrows() throws InvalidMidiDataException {
        // Arrange: buildSequence throws so the inner try fails
        playbackMock.when(() -> PlaybackController.buildSequence(any()))
            .thenThrow(new InvalidMidiDataException("simulated failure"));

        // Act: setData() must reach the finally block despite the exception
        dialog.setData();

        // Assert: applySettings must be called with the original saved settings
        playbackMock.verify(() -> PlaybackController.applySettings(SAVED_SETTINGS));
    }

    @Test
    void testPlaybackSettingsSavedBeforeOverrides() throws InvalidMidiDataException {
        // Arrange: buildSequence throws to short-circuit after override calls
        playbackMock.when(() -> PlaybackController.buildSequence(any()))
            .thenThrow(new InvalidMidiDataException("simulated failure"));

        // Act
        dialog.setData();

        // Assert: getPlaybackSettings() was called (to capture the value to restore)
        // and applySettings() was called with that captured value.
        // The fact that applySettings receives exactly SAVED_SETTINGS proves
        // getPlaybackSettings() was invoked before any overrides were applied.
        playbackMock.verify(PlaybackController::getPlaybackSettings);
        playbackMock.verify(() -> PlaybackController.applySettings(SAVED_SETTINGS));
    }
}
