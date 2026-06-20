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

package songscribe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import songscribe.ui.action.Actions;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.action.MockEnvHelper;
import songscribe.ui.component.MainFrame;

import static org.mockito.Mockito.mockStatic;

/**
 * Base class for unit tests that need a mocked {@link MainFrame} singleton.
 *
 * <p>Stubs {@code MainFrame.getInstance()} for the duration of each test and
 * exposes the same mock via {@link #mainFrame()} for constructor injection, so
 * tests have a single, consistent way to obtain a MainFrame regardless of whether
 * the code under test reaches the singleton transitively.
 *
 * <p>Also calls {@link Actions#initialize} (and {@link PlaybackController#initialize})
 * with the mock frame so that the {@code Actions.*} / {@code PlaybackController.*}
 * constants are populated for each test, and clears the injected frame in teardown
 * via {@link Actions#resetForTest()}. The constants are {@code @NonNull}, so they are
 * not nulled individually; the per-test {@code initialize} call in setup guarantees
 * fresh state.
 *
 * <p>The {@code mockStatic(MainFrame.class)} stub is retained even though the four
 * action/dialog routes were decoupled (#375 follow-up): the deliberately out-of-scope
 * non-action paths still call {@code MainFrame.getInstance()} — {@code PreviewElementManager}
 * (preview-element insert/modify mouse handlers) and {@code uiconverter.ConvertAction} —
 * and subclass tests that reach them rely on the stub returning the mock frame.
 */
public abstract class MainFrameMockTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private MockEnvHelper.MockEnv env;

    @BeforeEach
    void setUpMainFrameMock() {
        mainFrameMock = mockStatic(MainFrame.class);
        env = MockEnvHelper.setupMockEnv(mainFrameMock);
        Actions.initialize(env.frame());
        PlaybackController.initialize(env.frame());
    }

    @AfterEach
    void tearDownMainFrameMock() {
        // Unsubscribe this test's action objects so they don't linger as zombie bus
        // subscribers once the next test's initialize() reassigns the constants.
        Actions.unsubscribeForTest();
        PlaybackController.unsubscribeForTest();
        mainFrameMock.close();
        Actions.resetForTest();
    }

    /** The injected mock MainFrame; identical to {@code MainFrame.getInstance()} under the mock. */
    protected final MainFrame mainFrame() {
        return env.frame();
    }

    /** Full mock environment (frame, score, coordinator, controller) for tests that stub deeper. */
    protected final MockEnvHelper.MockEnv mockEnv() {
        return env;
    }
}
