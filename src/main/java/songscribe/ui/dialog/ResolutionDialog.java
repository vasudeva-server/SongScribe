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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import songscribe.export.ExportOptions;
import songscribe.prefs.Prefs;
import songscribe.Strings;
import songscribe.ui.component.BorderPanel;
import songscribe.ui.component.MyBorder;
import songscribe.util.GraphicUtils;

@SuppressWarnings("NonStaticInitializer")
public class ResolutionDialog extends StandardDialog implements ChangeListener {

    private boolean approved = false;
    private JPanel mainPanel;
    private JTextField widthField;
    private JTextField heightField;
    private JCheckBox withoutLyricsCheck;
    private JSpinner resolutionSpinner;
    private BorderPanel borderPanel;
    private JCheckBox exportWithoutTitleCheckBox;
    private int sheetWidth = 0;
    private int sheetHeight = 0;
    private int sheetHeightWithoutLyrics = 0;
    private int sheetHeightWithoutTitle = 0;

    public ResolutionDialog() {
        super(Strings.get(Strings.DIALOG_RESOLUTION_TITLE));
        borderPanel.setPackListener(_ -> pack());
        borderPanel.addChangeListener(this);
        resolutionSpinner.addChangeListener(this);
        withoutLyricsCheck.addChangeListener(this);
        exportWithoutTitleCheckBox.addChangeListener(this);
        resolutionSpinner.setModel(new SpinnerNumberModel(300, 30, 1200, 1));
        contentPanel.add(BorderLayout.CENTER, mainPanel);
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 13, 0));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected void getData() {
        approved = false;
        resolutionSpinner.setValue(Prefs.getInstance().getInt("exportDpi"));
        var score = getScore();
        var composition = score.getComposition();
        sheetWidth = score.getSheetWidthPx();
        sheetHeight = score.getSheetHeightPx();

        var noLyricsOptions = new ExportOptions(false, true, true);
        sheetHeightWithoutLyrics = sheetHeight - score.getSheetHeightPx(noLyricsOptions);

        var noTitleOptions = new ExportOptions(true, false, true);
        sheetHeightWithoutTitle = sheetHeight - score.getSheetHeightPx(noTitleOptions);

        var underLyrics = composition.getUnderLyrics();
        var translatedLyrics = composition.getTranslatedLyrics();

        if (underLyrics.isEmpty() && translatedLyrics.isEmpty()) {
            withoutLyricsCheck.setSelected(false);
            withoutLyricsCheck.setEnabled(false);
        } else {
            withoutLyricsCheck.setEnabled(true);
        }

        if (composition.getTitle().isEmpty()) {
            exportWithoutTitleCheckBox.setSelected(false);
            exportWithoutTitleCheckBox.setEnabled(false);
        } else {
            exportWithoutTitleCheckBox.setEnabled(true);
        }

        borderPanel.setExpertBorder(false);
        stateChanged(null);
    }

    @Override
    protected void setData() {
        approved = true;
        Prefs.getInstance().put("exportDpi", (int) resolutionSpinner.getValue());
    }

    public boolean isApproved() {
        return approved;
    }

    public int getResolution() {
        return (Integer) resolutionSpinner.getValue();
    }

    public boolean isWithoutLyrics() {
        return withoutLyricsCheck.isSelected();
    }

    public boolean isWithoutTitle() {
        return exportWithoutTitleCheckBox.isSelected();
    }

    public MyBorder getBorder() {
        return borderPanel.getMyBorder();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        var scale = (float) getResolution() / (float) GraphicUtils.getDpi();
        var myBorder = borderPanel.getMyBorder();
        widthField.setText(
            Integer.toString(
                Math.round(scale * sheetWidth) + myBorder.getWidth()
            )
        );
        var height = sheetHeight;

        if (withoutLyricsCheck.isSelected()) {
            height -= sheetHeightWithoutLyrics;
        }

        if (exportWithoutTitleCheckBox.isSelected()) {
            height -= sheetHeightWithoutTitle;
        }

        heightField.setText(
            Integer.toString(Math.round(scale * height) + myBorder.getHeight())
        );
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
        mainPanel = new JPanel();
        mainPanel.setLayout(
            new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1)
        );
        mainPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                null,
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            )
        );
        final JPanel panel1 = new JPanel();
        panel1.setLayout(
            new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1)
        );
        mainPanel.add(
            panel1,
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
        final JLabel label1 = new JLabel();
        label1.setText(Strings.get(Strings.LABEL_IMAGE_RESOLUTION));
        panel1.add(
            label1,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
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
                3,
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
        resolutionSpinner = new JSpinner();
        panel1.add(
            resolutionSpinner,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                new Dimension(70, 22),
                null,
                0,
                false
            )
        );
        final JLabel label2 = new JLabel();
        label2.setText(Strings.get(Strings.LABEL_DPI));
        panel1.add(
            label2,
            new GridConstraints(
                0,
                2,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer2 = new Spacer();
        mainPanel.add(
            spacer2,
            new GridConstraints(
                5,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                1,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false
            )
        );
        withoutLyricsCheck = new JCheckBox();
        withoutLyricsCheck.setText(Strings.get(Strings.LABEL_EXPORT_WITHOUT_LYRICS));
        mainPanel.add(
            withoutLyricsCheck,
            new GridConstraints(
                3,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
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
        final JPanel panel2 = new JPanel();
        panel2.setLayout(
            new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1)
        );
        mainPanel.add(
            panel2,
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
        borderPanel = new BorderPanel();
        panel2.add(
            borderPanel.$$$getRootComponent$$$(),
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_NONE,
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
        final Spacer spacer3 = new Spacer();
        panel2.add(
            spacer3,
            new GridConstraints(
                0,
                1,
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
        final JPanel panel3 = new JPanel();
        panel3.setLayout(
            new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1)
        );
        mainPanel.add(
            panel3,
            new GridConstraints(
                4,
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
        final JPanel panel4 = new JPanel();
        panel4.setLayout(
            new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1)
        );
        panel3.add(
            panel4,
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
        panel4.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                Strings.get(Strings.LABEL_IMAGE_SIZE),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            )
        );
        final JLabel label3 = new JLabel();
        label3.setText(Strings.get(Strings.LABEL_WIDTH));
        panel4.add(
            label3,
            new GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_EAST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JLabel label4 = new JLabel();
        label4.setText(Strings.get(Strings.LABEL_HEIGHT));
        panel4.add(
            label4,
            new GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_EAST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        widthField = new JTextField();
        widthField.setEditable(false);
        panel4.add(
            widthField,
            new GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                new Dimension(50, -1),
                null,
                0,
                false
            )
        );
        heightField = new JTextField();
        heightField.setEditable(false);
        panel4.add(
            heightField,
            new GridConstraints(
                1,
                1,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                new Dimension(50, -1),
                null,
                0,
                false
            )
        );
        final JLabel label5 = new JLabel();
        label5.setText(Strings.get(Strings.LABEL_PX));
        panel4.add(
            label5,
            new GridConstraints(
                0,
                2,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final JLabel label6 = new JLabel();
        label6.setText(Strings.get(Strings.LABEL_PX));
        panel4.add(
            label6,
            new GridConstraints(
                1,
                2,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        );
        final Spacer spacer4 = new Spacer();
        panel3.add(
            spacer4,
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
        final Spacer spacer5 = new Spacer();
        panel3.add(
            spacer5,
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
        exportWithoutTitleCheckBox = new JCheckBox();
        exportWithoutTitleCheckBox.setText(Strings.get(Strings.LABEL_EXPORT_WITHOUT_TITLE));
        mainPanel.add(
            exportWithoutTitleCheckBox,
            new GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
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
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }
}
