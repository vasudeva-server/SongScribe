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

import static songscribe.util.UIUtils.MENU_SHORTCUT_MASK;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import songscribe.music.LyricsProcessor;
import songscribe.ui.Constants;
import songscribe.ui.component.MyJTextArea;
import songscribe.util.StringUtils;
import songscribe.util.UIUtils;

@SuppressWarnings("NonStaticInitializer")
public class LyricsDialog extends StandardDialog {

    private static final char[][] specialChars = new char[][] {
        new char[] { 'ā', 'ñ', 'â', 'ũ', 'ã' },
        new char[] { 'Ā', 'Ñ', 'Â', 'Ũ', 'Ã' },
    };

    protected MyJTextArea lyricsArea;
    protected MyJTextArea underSongArea;
    protected MyJTextArea translatedArea;
    protected JPanel centerPanel;
    protected JPanel charsPanel;
    protected JButton moreButton;
    protected JButton takeButton;
    protected JPanel morePanel;
    protected JButton nonBreakingHyphenButton;
    protected JPanel syllabifiedLyricsPanel;
    protected JPanel underLyricsPanel;
    protected JPanel translatedLyricsPanel;

    public LyricsDialog() {
        super("Lyrics");
        charsPanel.setLayout(
            new GridLayout(1, specialChars[0].length * 2, 4, 0)
        );
        Font font = null;

        for (var i = 0; i < 2; i++) {
            for (var j = 0; j < specialChars[i].length; j++) {
                var button = new JButton(
                    Character.toString(specialChars[i][j])
                );

                if (font == null) {
                    font = button.getFont().deriveFont(16f);
                }

                button.setFont(font);

                var k = i;
                var l = j;

                var action = new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        lyricsArea.insert(
                            Character.toString(specialChars[k][l]),
                            lyricsArea.getCaretPosition()
                        );
                        lyricsArea.requestFocusInWindow();
                    }
                };

                button.addActionListener(action);

                var specCharStroke = new KeyStroke[][] {
                    new KeyStroke[] {
                        KeyStroke.getKeyStroke(
                            KeyEvent.VK_A,
                            InputEvent.ALT_DOWN_MASK
                        ),
                        KeyStroke.getKeyStroke(
                            KeyEvent.VK_N,
                            MENU_SHORTCUT_MASK
                        ),
                        null,
                        null,
                        null,
                    },
                    new KeyStroke[] {
                        KeyStroke.getKeyStroke(
                            KeyEvent.VK_A,
                            InputEvent.ALT_DOWN_MASK |
                            InputEvent.SHIFT_DOWN_MASK
                        ),
                        KeyStroke.getKeyStroke(
                            KeyEvent.VK_N,
                            MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK
                        ),
                        null,
                        null,
                        null,
                    },
                };
                if (specCharStroke[i][j] != null) {
                    button.setToolTipText(
                        UIUtils.makeTooltipWithKeystroke(
                            "",
                            specCharStroke[i][j]
                        )
                    );
                    lyricsArea
                        .getInputMap()
                        .put(specCharStroke[i][j], specCharStroke[i][j]);
                    lyricsArea.getActionMap().put(specCharStroke[i][j], action);
                }

                charsPanel.add(button);
            }
        }

        nonBreakingHyphenButton.addActionListener(_ -> {
            lyricsArea.insert(
                Constants.NON_BREAKING_HYPHEN,
                lyricsArea.getCaretPosition()
            );
            lyricsArea.requestFocusInWindow();
        });
        takeButton.addActionListener(
            new TakeUnderSongLyricsFromSyllabifiedLyricsAction()
        );
        morePanel.setVisible(false);
        moreButton.addActionListener(_ -> {
            morePanel.setVisible(!morePanel.isVisible());
            moreButton.setText(morePanel.isVisible() ? "<< Less" : "More >>");
            pack();
        });

        contentPanel.add(BorderLayout.CENTER, centerPanel);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    public void getData() {
        var composition = mainFrame.getScore().getComposition();
        lyricsArea.setText(composition.getLyrics());
        underSongArea.setText(composition.getUnderLyrics());
        translatedArea.setText(composition.getTranslatedLyrics());
    }

    @Override
    protected void setData() {
        var composition = mainFrame.getScore().getComposition();
        composition.setLyrics(lyricsArea.getText());
        composition.setUnderLyrics(underSongArea.getText());
        composition.setTranslatedLyrics(translatedArea.getText());
        LyricsProcessor.spellLyrics(composition);
        mainFrame.setDocumentModified(true);
        mainFrame.getLyricsModePanel().getData();
    }

    {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        centerPanel = new JPanel();
        centerPanel.setLayout(
            new GridLayoutManager(4, 1, new Insets(10, 10, 10, 10), -1, -1)
        );
        syllabifiedLyricsPanel = new JPanel();
        syllabifiedLyricsPanel.setLayout(
            new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1)
        );
        centerPanel.add(
            syllabifiedLyricsPanel,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        syllabifiedLyricsPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Syllabified lyrics",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            )
        );
        final JScrollPane scrollPane1 = new JScrollPane();
        syllabifiedLyricsPanel.add(
            scrollPane1,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        lyricsArea = new MyJTextArea();
        lyricsArea.setColumns(30);
        lyricsArea.setRows(6);
        lyricsArea.setToolTipText("This text is shown under the notes");
        scrollPane1.setViewportView(lyricsArea);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(
            new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1)
        );
        syllabifiedLyricsPanel.add(
            panel1,
            new GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        charsPanel = new JPanel();
        charsPanel.setLayout(new BorderLayout(0, 0));
        panel1.add(
            charsPanel,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer1 = new Spacer();
        panel1.add(
            spacer1,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer2 = new Spacer();
        panel1.add(
            spacer2,
            new GridConstraints(
                0,
                2,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JPanel panel2 = new JPanel();
        panel2.setLayout(
            new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1)
        );
        syllabifiedLyricsPanel.add(
            panel2,
            new GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        nonBreakingHyphenButton = new JButton();
        nonBreakingHyphenButton.setText("Non-breaking hyphen");
        panel2.add(
            nonBreakingHyphenButton,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer3 = new Spacer();
        panel2.add(
            spacer3,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer4 = new Spacer();
        panel2.add(
            spacer4,
            new GridConstraints(
                0,
                2,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false
            )
        );
        moreButton = new JButton();
        moreButton.setText("More >>");
        centerPanel.add(
            moreButton,
            new GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_EAST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        morePanel = new JPanel();
        morePanel.setLayout(
            new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1)
        );
        centerPanel.add(
            morePanel,
            new GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JPanel panel3 = new JPanel();
        panel3.setLayout(
            new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1)
        );
        morePanel.add(
            panel3,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        underLyricsPanel = new JPanel();
        underLyricsPanel.setLayout(
            new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1)
        );
        panel3.add(
            underLyricsPanel,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        underLyricsPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Lyrics under the song",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            )
        );
        final JScrollPane scrollPane2 = new JScrollPane();
        underLyricsPanel.add(
            scrollPane2,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        underSongArea = new MyJTextArea();
        underSongArea.setColumns(30);
        underSongArea.setRows(6);
        underSongArea.setToolTipText("This text is shown under the song");
        scrollPane2.setViewportView(underSongArea);
        takeButton = new JButton();
        takeButton.setText("Take from syllabified lyrics");
        underLyricsPanel.add(
            takeButton,
            new GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        translatedLyricsPanel = new JPanel();
        translatedLyricsPanel.setLayout(
            new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1)
        );
        panel3.add(
            translatedLyricsPanel,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        translatedLyricsPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "English translation",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            )
        );
        final JScrollPane scrollPane3 = new JScrollPane();
        translatedLyricsPanel.add(
            scrollPane3,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK |
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        translatedArea = new MyJTextArea();
        translatedArea.setColumns(30);
        translatedArea.setRows(6);
        translatedArea.setToolTipText(
            "This text is shown under the song as translation"
        );
        scrollPane3.setViewportView(translatedArea);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return centerPanel;
    }

    private class TakeUnderSongLyricsFromSyllabifiedLyricsAction
        implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            var underLyrics = StringUtils.removeSyllabifyMarkings(
                lyricsArea.getText()
            );
            underSongArea.setText(underLyrics);
        }
    }
}
