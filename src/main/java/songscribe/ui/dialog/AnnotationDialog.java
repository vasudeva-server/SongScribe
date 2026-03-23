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

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.jdesktop.layout.GroupLayout;
import org.jdesktop.layout.LayoutStyle;

import songscribe.Strings;
import songscribe.music.Annotation;
import songscribe.ui.OptionDialogs;
import songscribe.music.StaffElement;
import songscribe.file.FileUtils;

/**
 * A dialog for adding or modifying an annotation on an element.
 */
public class AnnotationDialog extends StandardDialog {

    private @Nullable StaffElement selectedElement = null;
    private final JComboBox<String> annotationCombo;
    private final JButton removeButton;
    private final JRadioButton aboveButton;
    private final JRadioButton belowButton;

    public AnnotationDialog() {
        super(Strings.get(Strings.DIALOG_ANNOTATION_TITLE));
        //----------------------centerPanel------------------------
        var centerPanel = new JPanel();
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var annotationLabel = new JLabel(Strings.get(Strings.LABEL_ANNOTATION));
        annotationCombo = new JComboBox<>();
        annotationCombo.setEditable(true);

        FileUtils.readComboValuesFromFile(annotationCombo, "annotations");
        var xPanel = new JPanel();

        xPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                Strings.get(Strings.DIALOG_ANNOTATION_ALIGNMENT)
            )
        );

        var verticalPanel = new JPanel();
        aboveButton = new JRadioButton();
        belowButton = new JRadioButton();

        Alignment.left.button.setText(Strings.get(Strings.LABEL_ALIGN_LEFT));
        Alignment.left.button.setBorder(BorderFactory.createEmptyBorder());
        Alignment.left.button.setMargin(new Insets(0, 0, 0, 0));

        Alignment.center.button.setText(Strings.get(Strings.LABEL_ALIGN_CENTER));
        Alignment.center.button.setBorder(BorderFactory.createEmptyBorder());
        Alignment.center.button.setMargin(new Insets(0, 0, 0, 0));

        Alignment.right.button.setText(Strings.get(Strings.LABEL_ALIGN_RIGHT));
        Alignment.right.button.setBorder(BorderFactory.createEmptyBorder());
        Alignment.right.button.setMargin(new Insets(0, 0, 0, 0));

        var alignmentGroup = new ButtonGroup();
        alignmentGroup.add(Alignment.left.button);
        alignmentGroup.add(Alignment.center.button);
        alignmentGroup.add(Alignment.right.button);

        var xPanelLayout = new GroupLayout(xPanel);
        xPanel.setLayout(xPanelLayout);
        xPanelLayout.setHorizontalGroup(
            xPanelLayout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    xPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(
                            xPanelLayout
                                .createParallelGroup(GroupLayout.LEADING)
                                .add(Alignment.left.button)
                                .add(Alignment.center.button)
                                .add(Alignment.right.button)
                        )
                        .addContainerGap(39, Short.MAX_VALUE)
                )
        );

        xPanelLayout.setVerticalGroup(
            xPanelLayout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    xPanelLayout
                        .createSequentialGroup()
                        .add(Alignment.left.button)
                        .addPreferredGap(LayoutStyle.RELATED)
                        .add(Alignment.center.button)
                        .addPreferredGap(LayoutStyle.RELATED)
                        .add(Alignment.right.button)
                )
        );

        verticalPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                Strings.get(Strings.DIALOG_ANNOTATION_VERTICAL)
            )
        );

        aboveButton.setText(Strings.get(Strings.DIALOG_ANNOTATION_ABOVE_STAFF));
        aboveButton.setBorder(BorderFactory.createEmptyBorder());
        aboveButton.setMargin(new Insets(0, 0, 0, 0));

        belowButton.setText(Strings.get(Strings.DIALOG_ANNOTATION_BELOW_STAFF));
        belowButton.setBorder(BorderFactory.createEmptyBorder());
        belowButton.setMargin(new Insets(0, 0, 0, 0));

        var verticalGroup = new ButtonGroup();
        verticalGroup.add(aboveButton);
        verticalGroup.add(belowButton);

        var verticalPanelLayout = new GroupLayout(verticalPanel);
        verticalPanel.setLayout(verticalPanelLayout);
        verticalPanelLayout.setHorizontalGroup(
            verticalPanelLayout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    verticalPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(
                            verticalPanelLayout
                                .createParallelGroup(GroupLayout.LEADING)
                                .add(belowButton)
                                .add(aboveButton)
                        )
                        .addContainerGap(
                            GroupLayout.DEFAULT_SIZE,
                            Short.MAX_VALUE
                        )
                )
        );

        verticalPanelLayout.setVerticalGroup(
            verticalPanelLayout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    GroupLayout.TRAILING,
                    verticalPanelLayout
                        .createSequentialGroup()
                        .addContainerGap(
                            GroupLayout.DEFAULT_SIZE,
                            Short.MAX_VALUE
                        )
                        .add(aboveButton)
                        .addPreferredGap(LayoutStyle.RELATED)
                        .add(belowButton)
                        .addContainerGap()
                )
        );

        var layout = new GroupLayout(centerPanel);
        centerPanel.setLayout(layout);
        layout.setHorizontalGroup(
            layout
                .createParallelGroup(GroupLayout.LEADING)
                .add(
                    layout
                        .createSequentialGroup()
                        .addContainerGap()
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.LEADING)
                                .add(
                                    layout
                                        .createSequentialGroup()
                                        .add(
                                            xPanel,
                                            GroupLayout.PREFERRED_SIZE,
                                            GroupLayout.DEFAULT_SIZE,
                                            GroupLayout.PREFERRED_SIZE
                                        )
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            verticalPanel,
                                            GroupLayout.DEFAULT_SIZE,
                                            GroupLayout.DEFAULT_SIZE,
                                            Short.MAX_VALUE
                                        )
                                )
                                .add(
                                    layout
                                        .createSequentialGroup()
                                        .add(annotationLabel)
                                        .addPreferredGap(LayoutStyle.RELATED)
                                        .add(
                                            annotationCombo,
                                            0,
                                            190,
                                            Short.MAX_VALUE
                                        )
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
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.BASELINE)
                                .add(annotationLabel)
                                .add(
                                    annotationCombo,
                                    GroupLayout.PREFERRED_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.PREFERRED_SIZE
                                )
                        )
                        .addPreferredGap(LayoutStyle.RELATED)
                        .add(
                            layout
                                .createParallelGroup(GroupLayout.LEADING, false)
                                .add(
                                    verticalPanel,
                                    GroupLayout.DEFAULT_SIZE,
                                    GroupLayout.DEFAULT_SIZE,
                                    Short.MAX_VALUE
                                )
                                .add(
                                    xPanel,
                                    GroupLayout.PREFERRED_SIZE,
                                    88,
                                    Short.MAX_VALUE
                                )
                        )
                        .addContainerGap(
                            GroupLayout.DEFAULT_SIZE,
                            Short.MAX_VALUE
                        )
                )
        );

        contentPanel.add(centerPanel);

        // Buttons
        var south = new JPanel();
        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            var score = Objects.requireNonNull(getScore());
            Objects.requireNonNull(selectedElement).setAnnotation(null);
            setVisible(false);
            score.getComposition().setModified(true);
            score.repaint();
        });

        south.add(okButton);
        south.add(removeButton);
        south.add(cancelButton);
        contentPanel.add(BorderLayout.SOUTH, south);
    }

    @Override
    protected boolean getData() {
        var score = Objects.requireNonNull(getScore());
        Annotation annotation = null;
        var hasExistingAnnotation = false;
        selectedElement = score.getSingleSelectedElement();

        if (selectedElement != null) {
            annotation = selectedElement.getAnnotation();
        }
        if (annotation == null) {
            annotation = new Annotation("fine");
        } else {
            hasExistingAnnotation = true;
        }

        annotationCombo.setSelectedItem(annotation.getAnnotation());

        for (var alignment : Alignment.values()) {
            if (annotation.getXAlignment() == alignment.value) {
                alignment.button.setSelected(true);
            }
        }

        var oldVerticalButton = (annotation.getYPosPx() < 0)
            ? aboveButton
            : belowButton;

        oldVerticalButton.setSelected(true);
        removeButton.setEnabled(hasExistingAnnotation);

        okButton.setText(Strings.get(hasExistingAnnotation ? Strings.LABEL_BUTTON_MODIFY : Strings.LABEL_BUTTON_ADD));
        return true;
    }

    @Override
    protected void setData() {
        @Nullable
        Annotation annotation;
        var annotationText = (String) annotationCombo.getSelectedItem();

        if ((annotationText == null) || annotationText.isEmpty()) {
            annotation = null;
        } else {
            // Horizontal alignment
            var horizontalAlignment = Arrays.stream(Alignment.values())
                .filter(alignment -> alignment.button.isSelected())
                .findFirst()
                .orElse(null);

            if (horizontalAlignment == null) {
                var message = Strings.get(Strings.ERROR_PROGRAMMER_NO_HORIZONTAL_ANNOTATION);
                OptionDialogs.showErrorMessage(
                    getMainFrame(),
                    Strings.get(Strings.DIALOG_TITLE_ANNOTATION_ERROR),
                    message
                );
                throw new RuntimeException(message);
            }

            annotation = new Annotation(
                annotationText,
                horizontalAlignment.value
            );

            // Vertical alignment
            int yPosPx;

            if (aboveButton.isSelected()) {
                yPosPx = Annotation.ABOVE;
            } else if (belowButton.isSelected()) {
                yPosPx = Annotation.BELOW;
            } else {
                var message = Strings.get(Strings.ERROR_PROGRAMMER_NO_VERTICAL_ANNOTATION);
                OptionDialogs.showErrorMessage(
                    getMainFrame(),
                    Strings.get(Strings.DIALOG_TITLE_ANNOTATION_ERROR),
                    message
                );
                throw new RuntimeException(message);
            }

            annotation.setYPosPx(yPosPx);
        }

        Objects.requireNonNull(selectedElement).setAnnotation(annotation);
        getComposition().setModified(true);
    }

    private enum Alignment {
        left(Component.LEFT_ALIGNMENT),
        center(Component.CENTER_ALIGNMENT),
        right(Component.RIGHT_ALIGNMENT);

        final JRadioButton button;
        final float value;

        Alignment(float value) {
            button = new JRadioButton();
            this.value = value;
        }
    }
}
