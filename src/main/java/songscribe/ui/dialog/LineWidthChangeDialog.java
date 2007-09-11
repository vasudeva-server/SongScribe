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

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import songscribe.ui.Constants;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.NumericTextField;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

public class LineWidthChangeDialog
    extends StandardDialog
    implements ActionListener {

    // The minimum/maximum staff line width in inches
    public static final double MIN_LINE_WIDTH_IN_INCHES = 5.0;
    public static final double MAX_LINE_WIDTH_IN_INCHES = 10.0;

    private final NumericTextField lineWidthField = new NumericTextField(
        6,
        true
    );

    private final JComboBox<String> unitCombo = new JComboBox<>(
        new String[] {
            GraphicUtils.Unit.INCH.description(),
            GraphicUtils.Unit.CM.description(),
        }
    );

    private GraphicUtils.Unit currentUnit = GraphicUtils.Unit.UNDETERMINED;
    private int originalWidth = 0;

    public LineWidthChangeDialog(MainFrame mainFrame) {
        super("Line Width");
        initContent();
    }

    private void initContent() {
        var controlsPanel = new JPanel(
            new FlowLayout(FlowLayout.LEFT, HORIZONTAL_SPACER.width, 0)
        );
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(20, 8, 0, 15));
        addLabeledField(
            controlsPanel,
            "Line width: ",
            lineWidthField,
            LabelPosition.LEFT
        );

        initUnitCombo();
        unitCombo.addActionListener(this);
        controlsPanel.add(unitCombo);
        contentPanel.add(BorderLayout.NORTH, controlsPanel);
        initButtons();
    }

    private void initButtons() {
        // Override the OK button so we can validate the line width
        var originalOkAction = okButton.getActionListeners()[0];
        okButton.removeActionListener(originalOkAction);
        okButton.addActionListener(_ -> {
            var widthInInches = validateWidth();

            if (widthInInches < 0) {
                return;
            }

            var lineWidth = (int) Math.round(
                widthInInches * UIUtils.RESOLUTION
            );

            if (lineWidth != originalWidth) {
                var score = mainFrame.getScore();
                score.setLineWidth(lineWidth);
                score.getComposition().setModified(true);
            }

            originalOkAction.actionPerformed(null);
        });

        cancelButton.addActionListener(
            // Reset values so the dialog will be reinitialized when reopened
            _ -> {
                currentUnit = GraphicUtils.Unit.UNDETERMINED;
                lineWidthField.setText("");
            }
        );
        buttonPanel.remove(applyButton);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    private void initUnitCombo() {
        unitCombo.setSelectedIndex(propIsMetric() ? 1 : 0);
    }

    private boolean propIsMetric() {
        return mainFrame
            .getProperties()
            .getProperty(Constants.METRIC)
            .equals(Constants.TRUE_VALUE);
    }

    private boolean isMetric() {
        return unitCombo.getSelectedIndex() == 1;
    }

    private GraphicUtils.Unit getSelectedUnit() {
        return isMetric() ? GraphicUtils.Unit.CM : GraphicUtils.Unit.INCH;
    }

    @Override
    protected void getData() {
        originalWidth = mainFrame.getScore().getComposition().getLineWidth();
        actionPerformed(null);
    }

    @Override
    protected void setData() {
        var metricValue = isMetric()
            ? Constants.TRUE_VALUE
            : Constants.FALSE_VALUE;
        mainFrame.getProperties().setProperty(Constants.METRIC, metricValue);
    }

    private double getEnteredWidthInInches(GraphicUtils.Unit unit)
        throws NumberFormatException {
        var width = Double.parseDouble(lineWidthField.getText());

        if (unit == GraphicUtils.Unit.CM) {
            width /= GraphicUtils.CM_PER_INCH;
        }

        return width;
    }

    private double validateWidth() {
        double width;

        try {
            width = getEnteredWidthInInches(getSelectedUnit());
        } catch (NumberFormatException e) {
            mainFrame.showErrorMessage("Invalid line width.");
            return -1;
        }

        if (
            (width >= MIN_LINE_WIDTH_IN_INCHES) &&
            (width <= MAX_LINE_WIDTH_IN_INCHES)
        ) {
            return width;
        }

        var min = MIN_LINE_WIDTH_IN_INCHES;
        var max = MAX_LINE_WIDTH_IN_INCHES;

        if (isMetric()) {
            min *= GraphicUtils.CM_PER_INCH;
            max *= GraphicUtils.CM_PER_INCH;
        }

        mainFrame.showErrorMessage(
            "Line width must be between " +
            min +
            " and " +
            max +
            ' ' +
            (isMetric() ? "cm" : "inches") +
            '.'
        );

        return -1;
    }

    // Called when a value in the unit combo is selected
    @Override
    public void actionPerformed(ActionEvent e) {
        // This method could be called when the current unit is reselected
        var selectedUnit = getSelectedUnit();

        if (currentUnit == selectedUnit) {
            return;
        }

        currentUnit = selectedUnit;
        var text = lineWidthField.getText();
        double value;

        if (text.isEmpty()) {
            var composition = mainFrame.getScore().getComposition();
            value = (double) composition.getLineWidth() / UIUtils.RESOLUTION;
        } else {
            try {
                // The value in the text field is in the previously selected unit
                var unit = isMetric()
                    ? GraphicUtils.Unit.INCH
                    : GraphicUtils.Unit.CM;

                value = getEnteredWidthInInches(unit);
            } catch (NumberFormatException ex) {
                unitCombo.setSelectedIndex(propIsMetric() ? 1 : 0);
                return;
            }
        }

        if (isMetric()) {
            value *= GraphicUtils.CM_PER_INCH;
        }

        lineWidthField.setText(
            String.valueOf(Utils.roundToTwoDecimalPlaces(value))
        );
    }
}
