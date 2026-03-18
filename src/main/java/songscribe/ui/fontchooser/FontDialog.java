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
package songscribe.ui.fontchooser;

import module java.desktop;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.util.UIUtils;

/**
 * A dialog containing a {@code FontChooser} as well as OK and Cancel buttons.
 *
 * @author Christos Bohoris
 * @author Aparajita Fishman
 */
public class FontDialog extends JDialog {

    public static Optional<Font> showDialog(
        Component component,
        @Nullable Point location
    ) {
        @SuppressWarnings("NullAway") // JDialog accepts null Frame owner
        var dialog = new FontDialog((Frame) null, Strings.get(Strings.DIALOG_FONT_CHOOSER_TITLE), true);

        // Make the dialog a little taller than the default size
        var size = dialog.getPreferredSize();
        dialog.setSize(size.width, size.height + 70);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSelectedFont(component.getFont());

        if (location != null) {
            dialog.setLocation(location);
        } else {
            dialog.setLocationRelativeTo(null);
        }

        dialog.setVisible(true);

        if (!dialog.wasCancelled()) {
            return Optional.of(dialog.getSelectedFont());
        }

        return Optional.empty();
    }

    private final FontChooser chooser = new FontChooser();

    private final JButton cancelButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_CANCEL));

    private final JButton okButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_OK));

    public FontDialog() {
        initDialog();
    }

    public FontDialog(Frame owner) {
        super(owner);
        initDialog();
    }

    public FontDialog(Frame owner, boolean modal) {
        super(owner, modal);
        initDialog();
    }

    public FontDialog(Frame owner, String title) {
        super(owner, title);
        initDialog();
    }

    public FontDialog(Frame owner, String title, boolean modal) {
        super(owner, title, modal);
        initDialog();
    }

    public FontDialog(
        Frame owner,
        String title,
        boolean modal,
        GraphicsConfiguration gc
    ) {
        super(owner, title, modal, gc);
        initDialog();
    }

    public FontDialog(Dialog owner) {
        super(owner);
        initDialog();
    }

    public FontDialog(Dialog owner, boolean modal) {
        super(owner, modal);
        initDialog();
    }

    public FontDialog(Dialog owner, String title) {
        super(owner, title);
        initDialog();
    }

    public FontDialog(Dialog owner, String title, boolean modal) {
        super(owner, title, modal);
        initDialog();
    }

    public FontDialog(
        Dialog owner,
        String title,
        boolean modal,
        GraphicsConfiguration gc
    ) {
        super(owner, title, modal, gc);
        initDialog();
    }

    public FontDialog(Window owner) {
        super(owner);
        initDialog();
    }

    public FontDialog(Window owner, ModalityType modalityType) {
        super(owner, modalityType);
        initDialog();
    }

    public FontDialog(Window owner, String title) {
        super(owner, title);
        initDialog();
    }

    public FontDialog(Window owner, String title, ModalityType modalityType) {
        super(owner, title, modalityType);
        initDialog();
    }

    public FontDialog(
        Window owner,
        String title,
        ModalityType modalityType,
        GraphicsConfiguration gc
    ) {
        super(owner, title, modalityType, gc);
        initDialog();
    }

    private boolean cancelled = false;

    private void initDialog() {
        initComponents();
        UIUtils.addStandardDialogKeyBindings(this);
        setMinimumSize(new Dimension(500, 400));
        getRootPane().setDefaultButton(okButton);
        addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    cancelled = true;
                }
            }
        );
        pack();
    }

    private void initComponents() {
        var chooserPanel = new JPanel();
        chooserPanel.setBorder(BorderFactory.createEmptyBorder(13, 13, 0, 13));
        chooserPanel.setLayout(new BorderLayout(0, 13));
        chooserPanel.add(chooser);
        add(chooserPanel);

        var controlPanel = new JPanel();
        controlPanel.setBorder(BorderFactory.createEmptyBorder(13, 13, 13, 13));
        controlPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        add(controlPanel, BorderLayout.PAGE_END);

        cancelButton.addActionListener(event -> {
            cancelled = true;
            dispose();
        });
        controlPanel.add(cancelButton);

        okButton.addActionListener(event -> dispose());
        controlPanel.add(okButton);
    }

    public Font getSelectedFont() {
        return chooser.getSelectedFont();
    }

    public void setSelectedFont(Font font) {
        chooser.setSelectedFont(font);
    }

    public boolean wasCancelled() {
        return cancelled;
    }
}
