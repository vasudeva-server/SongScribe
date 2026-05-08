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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.export.PageLayoutData;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.GraphicUtils;
import songscribe.util.Utils;

public class PaperSizeStep extends Step {

    private static final String INFO =
        "<html>Set the output paper size and margins</html>";
    private static final double MM_PER_IN = 25.4;
    private final DefaultComboBoxModel<TemplateObject> templates =
        new DefaultComboBoxModel<>();
    private final SpinnerModel widthSpinnerModel = new SpinnerNumberModel(
        0.1,
        0.1,
        Double.MAX_VALUE,
        1.0
    );
    private final SpinnerModel heightSpinnerModel = new SpinnerNumberModel(
        0.1,
        0.1,
        Double.MAX_VALUE,
        1.0
    );
    private final SpinnerModel leftInnerSpinnerModel = new SpinnerNumberModel(
        0.1,
        0.1,
        Double.MAX_VALUE,
        1.0
    );
    private final SpinnerModel rightOuterSpinnerModel = new SpinnerNumberModel(
        0.1,
        0.1,
        Double.MAX_VALUE,
        1.0
    );
    private final SpinnerModel topSpinnerModel = new SpinnerNumberModel(
        0.1,
        0.1,
        Double.MAX_VALUE,
        1.0
    );
    private final SpinnerModel bottomSpinnerModel = new SpinnerNumberModel(
        0.1,
        0.1,
        Double.MAX_VALUE,
        1.0
    );
    private final JComboBox<?> unitsCombo = new JComboBox<>(
        new String[] {
            GraphicUtils.Unit.INCH.description(),
            GraphicUtils.Unit.CM.description(),
        }
    );
    private final JLabel leftInnerLabel = new JLabel();
    private final JLabel rightOuterLabel = new JLabel();
    private final JCheckBox mirroredCheck = new JCheckBox(Strings.get(Strings.DIALOG_PAPER_SIZE_MIRRORED));
    private GraphicUtils.Unit currentUnit = GraphicUtils.Unit.INCH;

    public PaperSizeStep(PageLayoutData pageLayoutData) {
        super(pageLayoutData);
        try {
            var br = new BufferedReader(
                new FileReader(Utils.getResourcePath("conf/papertemplates"))
            );
            var line = br.readLine();

            while ((line != null) && !line.isEmpty()) {
                templates.addElement(new TemplateObject(line));
                line = br.readLine();
            }
        } catch (IOException e) {
            // Ignore
        }

        templates.addElement(new TemplateObject("Custom"));

        var panel = new JPanel(new GridBagLayout());
        var c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.5;
        c.weighty = 0.5;
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(5, 5, 0, 5);
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_FROM_TEMPLATE)), c);
        c.gridy = 1;
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_PAPER_SIZE)), c);
        c.gridy = 2;
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_WIDTH)), c);
        c.gridy = 3;
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_HEIGHT)), c);
        c.gridy = 4;
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_MARGINS)), c);
        c.gridy = 5;
        panel.add(leftInnerLabel, c);
        c.gridy = 6;
        panel.add(rightOuterLabel, c);
        c.gridy = 7;
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_TOP)), c);
        c.gridy = 8;
        panel.add(new JLabel(Strings.get(Strings.DIALOG_PAPER_SIZE_BOTTOM)), c);
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        var templateCombo = new JComboBox<>(templates);
        templateCombo.addActionListener(new TemplateAction());
        panel.add(templateCombo, c);
        c.gridy = 1;
        panel.add(new JSeparator());
        c.gridwidth = 1;
        c.gridy = 2;
        var spinner = new JSpinner(widthSpinnerModel);
        var spinnerSize = new Dimension(70, 20);
        spinner.setPreferredSize(spinnerSize);
        panel.add(spinner, c);
        c.gridy = 3;
        spinner = new JSpinner(heightSpinnerModel);
        spinner.setPreferredSize(spinnerSize);
        panel.add(spinner, c);
        c.gridy = 4;
        panel.add(new JSeparator());
        c.gridy = 5;
        spinner = new JSpinner(leftInnerSpinnerModel);
        spinner.setPreferredSize(spinnerSize);
        panel.add(spinner, c);
        c.gridy = 6;
        spinner = new JSpinner(rightOuterSpinnerModel);
        spinner.setPreferredSize(spinnerSize);
        panel.add(spinner, c);
        c.gridy = 7;
        spinner = new JSpinner(topSpinnerModel);
        spinner.setPreferredSize(spinnerSize);
        panel.add(spinner, c);
        c.gridy = 8;
        spinner = new JSpinner(bottomSpinnerModel);
        spinner.setPreferredSize(spinnerSize);
        panel.add(spinner, c);
        c.gridx = 2;
        c.gridy = 2;
        c.gridheight = 2;
        unitsCombo.addActionListener(new UnitAction());
        panel.add(unitsCombo, c);
        c.gridy = 5;
        c.gridheight = 4;
        var mirroredAction = new MirroredAction();
        mirroredCheck.addActionListener(mirroredAction);
        panel.add(mirroredCheck, c);

        setLayout(new FlowLayout(FlowLayout.LEFT));
        add(panel);
        mirroredAction.actionPerformed(null);
    }

    @Override
    public String getInfo() {
        return INFO;
    }

    @Override
    public void start() {
        var metric = Prefs.getInstance().getBoolean(PrefsKey.METRIC);

        for (var i = 0; i < templates.getSize(); i++) {
            if (templates.getElementAt(i).metric == metric) {
                templates.setSelectedItem(templates.getElementAt(i));
                new TemplateAction().actionPerformed(null);
                break;
            }
        }
    }

    @Override
    public void end() {
        pageLayoutData.paperWidthPx = getValueInPixels(widthSpinnerModel);
        pageLayoutData.paperHeightPx = getValueInPixels(heightSpinnerModel);
        pageLayoutData.leftInnerMarginPx = getValueInPixels(
            leftInnerSpinnerModel
        );
        pageLayoutData.rightOuterMarginPx = getValueInPixels(
            rightOuterSpinnerModel
        );
        pageLayoutData.topMarginPx = getValueInPixels(topSpinnerModel);
        pageLayoutData.bottomMarginPx = getValueInPixels(bottomSpinnerModel);
        pageLayoutData.mirrored = mirroredCheck.isSelected();

        if (unitsCombo.getSelectedItem() instanceof TemplateObject) {
            Prefs.getInstance().put(
                PrefsKey.METRIC,
                ((TemplateObject) unitsCombo.getSelectedItem()).metric
            );
        } else {
            Prefs.getInstance().put(PrefsKey.METRIC, unitsCombo.getSelectedIndex() != 0);
        }
    }

    public int getValueInPixels(SpinnerModel model) {
        return currentUnit.convertToPixels((Double) model.getValue());
    }

    public void setValues(
        int pageWidth,
        int pageHeight,
        int leftInnerMargin,
        int rightOuterMargin,
        int topMargin,
        int bottomMargin,
        boolean mirroredMargin
    ) {
        var metric = GraphicUtils.Unit.create(Prefs.getInstance().getBoolean(PrefsKey.METRIC));
        unitsCombo.setSelectedIndex(metric.ordinal());
        widthSpinnerModel.setValue(
            GraphicUtils.convertFromPixels(pageWidth, metric)
        );
        heightSpinnerModel.setValue(
            GraphicUtils.convertFromPixels(pageHeight, metric)
        );
        templates.setSelectedItem(
            templates.getElementAt(templates.getSize() - 1)
        );

        for (var i = 0; i < templates.getSize(); i++) {
            //noinspection ConstantValue -- need for NullAway
            if (templates.getElementAt(i) != null) {
                var to = templates.getElementAt(i);

                if (
                    to.width.equals(widthSpinnerModel.getValue()) &&
                    to.height.equals(heightSpinnerModel.getValue())
                ) {
                    templates.setSelectedItem(to);
                }
            }
        }

        leftInnerSpinnerModel.setValue(
            GraphicUtils.convertFromPixels(leftInnerMargin, metric)
        );
        rightOuterSpinnerModel.setValue(
            GraphicUtils.convertFromPixels(rightOuterMargin, metric)
        );
        topSpinnerModel.setValue(
            GraphicUtils.convertFromPixels(topMargin, metric)
        );
        bottomSpinnerModel.setValue(
            GraphicUtils.convertFromPixels(bottomMargin, metric)
        );
        mirroredCheck.setSelected(mirroredMargin);
    }

    public void setMirroredCheckInvisible() {
        mirroredCheck.setVisible(false);
    }

    private static class TemplateObject {

        final String name;
        final Double width;
        final Double height;
        final Double margin;
        final String unit;
        final boolean metric;

        TemplateObject(String line) {
            var parts = line.split(";");
            name = (parts.length > 0) ? parts[0] : "";
            width = (parts.length > 1) ? Double.parseDouble(parts[1]) : 0d;
            height = (parts.length > 2) ? Double.parseDouble(parts[2]) : 0d;
            margin = (parts.length > 3) ? Double.parseDouble(parts[3]) : 0d;
            unit = (parts.length > 4) ? parts[4] : "";
            metric = (parts.length > 5) && parts[5].equals("metric");
        }

        public String toString() {
            return name;
        }
    }

    private class UnitAction implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            handleUnitChange();
        }

        private void handleUnitChange() {
            if (currentUnit == GraphicUtils.Unit.UNDETERMINED) {
                currentUnit = GraphicUtils.Unit.fromValue(
                    unitsCombo.getSelectedIndex()
                );
            }

            if (currentUnit.getValue() != unitsCombo.getSelectedIndex()) {
                var multiplier = (currentUnit == GraphicUtils.Unit.INCH)
                    ? MM_PER_IN
                    : (1.0 / MM_PER_IN);
                widthSpinnerModel.setValue(
                    (Double) widthSpinnerModel.getValue() * multiplier
                );
                heightSpinnerModel.setValue(
                    (Double) heightSpinnerModel.getValue() * multiplier
                );
                leftInnerSpinnerModel.setValue(
                    (Double) leftInnerSpinnerModel.getValue() * multiplier
                );
                rightOuterSpinnerModel.setValue(
                    (Double) rightOuterSpinnerModel.getValue() * multiplier
                );
                topSpinnerModel.setValue(
                    (Double) topSpinnerModel.getValue() * multiplier
                );
                bottomSpinnerModel.setValue(
                    (Double) bottomSpinnerModel.getValue() * multiplier
                );
                currentUnit = GraphicUtils.Unit.fromValue(
                    unitsCombo.getSelectedIndex()
                );
            }
        }
    }

    private class TemplateAction implements ActionListener {

        @Override
        public void actionPerformed(@Nullable ActionEvent e) {
            if (templates.getSelectedItem() instanceof TemplateObject to) {
                widthSpinnerModel.setValue(to.width);
                heightSpinnerModel.setValue(to.height);
                leftInnerSpinnerModel.setValue(to.margin);
                rightOuterSpinnerModel.setValue(to.margin);
                topSpinnerModel.setValue(to.margin);
                bottomSpinnerModel.setValue(to.margin);
                currentUnit = GraphicUtils.Unit.UNDETERMINED;
                unitsCombo.setSelectedItem(to.unit);
            }
        }
    }

    private class MirroredAction implements ActionListener {

        @Override
        public void actionPerformed(@Nullable ActionEvent e) {
            leftInnerLabel.setText(
                mirroredCheck.isSelected()
                    ? Strings.get(Strings.DIALOG_PAPER_SIZE_INNER)
                    : Strings.get(Strings.DIALOG_PAPER_SIZE_LEFT)
            );
            rightOuterLabel.setText(
                mirroredCheck.isSelected()
                    ? Strings.get(Strings.DIALOG_PAPER_SIZE_OUTER)
                    : Strings.get(Strings.DIALOG_PAPER_SIZE_RIGHT)
            );
        }
    }
}
