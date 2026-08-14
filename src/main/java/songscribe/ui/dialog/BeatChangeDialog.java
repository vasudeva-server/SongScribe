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
import songscribe.ui.component.MainFrame;
import songscribe.message.mutation.ElementField;
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.BeatChange;
import songscribe.dom.Duration;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.DurationListCellRenderer;
import songscribe.dom.BeatChangeAttachment;

public class BeatChangeDialog extends AttachmentDialog<BeatChange> {

    final JComboBox<Duration> durationCombo =
        DurationListCellRenderer.createCombo(Duration.values());
    final JComboBox<Duration> beatCombo =
        DurationListCellRenderer.createCombo(Duration.values());

    public BeatChangeDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_BEAT_CHANGE_TITLE));

        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        var extraGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP);
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
    protected String opLabel(DialogOp op) {
        return Strings.get(switch (op) {
            case ADD -> Strings.ACTION_EDIT_OP_ADD_BEAT_CHANGE;
            case EDIT -> Strings.ACTION_EDIT_OP_CHANGE_BEAT_CHANGE;
            case REMOVE -> Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE;
        });
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

        // The change branch routes itself through the chokepoint from inside
        // BeatChangeAttachment; wrapping both branches gives the add branch — a raw
        // addAttachment — its routing, and collapses the pair into one edit. Any tuplets
        // the new beat forces out are reported by the chokepoint's own notification.
        Song.withBeatDefiningEditOn(element, () -> {
            if (existing != null) {
                existing.setBeatChange(new BeatChange(duration, beat));
            } else {
                element.addAttachment(
                    new BeatChangeAttachment(element, new BeatChange(duration, beat)));
            }
        });
    }

    @Override
    protected void clearChange(StaffElement element) {
        AttachmentRemoval.removeBeatChange(element);
    }
}
