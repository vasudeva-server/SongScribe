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
 * Tracked removals shared by the dialog Remove button and the Delete key.
 *
 * <p>The caller owns the modification bracket, because {@link Line#modifyElement} records
 * unconditionally and only the caller knows whether a refusal is still possible.
 */
public final class AttachmentRemoval {
    private AttachmentRemoval() {}

    public static void removeTempoChange(StaffElement element) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);

        if (attachment != null) {
            element.removeAttachment(attachment);
        }
    }

    public static void removeBeatChange(StaffElement element) {
        var attachment = element.findAttachment(BeatChangeAttachment.class);

        if (attachment != null) {
            element.removeAttachment(attachment);
        }
    }

    public static void removeAnnotation(StaffElement element) {
        var existing = element.findAttachment(AnnotationAttachment.class);

        if (existing != null) {
            element.removeAttachment(existing);
        }
    }
}
