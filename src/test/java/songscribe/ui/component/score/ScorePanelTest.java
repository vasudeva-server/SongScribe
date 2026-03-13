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

package songscribe.ui.component.score;

import java.awt.*;

import javax.swing.*;

import songscribe.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScorePanelTest extends UnitTest {

    private static final String BACKGROUND_KEY = "SongScribe.scorePanel.background";

    @AfterEach
    void tearDown() {
        UIManager.put(BACKGROUND_KEY, null);
    }

    @Test
    void testUpdateUIFallsBackToLightGrayWhenKeyMissing() throws Exception {
        var bg = new Color[1];
        SwingUtilities.invokeAndWait(() -> bg[0] = new ScorePanel(new JPanel()).getBackground());
        assertThat(bg[0]).isEqualTo(Color.LIGHT_GRAY);
    }

    @Test
    void testUpdateUIReadsBackgroundFromUIManager() throws Exception {
        var customColor = new Color(0x404040);
        UIManager.put(BACKGROUND_KEY, customColor);

        var bg = new Color[1];
        SwingUtilities.invokeAndWait(() -> bg[0] = new ScorePanel(new JPanel()).getBackground());
        assertThat(bg[0]).isEqualTo(customColor);
    }

    @Test
    void testUpdateUIRespondsToColorChange() throws Exception {
        var panelRef = new ScorePanel[1];
        SwingUtilities.invokeAndWait(() -> panelRef[0] = new ScorePanel(new JPanel()));
        assertThat(panelRef[0].getBackground()).isEqualTo(Color.LIGHT_GRAY);

        var darkColor = new Color(0x404040);
        UIManager.put(BACKGROUND_KEY, darkColor);
        SwingUtilities.invokeAndWait(() -> panelRef[0].updateUI());

        assertThat(panelRef[0].getBackground()).isEqualTo(darkColor);
    }
}
