/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component.score;

import java.awt.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout2.ScaleContext;

/**
 * Static hit-testing utilities for note heads in a {@link LineComponent}.
 * <p>
 * Extracted from {@link SelectionHandler} so that {@link NoteDragHandler} can
 * share the same logic without duplicating it.
 */
class NoteHitTest {

    private NoteHitTest() {}

    /**
     * Iterates all notes in the line and returns the index of the first note
     * whose hit rectangle contains {@code point}, or -1 if none.
     */
    static int hitTestNote(@NotNull LineComponent lc, @NotNull Point point) {
        var line = lc.getLine();
        var helper = new Rectangle();

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);
            buildNoteHitRect(lc, note, noteIndex, helper);

            if (helper.contains(point)) {
                return noteIndex;
            }
        }

        return -1;
    }

    /**
     * Builds the pixel-coordinate hit rectangle for the given note into {@code out}.
     */
    static void buildNoteHitRect(
        @NotNull LineComponent lc,
        @NotNull Note note,
        int noteIndex,
        @NotNull Rectangle out
    ) {
        var line = lc.getLine();

        if (line.getBeamings().findInterval(noteIndex) != null) {
            out.setBounds(
                note.isUpper() ? NoteType.CROTCHET.getRealUpNoteRect() : NoteType.CROTCHET.getRealDownNoteRect()
            );
        } else {
            out.setBounds(
                note.isUpper() ? note.getRealUpNoteRect() : note.getRealDownNoteRect()
            );
        }

        // Get note X from LayoutResult (staff-space) and convert to pixels, so the
        // hit rect is in pixel coordinates consistent with the mouse-event point.
        var layoutResult = lc.getLayoutResult();
        var noteXss = layoutResult != null ? layoutResult.getNoteXSs(note) : 0.0;
        var noteXpx = (int) Math.round(ScaleContext.getInstance().toPixels(noteXss));
        var noteY = lc.staffPositionToYPx(note.getStaffPosition());
        out.translate(noteXpx, noteY - Note.HOT_SPOT.y);
    }
}
