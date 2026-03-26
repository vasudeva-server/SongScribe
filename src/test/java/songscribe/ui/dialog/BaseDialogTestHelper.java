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

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JRootPane;

import songscribe.ui.component.MainFrame;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared mock setup for {@link BaseDialog} tests.
 */
final class BaseDialogTestHelper {

    static final int SCREEN_WIDTH = 1920;
    static final int SCREEN_HEIGHT = 1080;
    static final Dimension DEFAULT_DIALOG_SIZE = new Dimension(300, 200);

    private BaseDialogTestHelper() {}

    /**
     * Configures a mock {@link MainFrame} with a standard screen bounds.
     */
    static void configureMockFrame(MainFrame mockFrame) {
        var mockGc = mock(GraphicsConfiguration.class);
        when(mockFrame.getGraphicsConfiguration()).thenReturn(mockGc);
        when(mockGc.getBounds()).thenReturn(new Rectangle(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
        when(mockFrame.getBounds()).thenReturn(new Rectangle(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
    }

    /**
     * Configures a mock {@link JDialog} with standard root pane, preferred/actual size,
     * and the given location.
     */
    static void configureMockDialog(JDialog dialog, Point location) {
        var mockRootPane = mock(JRootPane.class);
        when(dialog.getRootPane()).thenReturn(mockRootPane);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(mock(InputMap.class));
        when(mockRootPane.getActionMap()).thenReturn(mock(ActionMap.class));
        when(dialog.getPreferredSize()).thenReturn(DEFAULT_DIALOG_SIZE);
        when(dialog.getSize()).thenReturn(DEFAULT_DIALOG_SIZE);
        when(dialog.getLocation()).thenReturn(location);
    }
}
