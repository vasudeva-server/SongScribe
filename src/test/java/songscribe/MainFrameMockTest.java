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
 */
public abstract class MainFrameMockTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private MockEnvHelper.MockEnv env;

    @BeforeEach
    void setUpMainFrameMock() {
        mainFrameMock = mockStatic(MainFrame.class);
        env = MockEnvHelper.setupMockEnv(mainFrameMock);
    }

    @AfterEach
    void tearDownMainFrameMock() {
        mainFrameMock.close();
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
