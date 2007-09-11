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

package songscribe.ui.playback;

public final class MidiMetaMessageTypes {

    public static final int SEQUENCE_NUMBER = 0x00;
    public static final int TEXT_EVENT = 0x01;
    public static final int COPYRIGHT_NOTICE = 0x02;
    public static final int SEQUENCE_TRACK_NAME = 0x03;
    public static final int INSTRUMENT_NAME = 0x04;
    public static final int LYRIC = 0x05;
    public static final int MARKER = 0x06;
    public static final int CUE_POINT = 0x07;
    public static final int MIDI_CHANNEL_PREFIX = 0x20;
    public static final int END_OF_TRACK = 0x2F;
    public static final int SET_TEMPO = 0x51;
    public static final int SMPTE_OFFSET = 0x54;
    public static final int TIME_SIGNATURE = 0x58;
    public static final int KEY_SIGNATURE = 0x59;
    public static final int SEQUENCER_SPECIFIC_META_EVENT = 0x7F;

    private MidiMetaMessageTypes() {}
}
