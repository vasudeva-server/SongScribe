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
package songscribe.ui.dialog.fontchooser;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionListener;

import com.uber.nullaway.annotations.Initializer;

import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.dialog.BaseDialog;
import songscribe.ui.dialog.BaseDialog.LabelPosition;
import songscribe.ui.dialog.fontchooser.listeners.FamilyListSelectionListener;
import songscribe.ui.dialog.fontchooser.listeners.SizeListSelectionListener;
import songscribe.ui.dialog.fontchooser.listeners.StyleListSelectionListener;
import songscribe.ui.dialog.fontchooser.model.DefaultFontSelectionModel;
import songscribe.ui.dialog.fontchooser.model.FontSelectionModel;
import songscribe.ui.dialog.fontchooser.panes.FamilyPane;
import songscribe.ui.dialog.fontchooser.panes.PreviewPane;
import songscribe.ui.dialog.fontchooser.panes.SizePane;
import songscribe.ui.dialog.fontchooser.panes.StyleEntry;
import songscribe.ui.dialog.fontchooser.panes.StylePane;

/**
 * @author Christos Bohoris
 * @author Aparaajita Fishman
 */
public class FontChooser extends JPanel implements FontContainer {

    private static final String SELECTION_MODEL_PROPERTY = "selectionModel";

    @SuppressWarnings("NullAway.Init")
    private FontSelectionModel selectionModel;

    private final FamilyPane familyPane = new FamilyPane();

    private final PreviewPane previewPane = new PreviewPane();

    private final StylePane stylePane = new StylePane();

    private final SizePane sizePane = new SizePane();

    private final ListSelectionListener familyPaneListener =
        new FamilyListSelectionListener(this);

    private final ListSelectionListener stylePaneListener =
        new StyleListSelectionListener(this);

    private final ListSelectionListener sizePaneListener =
        new SizeListSelectionListener(this);

    public FontChooser() {
        this(
            new Font(
                Font.SANS_SERIF,
                Font.PLAIN,
                UIManager.getFont("Label.font").getSize()
            )
        );
    }

    public FontChooser(Font initialFont) {
        this(new DefaultFontSelectionModel(initialFont));
    }

    public FontChooser(FontSelectionModel model) {
        setSelectionModel(model);
        setLayout(new BorderLayout());
        addComponents();
        initPanes();
        previewPane.setPreviewFont(selectionModel.getSelectedFont());
    }

    @Override
    public Font getSelectedFont() {
        return selectionModel.getSelectedFont();
    }

    @Override
    public void setSelectedFont(Font font) {
        familyPane.removeListSelectionListener(familyPaneListener);
        stylePane.removeListSelectionListener(stylePaneListener);
        sizePane.removeListSelectionListener(sizePaneListener);

        selectionModel.setSelectedFont(font);

        initPanes();
    }

    public FontSelectionModel getSelectionModel() {
        return selectionModel;
    }

    @Initializer
    public void setSelectionModel(FontSelectionModel newModel) {
        var oldModel = selectionModel;
        selectionModel = newModel;
        selectionModel.addChangeListener(stylePane);
        firePropertyChange(SELECTION_MODEL_PROPERTY, oldModel, newModel);
    }

    public void addChangeListener(ChangeListener listener) {
        selectionModel.addChangeListener(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        selectionModel.removeChangeListener(listener);
    }

    private void initPanes() {
        //noinspection InstanceVariableUsedBeforeInitialized
        familyPane.setSelectedFamily(selectionModel.getSelectedFontFamily());
        familyPane.addListSelectionListener(familyPaneListener);

        stylePane.loadFamily(selectionModel.getSelectedFontFamily());
        stylePane.setSelectedStyle(selectionModel.getSelectedFont());
        stylePane.addListSelectionListener(stylePaneListener);

        sizePane.addListSelectionListener(sizePaneListener);
        sizePane.setSelectedSize(selectionModel.getSelectedFontSize());
    }

    private void addComponents() {
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_FONT_CHOOSER_GAP);

        setLayout(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, gap, 0);

        var familyColumn = createColumn();
        BaseDialog.addLabeledField(familyColumn, Strings.get(Strings.LABEL_FONT_FAMILY), familyPane, LabelPosition.TOP);
        gbc.weightx = 1.0;
        add(familyColumn, gbc);

        var styleColumn = createColumn();
        BaseDialog.addLabeledField(styleColumn, Strings.get(Strings.LABEL_FONT_STYLE), stylePane, LabelPosition.TOP);
        gbc.weightx = 0;
        gbc.insets = new Insets(0, gap, gap, 0);
        add(styleColumn, gbc);

        var sizeColumn = createColumn();
        BaseDialog.addLabeledField(sizeColumn, Strings.get(Strings.LABEL_FONT_SIZE), sizePane, LabelPosition.TOP);
        add(sizeColumn, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.insets = new Insets(0, 0, 0, 0);
        var previewColumn = createColumn();
        BaseDialog.addLabeledField(previewColumn, Strings.get(Strings.LABEL_FONT_PREVIEW), previewPane, LabelPosition.TOP);
        add(previewColumn, gbc);
    }

    private static JPanel createColumn() {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    @Override
    public StyleEntry getSelectedStyle() {
        return stylePane.getSelectedStyle();
    }

    @Override
    public float getSelectedSize() {
        return sizePane.getSelectedSize();
    }

    @Override
    public String getSelectedFamily() {
        return familyPane.getSelectedFamily();
    }

    @Override
    public void setPreviewFont(Font font) {
        previewPane.setPreviewFont(font);
    }
}
