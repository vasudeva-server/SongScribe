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

import module java.desktop;

import songscribe.Strings;

public class ProgressBarDialog extends BaseDialog {

    private static final int BORDER_PADDING = 10;
    private static final int PROGRESS_BAR_WIDTH = 400;
    private static final int PROGRESS_BAR_HEIGHT = 20;

    private final JProgressBar progressBar = new JProgressBar();

    public ProgressBarDialog(String label, int maximum) {
        super(Strings.get(Strings.DIALOG_PROGRESS_TITLE), true, DialogCategory.INFORMATIONAL);

        progressBar.setMaximum(maximum);

        var pane = new JPanel(new BorderLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(
            BORDER_PADDING, BORDER_PADDING, BORDER_PADDING, BORDER_PADDING
        ));
        pane.add(BorderLayout.NORTH, new JLabel(label));
        progressBar.setPreferredSize(new Dimension(PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT));
        pane.add(BorderLayout.CENTER, progressBar);
        contentPanel.add(pane);
    }

    @Override
    protected boolean isClosable() {
        return false;
    }

    public void nextValue() {
        nextValue(1);
    }

    public void nextValue(int value) {
        progressBar.setValue(progressBar.getValue() + value);
    }
}
