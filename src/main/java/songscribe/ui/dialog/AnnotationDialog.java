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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Annotation;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.NonBlankGuard;
import songscribe.util.UIUtils;

/**
 * An editable text combo plus alignment and placement radios, for editing the annotation on an
 * element.
 *
 * <p><strong>The text is never blank.</strong> {@link Annotation} does not permit it, and the combo
 * is editable, so a {@link NonBlankGuard} on its editor answers a blank field with an alert and the
 * previous text — as focus leaves, and again at {@link #gather} for an OK that commits before focus
 * has moved. Emptying the field is therefore not a way to delete an annotation; the Remove button
 * is.
 *
 * <p>The text is read from the combo's editor rather than its selected item, per
 * {@link UIUtils#comboEditor}: the two disagree precisely when the guard has just restored a
 * value the combo already committed.
 */
public class AnnotationDialog extends AttachmentDialog<Annotation> {

    static final String DEFAULT_ANNOTATION = "Fine";
    private static final String ANNOTATION_FILE = "annotations";

    final JComboBox<String> annotationCombo = new JComboBox<>();
    final JRadioButton leftRadio =
        new JRadioButton(Strings.get(Strings.LABEL_ALIGN_LEFT));
    final JRadioButton centerRadio =
        new JRadioButton(Strings.get(Strings.LABEL_ALIGN_CENTER));
    final JRadioButton rightRadio =
        new JRadioButton(Strings.get(Strings.LABEL_ALIGN_RIGHT));
    final JRadioButton aboveRadio =
        new JRadioButton(Strings.get(Strings.DIALOG_ANNOTATION_ABOVE_STAFF));
    final JRadioButton belowRadio =
        new JRadioButton(Strings.get(Strings.DIALOG_ANNOTATION_BELOW_STAFF));
    private final NonBlankGuard blankGuard;

    public AnnotationDialog(MainFrame mainFrame, DialogOps<@Nullable Annotation, Annotation> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_ANNOTATION_TITLE), ops);

        annotationCombo.setEditable(true);
        UIUtils.readComboValuesFromFile(annotationCombo, ANNOTATION_FILE);

        var comboEditor = UIUtils.comboEditor(annotationCombo);
        blankGuard = new NonBlankGuard(comboEditor, DEFAULT_ANNOTATION);
        comboEditor.setInputVerifier(blankGuard);

        var alignmentGroup = new ButtonGroup();
        alignmentGroup.add(leftRadio);
        alignmentGroup.add(centerRadio);
        alignmentGroup.add(rightRadio);

        var verticalGroup = new ButtonGroup();
        verticalGroup.add(aboveRadio);
        verticalGroup.add(belowRadio);

        var alignmentSection = new TitledSection(Strings.get(Strings.DIALOG_ANNOTATION_ALIGNMENT));
        alignmentSection.add(leftRadio);
        addSeparator(alignmentSection);
        alignmentSection.add(centerRadio);
        addSeparator(alignmentSection);
        alignmentSection.add(rightRadio);

        var verticalSection = new TitledSection(Strings.get(Strings.DIALOG_ANNOTATION_VERTICAL));
        verticalSection.add(aboveRadio);
        addSeparator(verticalSection);
        verticalSection.add(belowRadio);

        alignmentSection.setAlignmentY(Component.TOP_ALIGNMENT);
        verticalSection.setAlignmentY(Component.TOP_ALIGNMENT);

        var sectionRow = new JPanel();
        sectionRow.setLayout(new BoxLayout(sectionRow, BoxLayout.X_AXIS));
        sectionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionRow.add(alignmentSection);
        sectionRow.add(Box.createHorizontalStrut(
            FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP)));
        sectionRow.add(verticalSection);

        var annotationLabel = new JLabel(Strings.get(Strings.LABEL_ANNOTATION));
        annotationLabel.setLabelFor(annotationCombo);
        var annotationRow = new JPanel(new FlowLayout(
            FlowLayout.LEFT,
            FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP),
            0
        ));
        annotationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        annotationRow.add(annotationLabel);
        annotationRow.add(annotationCombo);

        var content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(annotationRow);
        content.add(Box.createVerticalStrut(
            FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP)));
        content.add(sectionRow);

        contentPanel.add(BorderLayout.CENTER, content);
    }

    @Override
    protected void populateControls(@Nullable Annotation existingChange) {
        var annotation = existingChange != null ? existingChange : new Annotation(DEFAULT_ANNOTATION);

        annotationCombo.setSelectedItem(annotation.getAnnotation());
        blankGuard.rememberCurrentText();

        var alignment = annotation.getXAlignment();

        if (alignment == Component.CENTER_ALIGNMENT) {
            centerRadio.setSelected(true);
        } else if (alignment == Component.RIGHT_ALIGNMENT) {
            rightRadio.setSelected(true);
        } else {
            leftRadio.setSelected(true);
        }

        if (annotation.getPlacement() == Annotation.Placement.ABOVE) {
            aboveRadio.setSelected(true);
        } else {
            belowRadio.setSelected(true);
        }
    }

    @Override
    protected Annotation gather() {
        var annotation = new Annotation(blankGuard.text(), selectedAlignment());
        annotation.setPlacement(
            aboveRadio.isSelected() ? Annotation.Placement.ABOVE : Annotation.Placement.BELOW);

        return annotation;
    }

    /**
     * @return the {@code Component} alignment constant the selected alignment radio stands for;
     *         left when none is selected, matching the button group's initial state
     */
    private float selectedAlignment() {
        if (centerRadio.isSelected()) {
            return Component.CENTER_ALIGNMENT;
        }

        if (rightRadio.isSelected()) {
            return Component.RIGHT_ALIGNMENT;
        }

        return Component.LEFT_ALIGNMENT;
    }
}
