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
import songscribe.message.mutation.ElementField;
import songscribe.model.BeatChange;
import songscribe.model.Duration;
import songscribe.model.StaffElement;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.DurationListCellRenderer;
import songscribe.ui.layout.BeatChangeAttachment;

public class BeatChangeDialog extends AttachmentDialog<BeatChange> {

    private final JComboBox<Duration> durationCombo =
        DurationListCellRenderer.createCombo(Duration.values());
    private final JComboBox<Duration> beatCombo =
        DurationListCellRenderer.createCombo(Duration.values());

    public BeatChangeDialog() {
        super(Strings.get(Strings.DIALOG_BEAT_CHANGE_TITLE));

        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        int extraGap = FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP);
        row.add(durationCombo);
        row.add(Box.createHorizontalStrut(extraGap));
        row.add(new JLabel("="));
        row.add(Box.createHorizontalStrut(extraGap));
        row.add(beatCombo);

        contentPanel.add(BorderLayout.CENTER, row);
    }

    @Override
    protected ElementField getElementField() {
        return ElementField.BEAT_CHANGE;
    }

    @Override
    protected @Nullable BeatChange getExistingChange(StaffElement element) {
        var attachment = element.findAttachment(BeatChangeAttachment.class);
        return attachment != null ? attachment.getBeatChange() : null;
    }

    @Override
    protected void populateControls(@Nullable BeatChange change) {
        if (change != null) {
            durationCombo.setSelectedItem(change.duration());
            beatCombo.setSelectedItem(change.beat());
        } else {
            durationCombo.setSelectedItem(Duration.CROTCHET_DOTTED);
            beatCombo.setSelectedItem(Duration.CROTCHET);
        }
    }

    @Override
    protected void applyChange(StaffElement element) {
        var duration = (Duration) durationCombo.getSelectedItem();
        var beat = (Duration) beatCombo.getSelectedItem();

        if (duration == null || beat == null) {
            return;
        }

        var existing = element.findAttachment(BeatChangeAttachment.class);

        if (existing != null) {
            existing.setBeatChange(new BeatChange(duration, beat));
        } else {
            element.addAttachment(new BeatChangeAttachment(element, new BeatChange(duration, beat)));
        }
    }

    @Override
    protected void clearChange(StaffElement element) {
        var attachment = element.findAttachment(BeatChangeAttachment.class);

        if (attachment != null) {
            element.removeAttachment(attachment);
        }
    }
}
