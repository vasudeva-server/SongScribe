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
package songscribe.ui.dialog.backend;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Reads and writes the tempo change on one element, for {@code TempoChangeDialog}.
 *
 * <p>A tempo change carries the beat, so it redefines the beat from its element onwards exactly
 * as a beat change does; every write here goes through {@link Song#withBeatDefiningEditOn} for
 * the same reason.
 *
 * <p>The value crossing the seam is a {@link Tempo}, not the {@link TempoChangeAttachment} that
 * holds it: the attachment is a node in the document graph and the dialog has no business
 * holding one.
 *
 * <p>Nothing is refused, so {@link songscribe.ui.dialog.DialogBackEnd#validate} keeps its default:
 * the beats-per-minute comes from a bounded spinner and the note value from a fixed list, so every
 * {@link Tempo} the dialog can assemble is one the score can hold.
 */
public final class TempoChangeBackEnd extends AttachmentBackEndBase<Tempo> {

    public TempoChangeBackEnd(AttachmentTarget target) {
        super(target);
    }

    @Override
    protected ElementField elementField() {
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
    public @Nullable Tempo existingChange() {
        var attachment = element().findAttachment(TempoChangeAttachment.class);

        return attachment != null ? attachment.getTempo() : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Replaces the element's tempo change when it already has one, and adds one when it does
     * not; either way the element ends up carrying exactly {@code change}, and either way exactly
     * one {@link SongDidChangeNotification} is posted.
     */
    @Override
    public void apply(Tempo change) {
        var element = element();
        var existing = element.findAttachment(TempoChangeAttachment.class);

        // A tempo change carries the beat, so both branches redefine it from here on. The
        // change branch routes itself from inside TempoChangeAttachment; wrapping both gives
        // the add branch — a raw addAttachment — its routing, and collapses the pair into
        // one edit. Any tuplets the new beat forces out are reported by the chokepoint's
        // own notification.
        modifyTarget(commitOp(), () -> Song.withBeatDefiningEditOn(element, () -> {
            if (existing != null) {
                existing.setTempo(change);
            } else {
                element.addAttachment(new TempoChangeAttachment(element, change));
            }
        }));
    }

    @Override
    protected void removeAttachment() {
        AttachmentRemoval.removeTempoChange(element());
    }
}
