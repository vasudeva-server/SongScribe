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
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Song;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Reads and writes the beat change on one element, for {@code BeatChangeDialog}.
 *
 * <p>A beat change redefines the beat from its element onwards, so every write here goes through
 * {@link Song#withBeatDefiningEditOn} — the chokepoint that reports any tuplets the new beat
 * forces out.
 *
 * <p>Nothing is refused, so {@link songscribe.ui.dialog.DialogBackEnd#validate} keeps its default:
 * both of a {@link BeatChange}'s durations come from {@link songscribe.dom.Duration}, and there is
 * no pair of note values the dialog can offer that this could sensibly reject.
 */
public final class BeatChangeBackEnd extends AttachmentBackEndBase<BeatChange> {

    public BeatChangeBackEnd(AttachmentTarget target) {
        super(target);
    }

    @Override
    protected ElementField elementField() {
        return ElementField.BEAT_CHANGE;
    }

    @Override
    protected String opLabel(AttachmentOp op) {
        return Strings.get(switch (op) {
            case ADD -> Strings.ACTION_EDIT_OP_ADD_BEAT_CHANGE;
            case CHANGE -> Strings.ACTION_EDIT_OP_CHANGE_BEAT_CHANGE;
            case REMOVE -> Strings.ACTION_EDIT_OP_REMOVE_BEAT_CHANGE;
        });
    }

    @Override
    public @Nullable BeatChange existingChange() {
        var attachment = element().findAttachment(BeatChangeAttachment.class);

        return attachment != null ? attachment.getBeatChange() : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Replaces the element's beat change when it already has one, and adds one when it does
     * not; either way the element ends up carrying exactly {@code change}, and either way exactly
     * one {@link SongDidChangeNotification} is posted.
     */
    @Override
    public void apply(BeatChange change) {
        var element = element();
        var existing = element.findAttachment(BeatChangeAttachment.class);

        // The change branch routes itself through the chokepoint from inside
        // BeatChangeAttachment; wrapping both branches gives the add branch — a raw
        // addAttachment — its routing, and collapses the pair into one edit. Any tuplets
        // the new beat forces out are reported by the chokepoint's own notification.
        modifyTarget(commitOp(), () -> Song.withBeatDefiningEditOn(element, () -> {
            if (existing != null) {
                existing.setBeatChange(change);
            } else {
                element.addAttachment(new BeatChangeAttachment(element, change));
            }
        }));
    }

    @Override
    protected void removeAttachment() {
        AttachmentRemoval.removeBeatChange(element());
    }
}
