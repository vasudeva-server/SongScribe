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
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.util.MyFontUtils;
import songscribe.util.UIUtils;

/**
 * Factory for the "font setting" row shared by the Song Settings tabs: a leading
 * label, a stretchy font-description display, and a Choose / Reset button pair.
 * <p>
 * The row also owns the actions that rewrite the target label and restyle the
 * preview, plus the {@link #applyFont} helper they share with the Font tab.
 */
final class FontSettingRow {

    private FontSettingRow() {
    }

    /**
     * Builds a row whose leading label is the standard "Font" label, associated
     * with {@code fontDisplay}.
     */
    static JPanel create(
        MainFrame mainFrame,
        JComponent fontDisplay,
        FontKey fontKey,
        JLabel targetLabel,
        JComponent preview
    ) {
        return create(
            mainFrame,
            new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT)),
            fontDisplay,
            fontKey,
            targetLabel,
            preview
        );
    }

    /**
     * Builds a row with a caller-supplied leading label.
     *
     * @param rowLabel    the leading label (column 0)
     * @param fontDisplay the stretchy display in the middle column
     * @param targetLabel the label the Choose/Reset actions rewrite; may be the
     *                    same instance as {@code fontDisplay}
     * @param preview     the preview the actions restyle
     */
    static JPanel create(
        MainFrame mainFrame,
        JLabel rowLabel,
        JComponent fontDisplay,
        FontKey fontKey,
        JLabel targetLabel,
        JComponent preview
    ) {
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);
        var row = new JPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        var constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;

        rowLabel.setLabelFor(fontDisplay);
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        // Lead with the horizontal gap so the label indents to match the
        // FlowLayout-based rows in the other sections (which carry the gap
        // either as the layout's leading hgap or a leading empty border).
        constraints.insets = new Insets(0, gap, 0, gap);
        row.add(rowLabel, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        row.add(fontDisplay, constraints);

        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(new JButton(new ChooseFontAction(mainFrame, targetLabel, preview)));
        BaseDialog.addLargeSeparator(buttons);
        buttons.add(new JButton(new ResetFontAction(mainFrame, fontKey, targetLabel, preview)));

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, gap, 0, 0);
        row.add(buttons, constraints);

        UIUtils.setFlexibleWidth(row);
        return row;
    }

    static void applyFont(Font font, JLabel label, JComponent preview) {
        label.setText(MyFontUtils.getFullFontDescription(font));
        preview.setFont(font);
    }

    private static final class ChooseFontAction extends UIAction {

        private final JLabel fontDescription;
        private final JComponent preview;

        private ChooseFontAction(
            MainFrame mainFrame,
            JLabel fontDescription,
            JComponent preview
        ) {
            super(
                mainFrame,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_CHOOSE),
                "choose-font"
            );
            this.fontDescription = fontDescription;
            this.preview = preview;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            var selectedFont = FontDialog.showDialog(preview);
            fontDescription.setText(
                MyFontUtils.getFullFontDescription(selectedFont)
            );
            preview.setFont(selectedFont);
            preview.revalidate();
            preview.repaint();
        }
    }

    private static final class ResetFontAction extends UIAction {

        private final FontKey fontKey;
        private final JLabel fontDescription;
        private final JComponent preview;

        private ResetFontAction(
            MainFrame mainFrame,
            FontKey fontKey,
            JLabel fontDescription,
            JComponent preview
        ) {
            super(
                mainFrame,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_RESET),
                "reset-font"
            );
            this.fontKey = fontKey;
            this.fontDescription = fontDescription;
            this.preview = preview;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyFont(
                DocumentFonts.defaultsFromSystemDefaults().getFont(fontKey),
                fontDescription,
                preview
            );
            preview.revalidate();
            preview.repaint();
        }
    }
}
