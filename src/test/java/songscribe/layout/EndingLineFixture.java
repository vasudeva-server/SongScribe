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
package songscribe.layout;

import songscribe.UnitTest;
import songscribe.dom.*;

/**
 * Shared test fixture for the canonical 7-element ending line layouts used across
 * {@code EndingInvalidationTest}, {@code LineMutationTest}, and {@code EndingConfirmsTest}.
 *
 * <p>Primary layout (split = {@code REPEAT_RIGHT}):
 * <pre>
 *  idx:  0             1        2        3             4        5        6
 *        SINGLE_BAR    CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BAR
 *        (anchor)                        (split)                          (end)
 * </pre>
 *
 * <p>Secondary layout (split = {@code REPEAT_LEFT_RIGHT}):
 * <pre>
 *  idx:  0            1        2        3                 4        5        6
 *        REPEAT_LEFT  CROTCHET CROTCHET REPEAT_LEFT_RIGHT CROTCHET CROTCHET REPEAT_RIGHT
 *        (anchor)                       (split)                              (end)
 * </pre>
 */
public record EndingLineFixture(
    Song song,
    Line line,
    StaffElement anchor,
    StaffElement note1,
    StaffElement note2,
    StaffElement split,
    StaffElement note4,
    StaffElement note5,
    StaffElement end,
    Ending ending
) {

    /** Primary layout with a freshly created song. */
    public static EndingLineFixture primary() {
        return primary(new Song());
    }

    /**
     * Primary layout populated into the first line of the given song.
     * Use this overload when the song must be created before a static mock is set up.
     */
    public static EndingLineFixture primary(Song song) {
        var line   = song.getLine(0);
        var anchor = new StaffElement(ElementType.SINGLE_BARLINE);
        var note1  = new StaffElement(ElementType.CROTCHET);
        var note2  = new StaffElement(ElementType.CROTCHET);
        var split  = new StaffElement(ElementType.REPEAT_RIGHT);
        var note4  = new StaffElement(ElementType.CROTCHET);
        var note5  = new StaffElement(ElementType.CROTCHET);
        var end    = new StaffElement(ElementType.SINGLE_BARLINE);
        var ending = new Ending(anchor, end);

        song.withoutMutationTracking(() -> {
            // A fresh Song has no line width until the app supplies one, and a zero width
            // reads to every fit gate as a line with no room at all.
            song.setLineWidthSs(UnitTest.UNCONSTRAINED_LINE_WIDTH_SS);
            line.addElement(anchor);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(split);
            line.addElement(note4);
            line.addElement(note5);
            line.addElement(end);
            line.addRangeElement(ending);
        });

        return new EndingLineFixture(song, line, anchor, note1, note2, split, note4, note5, end, ending);
    }

    /** Secondary layout with a freshly created song. */
    public static EndingLineFixture secondary() {
        return secondary(new Song());
    }

    /**
     * Secondary layout populated into the first line of the given song.
     * Use this overload when the song must be created before a static mock is set up.
     */
    public static EndingLineFixture secondary(Song song) {
        var line   = song.getLine(0);
        var anchor = new StaffElement(ElementType.REPEAT_LEFT);
        var note1  = new StaffElement(ElementType.CROTCHET);
        var note2  = new StaffElement(ElementType.CROTCHET);
        var split  = new StaffElement(ElementType.REPEAT_LEFT_RIGHT);
        var note4  = new StaffElement(ElementType.CROTCHET);
        var note5  = new StaffElement(ElementType.CROTCHET);
        var end    = new StaffElement(ElementType.REPEAT_RIGHT);
        var ending = new Ending(anchor, end);

        song.withoutMutationTracking(() -> {
            // A fresh Song has no line width until the app supplies one, and a zero width
            // reads to every fit gate as a line with no room at all.
            song.setLineWidthSs(UnitTest.UNCONSTRAINED_LINE_WIDTH_SS);
            line.addElement(anchor);
            line.addElement(note1);
            line.addElement(note2);
            line.addElement(split);
            line.addElement(note4);
            line.addElement(note5);
            line.addElement(end);
            line.addRangeElement(ending);
        });

        return new EndingLineFixture(song, line, anchor, note1, note2, split, note4, note5, end, ending);
    }
}
