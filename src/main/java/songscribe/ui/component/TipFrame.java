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
package songscribe.ui.component;

import module java.desktop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.jdesktop.layout.GroupLayout;
import org.jdesktop.layout.LayoutStyle;

import songscribe.Strings;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Dialogs;
import songscribe.util.Utils;

public class TipFrame extends JFrame {

    private final MainFrame mainFrame;
    private static final File tipFile = new File(
        Utils.getResourcePath("help/tips")
    );
    private int index;
    private final StringBuilder tipBuffer = new StringBuilder(256);

    private JTextPane tipPane;
    private JCheckBox showTip;

    public TipFrame(MainFrame mainFrame)
        throws IOException, FileNotFoundException {
        super(Strings.get(Strings.DIALOG_TIP_TITLE));
        this.mainFrame = mainFrame;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    closeWindow();
                }
            }
        );
        index = Prefs.getInstance().getInt(PrefsKey.TIP_INDEX);
        initComponents();
        showTip();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        var didYouKnowLabel = new JLabel();
        var tipScroll = new JScrollPane();
        tipPane = new JTextPane();
        showTip = new JCheckBox();
        var closeButton = new JButton();
        var nextButton = new JButton();
        var previousButton = new JButton();

        didYouKnowLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        didYouKnowLabel.setText(Strings.get(Strings.LABEL_TIP_DID_YOU_KNOW));

        tipPane.setEditable(false);
        tipPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        tipScroll.setViewportView(tipPane);

        showTip.setSelected(Prefs.getInstance().getBoolean(PrefsKey.SHOW_TIPS));
        showTip.setText(Strings.get(Strings.LABEL_TIP_SHOW_ON_STARTUP));
        showTip.setBorder(BorderFactory.createEmptyBorder());
        showTip.setMargin(new Insets(0, 0, 0, 0));

        closeButton.setText(Strings.get(Strings.DIALOG_BUTTON_CLOSE));
        closeButton.addActionListener(_ -> closeWindow());

        nextButton.setText(Strings.get(Strings.LABEL_TIP_NEXT));
        nextButton.addActionListener(_ -> {
            try {
                showTip();
            } catch (IOException e1) {
                Dialogs.showErrorMessage(
                    this,
                    Strings.get(Strings.DIALOG_TITLE_TIP_ERROR),
                    Strings.get(Strings.ERROR_TIP_READ)
                );
            }
        });

        previousButton.setText(Strings.get(Strings.LABEL_TIP_PREVIOUS));
        previousButton.addActionListener(_ -> {
            try {
                if (index > 1) {
                    index -= 2;
                    showTip();
                }
            } catch (IOException e1) {
                Dialogs.showErrorMessage(
                    this,
                    Strings.get(Strings.DIALOG_TITLE_TIP_ERROR),
                    Strings.get(Strings.ERROR_TIP_READ)
                );
            }
        });

        var layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    layout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.LEADING, false)
                                .add(
                                    layout
                                        .createSequentialGroup()
                                        .add(
                                            layout
                                                .createParallelGroup(
                                                    GroupLayout.TRAILING
                                                )
                                                .add(
                                                    layout
                                                        .createSequentialGroup()
                                                        .add(
                                                            layout
                                                                .createParallelGroup(
                                                                    GroupLayout.LEADING
                                                                )
                                                                .add(
                                                                    didYouKnowLabel
                                                                )
                                                                .add(showTip)
                                                        )
                                                        .addPreferredGap(
                                                            LayoutStyle.RELATED,
                                                            GroupLayout.DEFAULT_SIZE,
                                                            Short.MAX_VALUE
                                                        )
                                                )
                                                .add(
                                                    layout
                                                        .createSequentialGroup()
                                                        .add(previousButton)
                                                        .addPreferredGap(
                                                            LayoutStyle.RELATED
                                                        )
                                                )
                                        )
                                        .add(nextButton)
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(closeButton)
                                )
                                .add(
                                    tipScroll,
                                    GroupLayout.PREFERRED_SIZE,
                                    376,
                                    GroupLayout.PREFERRED_SIZE
                                )
                        )
                        .addContainerGap(
                            GroupLayout.DEFAULT_SIZE,
                            Short.MAX_VALUE
                        )
                )
        );
        layout.setVerticalGroup(
            layout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    layout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(didYouKnowLabel)
                        .addPreferredGap(LayoutStyle.RELATED)
                        .add(
                            tipScroll,
                            GroupLayout.PREFERRED_SIZE,
                            151,
                            GroupLayout.PREFERRED_SIZE
                        )
                        .add(15, 15, 15)
                        .add(showTip)
                        .addPreferredGap(
                            LayoutStyle.RELATED,
                            21,
                            Short.MAX_VALUE
                        )
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.BASELINE)
                                .add(closeButton)
                                .add(nextButton)
                                .add(previousButton)
                        )
                        .addContainerGap()
                )
        );
        pack();
    }

    private void closeWindow() {
        Prefs.getInstance().put(PrefsKey.SHOW_TIPS, showTip.isSelected());
        setVisible(false);
        dispose();
    }

    private void showTip() throws IOException, FileNotFoundException {
        var fr = new FileReader(tipFile);
        tipBuffer.delete(0, tipBuffer.length());
        var breakFound = 0;
        var ch = fr.read();

        while ((ch != -1) && (breakFound <= index)) {
            if (breakFound == index) {
                tipBuffer.append((char) ch);
            }

            if (ch == '\n') {
                breakFound++;
            }

            ch = fr.read();
        }

        fr.close();

        if (tipBuffer.isEmpty()) {
            index = 0;
            showTip();
        } else {
            index++;
        }

        tipPane.setText(tipBuffer.toString());
        Prefs.getInstance().put(PrefsKey.TIP_INDEX, index);
    }
}
