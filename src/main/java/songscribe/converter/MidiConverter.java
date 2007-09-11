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

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.sound.midi.MidiSystem;

import songscribe.ui.Constants;
import songscribe.ui.component.Score;
import songscribe.util.FileUtils;
import songscribe.util.Log;

@SuppressWarnings("FieldMayBeStatic")
public class MidiConverter {

    @ArgumentDescribe("MIDI Instrument (0-127)")
    public final int instrument = 0;

    @ArgumentDescribe("Export with repeats")
    public final boolean withRepeat = false;

    @ArgumentDescribe("Tempo change (in percentage)")
    public final int tempoChange = 100;

    @FileArgument
    public final File[] files = new File[0];

    public static void main(String[] args) {
        Log.setNameWithoutExtension("midi-converter");
        var reader = new ArgumentReader<>(args, MidiConverter.class);
        reader.getObj().convert();
    }

    @SuppressWarnings("ConstantValue")
    public void convert() {
        if ((instrument < 0) || (instrument > 127)) {
            System.out.println("The instrument must be in range of 0-127");
            return;
        }

        if ((tempoChange <= 0) || (tempoChange > 200)) {
            System.out.println("The tempo change must be in range of 1-200");
            return;
        }

        var mainFrame = new ConverterMainFrame();
        var score = new Score(mainFrame);
        mainFrame.setScore(score);

        var props = new Properties(mainFrame.getProperties());
        props.setProperty(
            Constants.WITH_REPEAT_PROP,
            withRepeat ? Constants.TRUE_VALUE : Constants.FALSE_VALUE
        );
        props.setProperty(
            Constants.INSTRUMENT_PROP,
            Integer.toString(instrument)
        );
        props.setProperty(
            Constants.TEMPO_CHANGE_PROP,
            Integer.toString(tempoChange)
        );

        for (var file : files) {
            try {
                score.setComposition(null);
                score.openFile(mainFrame, file, false);
                score.musicDidChange(props);

                MidiSystem.write(
                    score.getSequence(),
                    1,
                    new File(
                        FileUtils.getPathWithoutExtension(file) + ".midi"
                    )
                );
            } catch (IOException e) {
                System.out.println("Could not convert " + file.getName());
            }
        }
    }
}
