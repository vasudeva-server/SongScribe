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
package songscribe.dom;

/**
 * Holds the tracked-removal bodies for the attachment kinds that have a dialog, so the dialog
 * Remove button and the Delete key run identical logic.
 *
 * <p>None of these methods opens a modification bracket — the caller owns the bracket, because
 * {@link Line#modifyElement} records unconditionally and only the caller knows whether a
 * refusal is still possible.
 */
public final class AttachmentRemoval {
    private AttachmentRemoval() {}

    /**
     * Removing a tempo change redefines the beat from that point on just as adding one does,
     * so it goes through the same chokepoint — which is also what warns the user when the
     * removal costs a tuplet, on the Remove button's path as on the commit path.
     */
    public static void removeTempoChange(StaffElement element) {
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

    /**
     * Removing a beat change redefines the beat from that point on just as adding one does,
     * so it goes through the same chokepoint — which is also what warns the user when the
     * removal costs a tuplet, on the Remove button's path as on the commit path.
     */
    public static void removeBeatChange(StaffElement element) {
        var attachment = element.findAttachment(BeatChangeAttachment.class);

        if (attachment == null) {
            return;
        }

        Song.withBeatDefiningEditOn(element, () -> element.removeAttachment(attachment));
    }

    public static void removeAnnotation(StaffElement element) {
        var existing = element.findAttachment(AnnotationAttachment.class);

        if (existing != null) {
            element.removeAttachment(existing);
        }
    }
}
