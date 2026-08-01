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
package songscribe.io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import songscribe.dom.TupletLoadPass;

/**
 * Loads a {@link songscribe.dom.Song} from a file without requiring a {@code ScoreView}.
 * <p>
 * Use this for headless operations (MIDI export) that only need
 * the song data. For operations that also need rendering (image, PDF,
 * SVG export), use {@code ScoreView.openFile} instead.
 */
public final class SongLoader {

    // A .mssw file is user-supplied like any other, so it gets the same hardening
    // as the MusicXML path — see SafeXmlParser.
    private static final SAXParserFactory PARSER_FACTORY = SafeXmlParser.newHardenedFactory();

    private SongLoader() {}

    /**
     * Parses a SongScribe file and returns a {@link SongLoadResult}.
     *
     * @param file the .mssw file to load
     * @return a {@link SongLoadResult.Success} on success, or one of the
     *         {@link SongLoadResult.Failure} variants on error
     */
    public static SongLoadResult load(File file) {
        try {
            var parser = PARSER_FACTORY.newSAXParser();
            var reader = new SongIO.DocumentReader();
            parser.parse(file, reader);
            var invalidLyricsDate = reader.getInvalidLyricsDate();
            var warnings = new ArrayList<LoadWarning>();

            if (invalidLyricsDate != null) {
                warnings.add(new LoadWarning(LoadWarning.Type.INVALID_LYRICS_DATE, invalidLyricsDate));
            }

            var song = reader.getSong();

            // Both readers own the pass rather than the UI: the headless MIDI-export route
            // comes through here, and leaving it to ScoreView would have it play tuplets the
            // UI had dropped — two different MIDI files from one document, with no error on
            // either.
            var tupletReport = TupletLoadPass.run(song);

            return new SongLoadResult.Success(
                song,
                reader.getDocumentFonts(),
                warnings,
                reader.accidentalsConverted(),
                tupletReport);
        } catch (SongIO.NewerVersionException e) {
            return new SongLoadResult.NewerVersion(file, e);
        } catch (SAXException e) {
            return new SongLoadResult.ParseError(file, e);
        } catch (IOException e) {
            return new SongLoadResult.IoError(file, e);
        } catch (ParserConfigurationException e) {
            return new SongLoadResult.ParseError(file, new SAXException("Failed to create SAX parser", e));
        }
    }
}
