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
import songscribe.ui.Dialogs;

public class ProcessDialog extends JDialog {

    protected final JProgressBar progressBar = new JProgressBar();

    public ProcessDialog(JDialog owner, String label, int maximum)
        throws HeadlessException {
        super(owner, Strings.get(Strings.DIALOG_PROGRESS_TITLE), true);
        init(maximum, label);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    public ProcessDialog(JFrame owner, String label, int maximum)
        throws HeadlessException {
        super(owner, Strings.get(Strings.DIALOG_PROGRESS_TITLE), true);
        init(maximum, label);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    public void packAndPos() {
        pack();
        Dialogs.positionDialog(this, getOwner());
    }

    private void init(int maximum, String label) {
        progressBar.setMaximum(maximum);
        var pane = new JPanel(new BorderLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pane.add(BorderLayout.NORTH, new JLabel(label));
        progressBar.setPreferredSize(new Dimension(400, 20));
        pane.add(BorderLayout.CENTER, progressBar);
        setContentPane(pane);
    }

    public void nextValue() {
        nextValue(1);
    }

    public void nextValue(int value) {
        progressBar.setValue(progressBar.getValue() + value);
    }
}
