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

package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
/**
 * Abstract base class for metronome-style markings (tempo and beat change).
 */
public abstract sealed class MetronomeAttachment extends Attachment
    permits TempoChangeAttachment, BeatChangeAttachment {

    // Design constant; mirrors FlatLaf.properties: SongScribe.score.tempo.note.scale
    public static final float NOTE_SCALE = 0.8f;

    private static final BBox QUARTER_NOTE_BBOX =
        SMuFLMetadata.bboxSs(SMuFLGlyph.MET_NOTE_QUARTER_UP);

    public static final double QUARTER_NOTE_HEIGHT_SS = QUARTER_NOTE_BBOX.heightSs() * NOTE_SCALE;
}
