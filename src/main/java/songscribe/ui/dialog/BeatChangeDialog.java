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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.binding.Property;
import songscribe.dom.BeatChange;
import songscribe.dom.Duration;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.binding.Controls;
import songscribe.ui.component.DurationListCellRenderer;
import songscribe.ui.component.MainFrame;

/**
 * Two note-value combos reading {@code duration = beat}, for editing the beat change on an
 * element.
 *
 * <p><strong>Both combos always carry a selection.</strong> Each is built over the whole
 * {@link Duration} enum, so its model is never empty, and a non-empty combo selects its first
 * entry on construction. That is the precondition {@link Controls#item} states, and it is what
 * lets {@link #gather()} produce a {@link BeatChange} unconditionally instead of quietly
 * declining to commit.
 */
public class BeatChangeDialog extends AttachmentDialog<BeatChange> {

    /**
     * What the controls start at when the element carries no beat change: a dotted crotchet
     * taking the beat of a crotchet, the compound-time case the dialog is opened for most often.
     */
    static final BeatChange DEFAULT_BEAT_CHANGE =
        new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET);

    private final Property<Duration> duration;
    private final Property<Duration> beat;

    public BeatChangeDialog(MainFrame mainFrame, DialogOps<? extends @Nullable BeatChange, BeatChange> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_BEAT_CHANGE_TITLE), ops);

        var durationCombo = DurationListCellRenderer.createCombo(Duration.values());
        var beatCombo = DurationListCellRenderer.createCombo(Duration.values());
        duration = Controls.item(durationCombo);
        beat = Controls.item(beatCombo);

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

    /**
     * {@inheritDoc}
     *
     * <p>The controls start at {@link #DEFAULT_BEAT_CHANGE} when there is nothing to show. Whatever
     * is put in comes back out: {@link #gather()} called straight afterwards, with nothing
     * else touched, answers the same {@link BeatChange}.
     */
    @Override
    protected void populateControls(@Nullable BeatChange existingChange) {
        var change = existingChange != null ? existingChange : DEFAULT_BEAT_CHANGE;

        duration.set(change.duration());
        beat.set(change.beat());
    }

    @Override
    protected BeatChange gather() {
        return new BeatChange(duration.get(), beat.get());
    }
}
