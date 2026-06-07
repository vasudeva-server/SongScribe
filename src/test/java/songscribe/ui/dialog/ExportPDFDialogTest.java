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
import songscribe.prefs.Prefs;
import songscribe.ui.component.ScoreView;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExportPDFDialog}: verifies that
 * {@link ExportPDFDialog#getPaperSizeData()} is null before {@code setData}
 * is called and non-null after.
 */
class ExportPDFDialogTest extends MainFrameMockTest {

    private MockedStatic<UIUtils> uiUtilsMock;
    private MockedStatic<Prefs> prefsMock;

    private ExportPDFDialog dialog;

    @BeforeEach
    void setUp() {
        uiUtilsMock = mockStatic(UIUtils.class);
        prefsMock = mockStatic(Prefs.class);
        prefsMock.when(() -> Prefs.getMap(any())).thenReturn(Collections.emptyMap());
        prefsMock.when(() -> Prefs.getBoolean(any())).thenReturn(false);

        BaseDialogTestHelper.configureMockFrame(mainFrame());
        BaseDialog.resetVisibleBlockingDialogCount();
        BaseDialog.resetSavedGeometry();

        // ExportPDFDialog constructor calls requireScoreView() to populate paperSizePageLayoutDataPrivate
        var scoreView = mock(ScoreView.class);
        when(mainFrame().requireScoreView()).thenReturn(scoreView);

        dialog = new ExportPDFDialog();
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
        uiUtilsMock.close();
    }

    @Test
    void testGetPaperSizeDataIsNullBeforeSetData() {
        assertThat(dialog.getPaperSizeData())
            .as("getPaperSizeData() is null before setData() is called")
            .isNull();
    }

    @Test
    void testGetPaperSizeDataIsNonNullAfterSetData() {
        dialog.setData();

        assertThat(dialog.getPaperSizeData())
            .as("getPaperSizeData() is non-null after setData() is called")
            .isNotNull();
    }
}
