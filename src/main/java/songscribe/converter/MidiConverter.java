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
package songscribe.converter;

import module java.desktop;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.SongScribe;
import songscribe.io.CompositionLoader;
import songscribe.ui.playback.PlaybackController;
import songscribe.file.FileUtils;

@SuppressWarnings("FieldMayBeStatic")
public class MidiConverter {

    private static final Logger LOG = LoggerFactory.getLogger(MidiConverter.class);

    @ArgumentDescribe("MIDI Instrument (0-127)")
    public final int instrument = 0;

    @ArgumentDescribe("Export with repeats")
    public final boolean withRepeat = false;

    @ArgumentDescribe("Tempo change (in percentage)")
    public final int tempoChange = 100;

    @FileArgument
    public final File[] files = new File[0];

    public static void main(String[] args) {
        SongScribe.configureLogging();
        var reader = new ArgumentReader<>(args, MidiConverter.class);
        var converter = reader.getObj();

        if (converter == null) {
            LOG.error("Failed to parse arguments");
            return;
        }

        converter.convert();
    }

    @SuppressWarnings("ConstantValue")
    public void convert() {
        if ((instrument < 0) || (instrument > 127)) {
            LOG.warn("The instrument must be in range of 0-127");
            return;
        }

        if ((tempoChange <= 0) || (tempoChange > 200)) {
            LOG.warn("The tempo change must be in range of 1-200");
            return;
        }

        PlaybackController.setPlayWithRepeats(withRepeat);
        PlaybackController.setInstrument(instrument);
        PlaybackController.setTempoChangePercent(tempoChange);

        for (var file : files) {
            try {
                var composition = CompositionLoader.load(file);
                var sequence = PlaybackController.buildSequence(composition);
                MidiSystem.write(
                    sequence,
                    1,
                    new File(
                        FileUtils.getPathWithoutExtension(file) + ".midi"
                    )
                );
                LOG.info("Converted {} to MIDI", file.getName());
            } catch (IOException | InvalidMidiDataException | SAXException e) {
                LOG.error("Could not convert {}", file.getName(), e);
            }
        }
    }
}
