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
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

/**
 * A non-editable combo box offering a fixed list of values plus a final {@code Other…} row, which
 * opens a one-field modal prompt for a value the list does not contain.
 *
 * <p>{@code Other…} is always the last row and is never a value: choosing it opens the prompt
 * instead of changing the selection, so no observer of this combo — listener, binding, or caller
 * of {@link #getValue()} — can ever see it. A value the prompt commits is added to the list
 * immediately above {@code Other…} and selected. So is a value written with
 * {@link #setSelectedItem(Object)} that the list does not already hold: the value a song carries is
 * shown even when it was typed before it was ever offered, rather than silently ignored the way a
 * non-editable {@link JComboBox} would ignore it.
 *
 * <p>Values entered through the prompt live as long as this combo does. Nothing persists them: a
 * later dialog starts from the configured list again, with the song's own value added back by the
 * write that populates the combo.
 *
 * <p>The optional empty row <strong>is the value</strong> {@code ""}, not a label with a mapping
 * behind it; a renderer paints it as {@code (none)}. {@link #getSelectedItem()} therefore answers
 * the real value with no translation layer. The row exists only when the caller asks for it with
 * {@link EmptyChoice#OFFERED}.
 *
 * <p>The class satisfies the preconditions of {@code Controls.item}: it is uneditable and always
 * holds a selection, so its value is its selection and a binding over it sees every change. An
 * editable combo satisfies neither — its value lives in its editor rather than in its selection,
 * and the two disagree until the look and feel chooses to commit.
 */
final class OtherValueComboBox extends JComboBox<String> {

    /**
     * Whether the list offers the empty value.
     *
     * <p>{@link #OFFERED} puts the value {@code ""} in the list as its first row, rendered as
     * {@code (none)}. A combo whose value may not be empty asks for {@link #WITHHELD}, and then no
     * row of the list is empty and {@link #getValue()} never answers {@code ""}.
     */
    enum EmptyChoice {
        OFFERED,
        WITHHELD
    }

    private static final String CONF_RESOURCE_PREFIX = "/conf/";

    private final OtherValuePrompt prompt;
    private final DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<>();

    /**
     * The one instance of the {@code Other…} text that {@link #setSelectedItem(Object)} compares
     * against by identity. Resolved once and never re-resolved, since a second equal instance would
     * not be the sentinel.
     */
    private final String otherItem = Strings.get(Strings.LABEL_OTHER);

    /**
     * Builds the combo: the empty row when asked for, then the values read from {@code fileNames}
     * in the order given, then {@code Other…}.
     *
     * @param prompt      the title and field label of the prompt that {@code Other…} opens, as
     *                    resolved text
     * @param emptyChoice whether the list offers the empty value as its first row
     * @param fileNames   names of the {@code /conf} resources to read values from, one value per
     *                    line; a name may be given for a resource that yields no values
     * @effects installs this combo's model and renderer, and reads each named resource
     * @log an unreadable resource is reported to the user as a damaged installation and leaves
     *      the combo with the values it read before the failure
     */
    OtherValueComboBox(OtherValuePrompt prompt, EmptyChoice emptyChoice, String... fileNames) {
        this.prompt = prompt;
        setModel(comboModel);

        if (emptyChoice == EmptyChoice.OFFERED) {
            comboModel.addElement("");
        }

        for (var fileName : fileNames) {
            addValuesFromFile(comboModel, fileName);
        }

        comboModel.addElement(otherItem);
        setRenderer(new EmptyValueRenderer());
    }

    /**
     * Selects {@code item}, adding it to the list first when the list does not hold it, or opens
     * the {@code Other…} prompt when {@code item} is the {@code Other…} sentinel.
     *
     * <p>Three promises, each of which is why this is written as an override rather than as a
     * listener:
     *
     * <ul>
     *   <li><strong>The sentinel is never observable.</strong> A listener reacting to
     *   {@code Other…} runs once the selection already is {@code Other…}, so every other listener
     *   would see the sentinel as the value and correctness would depend on registration order.
     *   Intercepting the write means no observer can see it at all.</li>
     *   <li><strong>The sentinel is recognised by identity.</strong> The popup and keyboard
     *   selection both route through {@link #setSelectedIndex(int)}, which passes the model's own
     *   instance, so identity is what the sentinel is. It is also what lets a user enter the
     *   literal text {@code Other…} as a value: it arrives as a different instance, and the two
     *   never collide.</li>
     *   <li><strong>A value the list does not hold is added, then selected.</strong> A
     *   non-editable {@link JComboBox} silently ignores an unknown value, which would leave a
     *   dialog showing a value other than the one the document carries, and commit that instead
     *   of the user's.</li>
     * </ul>
     *
     * <p>The prompt is opened through {@link SwingUtilities#invokeLater}, which is required rather
     * than stylistic: this runs inside the popup's {@code mouseReleased}, before the popup hides,
     * so a modal dialog shown here would re-enter the event loop with the popup still up.
     * Deferred, the popup closes first, and because the selection never changed no
     * {@code ActionEvent} fires — an observer sees one notification, carrying the committed value,
     * and never a transient one.
     *
     * @param item the value to select, or the {@code Other…} sentinel to open the prompt
     * @effects adds {@code item} to the list immediately above {@code Other…} when the list does
     *          not hold it; opens a modal prompt, later on the EDT, when {@code item} is the
     *          sentinel
     * @invariant the selection afterwards is never the {@code Other…} sentinel
     */
    @Override
    public void setSelectedItem(Object item) {
        // Identity, not equality: see the contract above. Do not "fix" this to equals().
        if (item == otherItem) {
            SwingUtilities.invokeLater(this::promptForOther);
            return;
        }

        if (item instanceof String text && comboModel.getIndexOf(text) < 0) {
            comboModel.insertElementAt(text, comboModel.getSize() - 1);
        }

        super.setSelectedItem(item);
    }

    /**
     * @return the selected value; {@code ""} exactly when the empty row is selected, and never the
     *         {@code Other…} sentinel, which {@link #setSelectedItem(Object)} never selects
     */
    String getValue() {
        return (String) getSelectedItem();
    }

    /**
     * Opens the modal prompt for a value the list does not hold. The call returns once the user has
     * answered: a committed value arrives as a write back to this combo, and a cancel writes
     * nothing, leaving the selection as it was.
     */
    private void promptForOther() {
        var mainFrame = MainFrame.getInstance();
        new OtherValueDialog(mainFrame, prompt, new OtherValueController(mainFrame, this).ops())
            .setVisible(true);
    }

    /**
     * Adds one value per line of the {@code /conf} resource named {@code fileName} to
     * {@code model}, in file order.
     *
     * @param model    the model to add the values to
     * @param fileName the resource name under {@code /conf}
     * @effects reports an unreadable resource to the user as a damaged installation, and returns
     *          with the values read before the failure already added
     */
    private static void addValuesFromFile(DefaultComboBoxModel<String> model, String fileName) {
        try {
            var inputStream =
                OtherValueComboBox.class.getResourceAsStream(CONF_RESOURCE_PREFIX + fileName);

            if (inputStream == null) {
                throw new FileNotFoundException("File not found: " + fileName);
            }

            try (
                var reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                )
            ) {
                var line = reader.readLine();

                while (line != null) {
                    model.addElement(line);
                    line = reader.readLine();
                }
            }
        } catch (IOException e) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_FILE_ERROR,
                Strings.ERROR_FILE_REINSTALL
            );
        }
    }

    /**
     * Paints the empty value as {@code (none)} and every other value verbatim.
     *
     * <p>Installed whatever the {@link EmptyChoice}: a {@link EmptyChoice#WITHHELD} combo holds no
     * empty value, so the branch is simply never taken, and a conditional install would be a
     * second thing to keep in step with the model. It is also what stops an empty row painting at
     * zero height.
     */
    private static final class EmptyValueRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            var text = "".equals(value) ? Strings.get(Strings.LABEL_NONE) : value;

            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }
}
