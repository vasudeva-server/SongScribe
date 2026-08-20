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

import songscribe.ui.binding.Controls;
import songscribe.ui.binding.Property;
import songscribe.ui.binding.Timing;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.NonBlankTextField;


/**
 * Asks for a value an {@link OtherValueComboBox} does not offer: one field under one prompt, with
 * OK and Cancel and no Remove. The wording of both the title and the prompt comes from the combo
 * that opened it, as an {@link OtherValuePrompt}.
 *
 * <p><strong>OK is unavailable while the field is blank</strong>, rather than blankness being
 * refused when OK is pressed. Stated as a validity condition, the user sees the commit become
 * unavailable at the moment they make it unavailable, and they see it come back the moment they
 * type; refused at OK, they would learn it only after asking for the thing they cannot have. The
 * condition reads the field's property, so it answers to a cut and a paste as readily as to
 * typing.
 *
 * <p>The field is a {@link NonBlankTextField}, so the same rule also holds once focus leaves it:
 * the condition speaks while the user types, the field's own guard restores what was there when
 * they move on. See the validity section of {@code .claude/guides/dialogs.md}.
 *
 * <p><strong>It opens while another modal dialog is already up</strong>, which is the ordinary case
 * rather than the exception: the combo it serves lives inside a dialog. That needs no
 * {@link DialogCategory} override — it is {@code OPERATIONAL}, the default.
 */
final class OtherValueDialog extends StandardDialog<OtherValue, String> {

    private static final int FIELD_COLUMNS = 24;

    private final NonBlankTextField field = new NonBlankTextField(FIELD_COLUMNS);
    private final Property<String> text = Controls.text(field, Timing.WHILE_TYPING);

    /**
     * @param mainFrame the application window, for parenting
     * @param prompt    the title and field label to show, already resolved to display text
     * @param ops       the operations this dialog's OK reads from and commits through
     */
    OtherValueDialog(MainFrame mainFrame, OtherValuePrompt prompt, DialogOps<OtherValue, ? super String> ops) {
        super(mainFrame, prompt.title(), ops);

        addLabeledField(contentPanel, prompt.label(), field, LabelPosition.TOP);
        requireValid(bindings().computed(() -> !text.get().isBlank()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Written through the property rather than through the field, so the value propagates as an
     * edit does rather than depending on which route the control reports.
     *
     * @param values the value to open on, which {@link OtherValueController} leaves empty
     */
    @Override
    protected void populate(OtherValue values) {
        text.set(values.text());
        field.rememberCurrentText();
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>The text is returned as it stands, unstripped.</strong> Stripping belongs to the
     * field's guard, which {@code StandardDialog.verifyFocusedField} runs before this dialog
     * commits, so the focused field has already been normalized by the time OK reads it. A strip
     * here would be a second copy of that rule, and the copy is the one that drifts.
     *
     * @return the text the field now shows
     */
    @Override
    protected String gather() {
        return text.get();
    }
}
