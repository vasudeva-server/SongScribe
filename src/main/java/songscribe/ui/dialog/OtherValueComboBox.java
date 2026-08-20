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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.ui.component.MainFrame;

/**
 * A non-editable combo box offering a fixed list of values plus a final {@code Other…} row, which
 * opens a one-field modal prompt for a value the list does not contain.
 *
 * <p><strong>The command rows are the ends of the list.</strong> The last row is always
 * {@code Other…}; the first row is the {@code (none)} row when the caller asks for it with
 * {@link EmptyChoice#OFFERED}. Position is what identifies them, so no text comparison decides
 * whether the user picked a command or a value.
 *
 * <p>Choosing {@code Other…} opens the prompt rather than selecting anything, so no listener,
 * binding, or caller of {@link #getValue()} ever sees it as the value. The prompt refuses a value
 * any row already shows — see {@link #isValueInUse} — so the list never holds two rows the user
 * cannot tell apart.
 *
 * <p>{@link #setValue(String)} adds a value the list does not hold, immediately above
 * {@code Other…}, and selects it — so the value a song carries is shown even when it was entered
 * before it was ever offered.
 *
 * <p>Values entered through the prompt live as long as this combo does. Nothing persists them: a
 * later dialog starts from the configured list again, with the song's own value added back by the
 * write that populates the combo.
 *
 * <p>The {@code (none)} row's value is {@code ""}, painted as {@code (none)}, and
 * {@link #getValue()} answers {@code ""} for it.
 */
final class OtherValueComboBox extends JComboBox<String> {

    /**
     * Whether the list offers a row standing for no value.
     *
     * <p>{@link #OFFERED} puts the {@code (none)} row first, and {@link #getValue()} answers
     * {@code ""} when it is selected. A combo whose value may not be empty asks for
     * {@link #WITHHELD}, and then {@link #getValue()} never answers {@code ""}.
     */
    enum EmptyChoice {
        OFFERED,
        WITHHELD
    }

    private static final String CONF_RESOURCE_PREFIX = "/conf/";

    private final OtherValuePrompt prompt;
    private final EmptyChoice emptyChoice;
    private final DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<>();

    /**
     * Builds the combo: the {@code (none)} row when asked for, then the values read from
     * {@code fileNames} in the order given, then {@code Other…}.
     *
     * @param prompt      the title and field label of the prompt that {@code Other…} opens, as
     *                    resolved text
     * @param emptyChoice whether the list offers the {@code (none)} row first
     * @param fileNames   names of the {@code /conf} resources to read values from, one value per
     *                    line; blank lines are skipped
     * @effects installs this combo's model and renderer, and reads each named resource
     * @throws RuntimeException via {@link RuntimeError#missingResource}, exiting the application,
     *                          when a named resource is absent or unreadable, or when the rows
     *                          leave nothing selectable — each is a damaged installation
     */
    OtherValueComboBox(OtherValuePrompt prompt, EmptyChoice emptyChoice, List<String> fileNames) {
        this.prompt = prompt;
        this.emptyChoice = emptyChoice;
        setModel(comboModel);

        if (emptyChoice == EmptyChoice.OFFERED) {
            comboModel.addElement("");
        }

        for (var fileName : fileNames) {
            addValuesFromFile(comboModel, fileName);
        }

        // Nothing but the command row would mean no value can ever be chosen, and the model would
        // select the command row for want of anything else.
        if (comboModel.getSize() == 0) {
            throw RuntimeError.missingResource(
                "No combo values were read from: " + String.join(", ", fileNames));
        }

        comboModel.addElement(Strings.get(Strings.LABEL_OTHER));
        setRenderer(new EmptyValueRenderer());
    }

    /**
     * Selects {@code text}, adding it to the list immediately above {@code Other…} when the list
     * does not already hold it.
     *
     * <p>A programmatic write never opens the prompt, whatever the text.
     *
     * @param text the value to select; {@code ""} selects the {@code (none)} row, and does nothing
     *             in a {@link EmptyChoice#WITHHELD} combo, which has none
     * @effects adds a row for {@code text} when the list does not hold one
     */
    void setValue(String text) {
        if (text.isEmpty()) {
            if (emptyChoice == EmptyChoice.OFFERED) {
                super.setSelectedIndex(0);
            }

            return;
        }

        var index = comboModel.getIndexOf(text);

        if (index < 0) {
            index = otherIndex();
            comboModel.insertElementAt(text, index);
        }

        super.setSelectedIndex(index);
    }

    /**
     * Selects the row at {@code index}, or opens the prompt when {@code index} is the
     * {@code Other…} row.
     *
     * <p>Every route by which the user picks a row — the popup, the arrow keys, typeahead — arrives
     * here, which is what lets position alone identify the command. Intercepting the write is what
     * keeps the command out of every observer's view.
     *
     * <p>The prompt opens through {@link SwingUtilities#invokeLater} because this runs inside the
     * popup's mouse-release handling, before the popup hides, and a modal dialog shown there would
     * re-enter the event loop with the popup still up. The call therefore returns before the user
     * has answered, with the selection unchanged.
     *
     * @param index the row to select, or {@link #otherIndex()} to open the prompt
     * @effects opens a modal prompt, later on the event dispatch thread, for the {@code Other…} row
     * @invariant the selection afterwards is never the {@code Other…} row
     */
    @Override
    public void setSelectedIndex(int index) {
        if (index == otherIndex()) {
            SwingUtilities.invokeLater(this::promptForOther);
            return;
        }

        super.setSelectedIndex(index);
    }

    /**
     * @return the selected value, or {@code ""} when the {@code (none)} row is selected
     */
    String getValue() {
        return (String) getSelectedItem();
    }

    /**
     * Whether a row already shows {@code text}, comparing what the rows <strong>display</strong> so
     * that the {@code (none)} and {@code Other…} labels count as taken.
     *
     * @param text the value the prompt is offering to commit
     * @return {@code true} when some row already shows {@code text}, in which case committing it
     *         would add a second row the user could not tell from the first
     */
    boolean isValueInUse(String text) {
        for (var index = 0; index < comboModel.getSize(); index++) {
            if (text.equals(displayText(comboModel.getElementAt(index)))) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the index of the {@code Other…} row, which is always the last
     */
    private int otherIndex() {
        return comboModel.getSize() - 1;
    }

    /**
     * @param value a row's value
     * @return the text that row paints — the {@code (none)} label for the empty value, and the
     *         value itself otherwise
     */
    private static String displayText(String value) {
        return value.isEmpty() ? Strings.get(Strings.LABEL_NONE) : value;
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
     * Adds one value per non-blank line of the {@code /conf} resource named {@code fileName} to
     * {@code model}, in file order.
     *
     * @param model    the model to add the values to
     * @param fileName the resource name under {@code /conf}
     * @throws RuntimeException via {@link RuntimeError#missingResource}, exiting the application,
     *                          when the resource is absent or cannot be read
     */
    private static void addValuesFromFile(
        DefaultComboBoxModel<String> model,
        String fileName
    ) {
        var resource = CONF_RESOURCE_PREFIX + fileName;
        var inputStream = OtherValueComboBox.class.getResourceAsStream(resource);

        if (inputStream == null) {
            throw RuntimeError.missingResource("Combo values resource not found: " + resource);
        }

        try (
            var reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )
        ) {
            var line = reader.readLine();

            while (line != null) {
                if (!line.isBlank()) {
                    model.addElement(line);
                }

                line = reader.readLine();
            }
        } catch (IOException e) {
            throw RuntimeError.missingResource(
                "Could not read combo values from " + resource + ": " + e.getMessage());
        }
    }

    /** Paints the empty value as {@code (none)} and every other value verbatim. */
    private static final class EmptyValueRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            var text = value instanceof String string ? displayText(string) : value;

            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }
}
