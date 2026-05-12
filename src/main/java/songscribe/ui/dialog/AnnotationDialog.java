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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.util.UIUtils;
import songscribe.message.mutation.ElementField;
import songscribe.music.Annotation;
import songscribe.music.StaffElement;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;

public class AnnotationDialog extends AttachmentDialog<Annotation> {

    private static final String DEFAULT_ANNOTATION = "fine";
    private static final String ANNOTATION_FILE = "annotations";

    private final JComboBox<String> annotationCombo = new JComboBox<>();
    private final JRadioButton leftRadio =
        new JRadioButton(Strings.get(Strings.LABEL_ALIGN_LEFT));
    private final JRadioButton centerRadio =
        new JRadioButton(Strings.get(Strings.LABEL_ALIGN_CENTER));
    private final JRadioButton rightRadio =
        new JRadioButton(Strings.get(Strings.LABEL_ALIGN_RIGHT));
    private final JRadioButton aboveRadio =
        new JRadioButton(Strings.get(Strings.DIALOG_ANNOTATION_ABOVE_STAFF));
    private final JRadioButton belowRadio =
        new JRadioButton(Strings.get(Strings.DIALOG_ANNOTATION_BELOW_STAFF));

    public AnnotationDialog() {
        super(Strings.get(Strings.DIALOG_ANNOTATION_TITLE));

        annotationCombo.setEditable(true);
        UIUtils.readComboValuesFromFile(annotationCombo, ANNOTATION_FILE);

        var alignmentGroup = new ButtonGroup();
        alignmentGroup.add(leftRadio);
        alignmentGroup.add(centerRadio);
        alignmentGroup.add(rightRadio);

        var verticalGroup = new ButtonGroup();
        verticalGroup.add(aboveRadio);
        verticalGroup.add(belowRadio);

        var alignmentSection = new TitledSection(Strings.get(Strings.DIALOG_ANNOTATION_ALIGNMENT));
        alignmentSection.add(leftRadio);
        alignmentSection.addSeparator();
        alignmentSection.add(centerRadio);
        alignmentSection.addSeparator();
        alignmentSection.add(rightRadio);

        var verticalSection = new TitledSection(Strings.get(Strings.DIALOG_ANNOTATION_VERTICAL));
        verticalSection.add(aboveRadio);
        verticalSection.addSeparator();
        verticalSection.add(belowRadio);

        alignmentSection.setAlignmentY(Component.TOP_ALIGNMENT);
        verticalSection.setAlignmentY(Component.TOP_ALIGNMENT);

        var sectionRow = new JPanel();
        sectionRow.setLayout(new BoxLayout(sectionRow, BoxLayout.X_AXIS));
        sectionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionRow.add(alignmentSection);
        sectionRow.add(Box.createHorizontalStrut(
            FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP)));
        sectionRow.add(verticalSection);

        var annotationLabel = new JLabel(Strings.get(Strings.LABEL_ANNOTATION));
        annotationLabel.setLabelFor(annotationCombo);
        var annotationRow = new JPanel(new FlowLayout(
            FlowLayout.LEFT,
            FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_GAP),
            0
        ));
        annotationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        annotationRow.add(annotationLabel);
        annotationRow.add(annotationCombo);

        var content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(annotationRow);
        content.add(Box.createVerticalStrut(
            FlatLafProps.<Integer>get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP)));
        content.add(sectionRow);

        contentPanel.add(BorderLayout.CENTER, content);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    @Override
    protected ElementField getElementField() {
        return ElementField.ANNOTATION;
    }

    @Override
    protected @Nullable Annotation getExistingChange(StaffElement element) {
        return element.getAnnotation();
    }

    @Override
    protected void populateControls(@Nullable Annotation change) {
        var annotation = change != null ? change : new Annotation(DEFAULT_ANNOTATION);

        annotationCombo.setSelectedItem(annotation.getAnnotation());

        var alignment = annotation.getXAlignment();

        if (alignment == Component.CENTER_ALIGNMENT) {
            centerRadio.setSelected(true);
        } else if (alignment == Component.RIGHT_ALIGNMENT) {
            rightRadio.setSelected(true);
        } else {
            leftRadio.setSelected(true);
        }

        if (annotation.getYPosPx() < 0) {
            aboveRadio.setSelected(true);
        } else {
            belowRadio.setSelected(true);
        }
    }

    @Override
    protected void applyChange(StaffElement element) {
        var annotationText = (String) annotationCombo.getSelectedItem();

        if (annotationText == null || annotationText.isEmpty()) {
            element.setAnnotation(null);
            return;
        }

        float alignment;

        if (centerRadio.isSelected()) {
            alignment = Component.CENTER_ALIGNMENT;
        } else if (rightRadio.isSelected()) {
            alignment = Component.RIGHT_ALIGNMENT;
        } else {
            alignment = Component.LEFT_ALIGNMENT;
        }

        var annotation = new Annotation(annotationText, alignment);
        annotation.setYPosPx(aboveRadio.isSelected() ? Annotation.ABOVE : Annotation.BELOW);
        element.setAnnotation(annotation);
    }

    @Override
    protected void clearChange(StaffElement element) {
        element.setAnnotation(null);
    }
}
