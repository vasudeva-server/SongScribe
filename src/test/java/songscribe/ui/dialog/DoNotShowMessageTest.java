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

import java.awt.Point;
import java.util.Collections;
import java.util.stream.Stream;

import javax.swing.JDialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Contract tests for {@link DoNotShowMessage}, whose two promises are both about the preference it
 * was handed and neither about what that preference means elsewhere.
 *
 * <p><strong>Suppression</strong> — a show request produces a window exactly when the preference
 * reads false, and produces nothing when it reads true. Both sides of that boolean are exercised;
 * there is no third state.
 *
 * <p><strong>The write</strong> — OK writes {@code true} to that preference when the box is ticked
 * and writes nothing at all otherwise. "Nothing at all" is asserted as the count of every boolean
 * write, not merely of the expected one, so a commit that wrote {@code false} to say "keep showing
 * it" would fail rather than pass unnoticed.
 */
class DoNotShowMessageTest extends MainFrameMockTest {

    /**
     * The preference the dialog under test is handed. Any boolean key serves: {@link Prefs} is
     * mocked, so what these tests pin is that the dialog reads and writes the key it was given.
     */
    private static final PrefsKey SUPPRESSION_KEY = PrefsKey.SHOW_TIPS;

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;
    private DoNotShowMessage dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        dialog = new DoNotShowMessage(mainFrame(), "Test Title", "Test message.", SUPPRESSION_KEY);
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    private record SuppressionCase(String description, boolean alreadySuppressed, int expectedWindows) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("suppressionCases")
    void testShowProducesAWindowExactlyWhenTheMessageIsNotYetSuppressed(SuppressionCase testCase) {
        prefsMock.when(() -> Prefs.getBoolean(SUPPRESSION_KEY))
            .thenReturn(testCase.alreadySuppressed());

        try (var construction = mockConstruction(JDialog.class,
                (d, ctx) -> BaseDialogTestHelper.configureMockDialog(d, new Point(100, 100)))) {

            dialog.setVisible(true);

            assertThat(construction.constructed())
                .as(testCase.description())
                .hasSize(testCase.expectedWindows());
        }
    }

    static Stream<SuppressionCase> suppressionCases() {
        return Stream.of(
            new SuppressionCase("not yet suppressed shows the message", false, 1),
            new SuppressionCase("already suppressed shows nothing", true, 0)
        );
    }

    private record CommitCase(String description, boolean boxTicked, int expectedWrites) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("commitCases")
    void testOkWritesTheSuppressionOnlyWhenTheBoxIsTicked(CommitCase testCase) {
        dialog.dontShowCheck.setSelected(testCase.boxTicked());

        dialog.commitOnOk();

        prefsMock.verify(() -> Prefs.put(SUPPRESSION_KEY, true), times(testCase.expectedWrites()));
        prefsMock.verify(
            () -> Prefs.put(any(PrefsKey.class), anyBoolean()), times(testCase.expectedWrites())
        );
    }

    static Stream<CommitCase> commitCases() {
        return Stream.of(
            new CommitCase("ticked suppresses the message", true, 1),
            new CommitCase("unticked writes nothing", false, 0)
        );
    }
}
