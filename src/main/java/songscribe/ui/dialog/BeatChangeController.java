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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.BeatUnit;
import songscribe.dom.Song;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Reads and writes the beat change on one element, for {@link BeatChangeDialog}.
 *
 * <p>A beat change redefines the beat from its element onwards; the tuplet beat rule revalidates
 * the tuplets after it when the modification bracket closes.
 *
 * <p>Nothing is refused, so {@link DialogController#validate} keeps its default: both of a
 * {@link BeatChange}'s durations come from {@link BeatUnit}, and there is no pair
 * of note values the dialog can offer that this could sensibly reject.
 */
public final class BeatChangeController extends AttachmentDialogController<BeatChange> {

    public BeatChangeController(AttachmentTarget target) {
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
    protected @Nullable BeatChange read() {
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
    protected void commit(BeatChange change) {
        var element = element();
        var existing = element.findAttachment(BeatChangeAttachment.class);

        modifyTarget(commitOp(), () -> {
            if (existing != null) {
                existing.setBeatChange(change);
            } else {
                element.addAttachment(new BeatChangeAttachment(change));
            }
        });
    }

    @Override
    protected void removeAttachment() {
        AttachmentRemoval.removeBeatChange(element());
    }
}
