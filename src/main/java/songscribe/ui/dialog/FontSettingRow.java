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

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.lifecycle.Disposable;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Factory for the reusable "font setting" row: a leading label, a stretchy
 * font-description display, and a Choose / Reset button pair. The row is a pure
 * font chooser — it knows nothing about any particular tab or document font.
 * <p>
 * The owning tab supplies a {@code currentFont} supplier (read to seed the
 * chooser) and an {@code onFontChosen} callback (invoked with the new font).
 * Each tab decides what choosing a font means for its own context.
 * <p>
 * The row lays the description label out but never writes it. The chosen font is the
 * tab's own value, and the label is one of the things the tab derives from it — writing
 * the label here as well would leave the same text arriving by two routes, one of which
 * fires only when the font is chosen through this row.
 */
final class FontSettingRow {

    private static final String CHOOSE_FONT_COMMAND = "choose-font";
    private static final String RESET_FONT_COMMAND = "reset-font";

    // The system defaults never change at runtime, so build them once and read
    // each Reset's font from the shared instance rather than rebuilding all
    // fonts on every Reset click.
    @Nullable
    private static DocumentFonts systemDefaultFonts = null;

    private FontSettingRow() {
    }

    /**
     * An assembled row: the panel to add to a section, and the two actions behind its
     * buttons, which subscribe themselves to the message bus and so have to be released
     * when the dialog that owns the row closes.
     * <p>
     * The actions travel back with the panel rather than being reachable from it, because a
     * caller that has to dig components out of a panel to release them is a caller that will
     * forget: the row hands over exactly what it made and the owning tab holds the whole
     * record until it disposes it.
     */
    record Row(JPanel panel, Disposable chooseAction, Disposable resetAction) implements Disposable {

        /** Releases both of the row's actions. Idempotent, as {@link Disposable} requires. */
        @Override
        public void dispose() {
            chooseAction.dispose();
            resetAction.dispose();
        }
    }

    /**
     * Builds a row whose leading label is the standard "Font" label, associated
     * with {@code fontDescription}.
     *
     * @return the assembled row, whose actions the caller owns and must dispose
     */
    static Row create(
        MainFrame mainFrame,
        JLabel fontDescription,
        FontKey fontKey,
        Supplier<? extends Font> currentFont,
        Consumer<? super Font> onFontChosen
    ) {
        return create(
            mainFrame,
            new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT)),
            fontDescription,
            fontKey,
            currentFont,
            onFontChosen
        );
    }

    /**
     * Builds a row with a caller-supplied leading label.
     *
     * @param rowLabel        the leading label (column 0)
     * @param fontDescription the stretchy description display in the middle column
     * @param currentFont     supplies the font that seeds the chooser
     * @param onFontChosen    notified with the font chosen (or reset to default)
     * @return the assembled row, whose actions the caller owns and must dispose
     */
    static Row create(
        MainFrame mainFrame,
        JLabel rowLabel,
        JLabel fontDescription,
        FontKey fontKey,
        Supplier<? extends Font> currentFont,
        Consumer<? super Font> onFontChosen
    ) {
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);
        var row = new JPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        var constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;

        rowLabel.setLabelFor(fontDescription);
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
        row.add(fontDescription, constraints);

        var chooseAction = new ChooseFontAction(mainFrame, currentFont, onFontChosen);
        var resetAction = new ResetFontAction(mainFrame, fontKey, onFontChosen);

        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(new JButton(chooseAction));
        BaseDialog.addLargeSeparator(buttons);
        buttons.add(new JButton(resetAction));

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, gap, 0, 0);
        row.add(buttons, constraints);

        UIUtils.setFlexibleWidth(row);
        return new Row(row, chooseAction, resetAction);
    }

    /**
     * Creates the boxed label used to display a font's description, matching
     * the style the Fonts tab uses for its font-name labels.
     */
    static JLabel createFontDescriptionLabel() {
        var label = new JLabel();
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtils.getComponentBorderColor()),
            UIUtils.spacingBorder(FlatLafKey.DIALOG_SONG_SETTINGS_FONT_LABEL_PADDING)
        ));
        return label;
    }

    static Font defaultFont(FontKey fontKey) {
        if (systemDefaultFonts == null) {
            systemDefaultFonts = DocumentFonts.defaultFonts();
        }

        return systemDefaultFonts.getFont(fontKey);
    }

    private static final class ChooseFontAction extends UIAction {

        private final Supplier<? extends Font> currentFont;
        private final Consumer<? super Font> onFontChosen;

        private ChooseFontAction(
            MainFrame mainFrame,
            Supplier<? extends Font> currentFont,
            Consumer<? super Font> onFontChosen
        ) {
            super(
                mainFrame,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_CHOOSE),
                CHOOSE_FONT_COMMAND
            );
            this.currentFont = currentFont;
            this.onFontChosen = onFontChosen;
        }

        @Override
        protected void performAction(ActionEvent e) {
            onFontChosen.accept(FontDialog.showDialog(getMainFrame(), currentFont.get()));
        }
    }

    private static final class ResetFontAction extends UIAction {

        private final FontKey fontKey;
        private final Consumer<? super Font> onFontChosen;

        private ResetFontAction(
            MainFrame mainFrame,
            FontKey fontKey,
            Consumer<? super Font> onFontChosen
        ) {
            super(
                mainFrame,
                Strings.get(Strings.DIALOG_SONG_SETTINGS_RESET),
                RESET_FONT_COMMAND
            );
            this.fontKey = fontKey;
            this.onFontChosen = onFontChosen;
        }

        @Override
        protected void performAction(ActionEvent e) {
            onFontChosen.accept(defaultFont(fontKey));
        }
    }
}
