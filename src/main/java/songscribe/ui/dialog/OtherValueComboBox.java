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
import javax.swing.DefaultListSelectionModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.ComboPopup;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.ui.component.MainFrame;

/**
 * A non-editable combo box offering a fixed list of values plus a final {@code Other…} row, which
 * opens a one-field modal prompt for a value the list does not contain.
 *
 * <p><strong>The command rows are the ends of the list.</strong> The last row is always
 * {@code Other…}; the first row, at {@value #EMPTY_INDEX}, is the {@code (none)} row when the
 * caller asks for it with {@link EmptyChoice#OFFERED}. Position is what identifies them, so no
 * text comparison decides whether the user picked a command or a value.
 *
 * <p>Choosing {@code Other…} opens the prompt rather than selecting anything, so no listener,
 * binding, or reader of the selected item ever sees it as the value. The prompt refuses a value
 * any row already shows — see {@link #isValueInUse} — so the list never holds two rows the user
 * cannot tell apart.
 *
 * <p>{@link #setSelectedItem(Object)} adds a value the list does not hold, immediately above
 * {@code Other…}, and selects it — so the value a song carries is shown even when it was entered
 * before it was ever offered.
 *
 * <p>Values entered through the prompt live as long as this combo does. Nothing persists them: a
 * later dialog starts from the configured list again, with the song's own value added back by the
 * write that populates the combo.
 *
 * <p>The {@code (none)} row's value is {@code ""}, painted as {@code (none)}, so the selected
 * item is {@code ""} while it is chosen. A caller whose value may be empty only under
 * some condition bars the row with {@link #setEmptyChoiceSelectable} rather than rebuilding
 * the list, which keeps every position fixed.
 */
final class OtherValueComboBox extends JComboBox<String> {

    /**
     * Whether the list offers a row standing for no value.
     *
     * <p>{@link #OFFERED} puts the {@code (none)} row first, and the selected item is
     * {@code ""} while it is chosen. A combo whose value may not be empty asks for
     * {@link #WITHHELD}, and then the selected item is never {@code ""}.
     */
    enum EmptyChoice {
        OFFERED,
        WITHHELD
    }

    private static final String CONF_RESOURCE_PREFIX = "/conf/";

    /**
     * The position of the {@code (none)} row, which is always the first.
     *
     * <p>Package-private because the class contract promises the position, and a test that
     * checks the promise names this rather than repeating the number.
     */
    static final int EMPTY_INDEX = 0;

    private final OtherValuePrompt prompt;
    private final DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<>();
    private final boolean offersEmptyChoice;
    private boolean emptyChoiceSelectable = true;

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
        offersEmptyChoice = emptyChoice == EmptyChoice.OFFERED;
        setModel(comboModel);

        if (offersEmptyChoice) {
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
     * Installs the selection model that refuses a barred row, on the list the drop-down shows.
     *
     * <p>Swing builds that list when it installs the look and feel, and builds a new one every
     * time the look and feel changes, so the model is installed here rather than in the
     * constructor.
     *
     * <p>{@link JComboBox}'s own constructor calls this before any field of this class is
     * assigned. That early run is harmless: the model reads those fields when a row is picked,
     * never now.
     *
     * @effects replaces the drop-down list's selection model
     */
    @Override
    public void updateUI() {
        super.updateUI();

        var popup = (ComboPopup) getUI().getAccessibleChild(this, 0);
        popup.getList().setSelectionModel(new BarredSelectionModel());
    }

    /**
     * Selects {@code anObject}, or opens the prompt when {@code anObject} is the value the
     * {@code Other…} row holds.
     *
     * <p>Every route ends here, which is what lets position alone identify the command.
     * {@link #setSelectedIndex(int)} calls this. The drop-down's mouse handling calls that. The
     * Enter key calls this directly, with the value the drop-down highlights. So the command is
     * intercepted at the one point nothing reaches past.
     *
     * <p>The prompt opens through {@link SwingUtilities#invokeLater} because this can run inside
     * the drop-down's mouse handling, before the drop-down hides, and a modal dialog shown there
     * would re-enter the event loop with the drop-down still up. The call therefore returns
     * before the user has answered, with the selection unchanged.
     *
     * <p>This never refuses the {@code (none)} row. Barring governs the user's routes only, so a
     * caller may write a value the user may not pick.
     *
     * @param anObject the value to select, or {@code null} to clear the selection, which is what
     *                 {@code setSelectedIndex(-1)} asks for
     * @effects adds a row for {@code anObject}, immediately above {@code Other…}, when the list
     *          holds no row equal to it
     * @effects opens a modal prompt, later on the event dispatch thread, for the {@code Other…} row
     * @invariant the selection afterwards is never the {@code Other…} row
     */
    @Override
    public void setSelectedItem(@Nullable Object anObject) {
        if (anObject instanceof String text) {
            var index = comboModel.getIndexOf(text);

            if (index == otherIndex()) {
                SwingUtilities.invokeLater(this::promptForOther);
                return;
            }

            if (index < 0) {
                comboModel.insertElementAt(text, otherIndex());
            }
        }

        super.setSelectedItem(anObject);
    }

    /**
     * Selects the row at {@code index}, unless that row is the barred {@code (none)} row.
     *
     * <p>The drop-down list's selection model refuses a barred row on every route that goes
     * through the drop-down. This covers the one route that does not: an arrow key pressed while
     * the drop-down is closed works out a row number from the current selection and passes it
     * here, whatever the drop-down list would have allowed.
     *
     * @param index the row to select, or -1 to clear the selection
     * @invariant the selection afterwards is never the {@code (none)} row while that row is
     *            barred by {@link #setEmptyChoiceSelectable}
     */
    @Override
    public void setSelectedIndex(int index) {
        if (isEmptyChoiceBarred() && index == EMPTY_INDEX) {
            return;
        }

        super.setSelectedIndex(index);
    }

    /**
     * Narrows {@code JComboBox}'s {@code Object} to what every row of this list actually holds.
     *
     * @return the selected value, which is {@code ""} while the {@code (none)} row is chosen
     */
    @Override
    public String getSelectedItem() {
        return (String) super.getSelectedItem();
    }

    /**
     * Sets whether the user may pick the {@code (none)} row.
     *
     * <p>A barred row stays in the list and stays at its position, painted disabled. Only the
     * user's routes are refused — {@link #setSelectedItem(Object)} still selects it, so a
     * caller may write a value the user may not choose. That is what lets a caller populate this
     * combo without knowing what is barred.
     *
     * <p>Barring refuses a new selection and never changes the current one. A combo already
     * showing {@code (none)} keeps showing it, and its selected item stays {@code ""}. A caller
     * that needs the value legal must write a legal one.
     *
     * <p>A combo built with {@link EmptyChoice#WITHHELD} has no {@code (none)} row, and this
     * changes nothing for it.
     *
     * @param selectable whether the user may pick {@code (none)}
     */
    void setEmptyChoiceSelectable(boolean selectable) {
        emptyChoiceSelectable = selectable;
    }

    /**
     * @return {@code true} when this combo has a {@code (none)} row and the user may not pick it
     */
    private boolean isEmptyChoiceBarred() {
        return offersEmptyChoice && !emptyChoiceSelectable;
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

    /**
     * The selection model of the list the drop-down shows, which refuses the {@code (none)} row
     * while that row is barred.
     *
     * <p>Every route by which the user picks a row from the open drop-down sets this list's
     * selection first — a click, a drag, an arrow key. Refusing it here refuses all of them.
     * The Enter key commits the list's selected value, so a row this refuses can never be the
     * value it commits.
     */
    private final class BarredSelectionModel extends DefaultListSelectionModel {

        @Override
        public void setSelectionInterval(int index0, int index1) {
            if (isEmptyChoiceBarred() && index1 == EMPTY_INDEX) {
                return;
            }

            super.setSelectionInterval(index0, index1);
        }
    }

    /**
     * Paints the empty value as {@code (none)} and every other value verbatim, and paints the
     * {@code (none)} row disabled while it is barred.
     *
     * <p>The disabled paint is applied by row position rather than by value, so it reaches the
     * row in the open popup and never the closed combo's own text. The closed combo renders
     * with {@code index == -1}, which no row position equals.
     */
    private final class EmptyValueRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            var text = value instanceof String string ? displayText(string) : value;
            var barred = isEmptyChoiceBarred() && index == EMPTY_INDEX;

            var label = super.getListCellRendererComponent(
                list,
                text,
                index,
                isSelected && !barred,
                cellHasFocus
            );

            // Only ever turn a row off. The superclass takes the enabled state from the list
            // on every call, so turning one on here would light a row up in a disabled combo.
            if (barred) {
                label.setEnabled(false);
            }

            return label;
        }
    }
}
