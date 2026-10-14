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
package songscribe.io.musicxml;

import org.audiveris.proxymusic.ScorePartwise;
import org.xml.sax.SAXException;

import songscribe.dom.ParsedSong;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.util.Utils;

/**
 * Turns an unmarshalled {@link ScorePartwise} graph into a {@link ParsedSong}.
 *
 * <p>Takes no {@code File}: it never learns where the bytes came from. A caller that needs a
 * document name in a message passes a {@code String}.
 */
final class SongMapper {

    private SongMapper() {
    }

    static ParsedSong map(
        MusicXmlSerializer.ParsedDocument document, DocumentFonts documentFonts
    ) throws SAXException {
        checkFormat(document);

        var score = document.score();

        // Before any mapping, so no Song is built from a document this program did not write.
        // Both gates read what unmarshalling already produced: a file too damaged to unmarshal
        // has failed before either runs, and a SongScribe file whose content is corrupt still
        // passes here and reports the corruption a mapper finds.
        HeaderMapper.checkProvenance(score);

        var song = Song.newParsingStub();
        ParsedSong parsed;

        // A load records no mutations, posts no notification and sets no modified flag.
        // begin/end rather than withoutMutationTracking(Runnable) because the mappers
        // throw a checked SAXException, which a Runnable cannot carry.
        song.beginSuspendMutationTracking();

        try {
            HeaderMapper.map(score, song, documentFonts);
            parsed = MeasureMapper.map(score, song, SupportedFeature.declaredIn(score));

            // Restore the terminal invariant while tracking is still suspended so the fix-ups
            // are silent: a damaged or partial file may leave the last line ending in a note or
            // a non-terminal barline.
            song.installTerminalAfterParsing();

            // Likewise for the key invariant: the mappers set line keys under suspended tracking,
            // so the per-mutation propagation never ran for any of them, and the file can hold a
            // key change that draws nothing.
            song.keyInheritance().settleAfterParsing();

            // Grace-host pairing is only settled once every <slide> has been resolved, so
            // the melisma repair runs over the finished song rather than per note.
            song.repairGraceHostMelismasAfterParsing();
        } finally {
            song.endSuspendMutationTracking();
        }

        return parsed;
    }

    private static void checkFormat(
        MusicXmlSerializer.ParsedDocument document
    ) throws MusicXmlReader.InvalidFormatException {
        var rootElement = document.rootElement();

        if (!MusicXmlTags.SCORE_PARTWISE.equals(rootElement)) {
            throw new MusicXmlReader.InvalidFormatException("root <" + rootElement + '>');
        }

        var version = document.version();

        if (version == null) {
            throw new MusicXmlReader.InvalidFormatException("missing version attribute");
        }

        int comparison;

        try {
            comparison = Utils.compareVersions(version, MusicXmlTags.VERSION_VALUE);
        } catch (NumberFormatException e) {
            throw new MusicXmlReader.InvalidFormatException("unparseable version '" + version + '\'');
        }

        if (comparison < 0) {
            throw new MusicXmlReader.InvalidFormatException(
                "unsupported MusicXML version '" + version +
                "'; requires " + MusicXmlTags.VERSION_VALUE + " or later"
            );
        }
    }

}
