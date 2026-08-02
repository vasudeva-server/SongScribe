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
import songscribe.dom.Duration;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.ui.component.MainFrame;

public class TempoChangeDialog extends AttachmentDialog<TempoChangeAttachment> {

    final TempoSection tempoSection = new TempoSection(
        Duration.values(),
        Strings.get(Strings.DIALOG_TEMPO_CHANGE_SHOW_ONLY_DESCRIPTION),
        "tempochanges", "tempos"
    );

    public TempoChangeDialog(MainFrame mainFrame) {
        super(mainFrame, Strings.get(Strings.DIALOG_TEMPO_CHANGE_TITLE));
        contentPanel.add(BorderLayout.CENTER, tempoSection);
    }

    public static void showForElement(MainFrame mainFrame, StaffElement element, Line line) {
        var dialog = new TempoChangeDialog(mainFrame);
        dialog.selectedElement = element;
        dialog.selectedLine = line;
        dialog.setVisible(true);
    }

    @Override
    protected ElementField getElementField() {
        return ElementField.TEMPO_CHANGE;
    }

    @Override
    protected String opLabel(AttachmentOp op) {
        return Strings.get(switch (op) {
            case ADD -> Strings.ACTION_EDIT_OP_ADD_TEMPO_CHANGE;
            case CHANGE -> Strings.ACTION_EDIT_OP_CHANGE_TEMPO_CHANGE;
            case REMOVE -> Strings.ACTION_EDIT_OP_REMOVE_TEMPO_CHANGE;
        });
    }

    @Override
    protected @Nullable TempoChangeAttachment getExistingChange(StaffElement element) {
        return element.findAttachment(TempoChangeAttachment.class);
    }

    @Override
    protected void populateControls(@Nullable TempoChangeAttachment change) {
        tempoSection.setTempo(
            change != null
                ? change.getTempo()
                : new Tempo()
        );
    }

    @Override
    protected void applyChange(StaffElement element) {
        var tempo = new Tempo(
            tempoSection.getVisibleTempo(),
            tempoSection.getTempoType(),
            tempoSection.getTempoDescription(),
            !tempoSection.isShowOnlyDescription()
        );
        var existing = element.findAttachment(TempoChangeAttachment.class);

        // A tempo change carries the beat, so both branches redefine it from here on. The
        // change branch routes itself from inside TempoChangeAttachment; wrapping both gives
        // the add branch — a raw addAttachment — its routing, and collapses the pair into
        // one edit. Any tuplets the new beat forces out are reported by the chokepoint's
        // own notification.
        Song.withBeatDefiningEditOn(element, () -> {
            if (existing != null) {
                existing.setTempo(tempo);
            } else {
                element.addAttachment(new TempoChangeAttachment(element, tempo));
            }
        });
    }

    /**
     * Removing a tempo change redefines the beat from that point on just as adding one does,
     * so it goes through the same chokepoint — which is also what warns the user when the
     * removal costs a tuplet, on the Remove button's path as on the commit path.
     */
    @Override
    protected void clearChange(StaffElement element) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);

        Song.withBeatDefiningEditOn(element, () -> {
            if (attachment != null) {
                element.removeAttachment(attachment);
            }

            var line = element.getParentLine();

            // An element in no line cannot orphan the song-level tempo.
            if (line == null) {
                return;
            }

            line.getSong().clearTempoIfOrphaned(element);
        });
    }
}
