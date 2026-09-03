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

import java.util.List;

import org.audiveris.proxymusic.ScorePartwise;
import org.xml.sax.SAXException;

import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.TupletLoadPass;
import songscribe.font.DocumentFonts;
import songscribe.io.SongLoadResult;
import songscribe.util.Utils;

/**
 * Turns an unmarshalled {@link ScorePartwise} graph into a {@link SongLoadResult.Success}.
 * The read-side orchestrator: it runs the format and provenance gates, then drives
 * {@link HeaderMapper} and {@link MeasureMapper} and the load-time passes.
 *
 * <p><b>No {@code File} parameter.</b> This class never learns where the bytes came
 * from, so compressed {@code .mxl} input can be added upstream without touching it, and
 * so it is callable from {@code MusicXmlReader.read(InputSource)} — the single parse path
 * the whole round-trip test suite goes through. A caller that needs a document name in a
 * message passes a {@code String}.
 */
final class SongMapper {

    private SongMapper() {
    }

    /**
     * Maps {@code document} onto a freshly parsed {@link Song}.
     *
     * @param document the unmarshalled score, with the root element name and {@code version}
     *                 attribute as the document actually wrote them
     * @return the mapped song, its document fonts, the load warnings, the
     *         accidentals-converted flag and the tuplet load-pass report
     * @throws SAXException if the document is not a supported MusicXML version
     *                      ({@link MusicXmlReader.UnsupportedFormatException}), was not
     *                      written by SongScribe
     *                      ({@link MusicXmlReader.ForeignSoftwareException}), carries an
     *                      unsupported SongScribe version
     *                      ({@link MusicXmlReader.UnsupportedVersionException}), or is
     *                      corrupt in a way a mapper rejects
     */
    static SongLoadResult.Success map(MusicXmlSerializer.ParsedDocument document) throws SAXException {
        checkFormat(document);

        var score = document.score();

        // Before any mapping: a document this program did not write is not an input it
        // supports, so there is nothing to gain by building a Song out of one first. Both
        // gates read what unmarshalling already produced, so a file too damaged to
        // unmarshal has failed before either runs, and a SongScribe file whose content is
        // corrupt still passes here and reports the corruption a mapper finds.
        HeaderMapper.checkProvenance(score);

        var song = Song.newParsingStub();
        var documentFonts = DocumentFonts.defaultFonts();
        boolean accidentalsConverted;

        // A load records no mutations, posts no notification and sets no modified flag.
        // begin/end rather than withoutMutationTracking(Runnable) because the mappers
        // throw a checked SAXException, which a Runnable cannot carry.
        song.beginSuspendMutationTracking();

        try {
            HeaderMapper.map(score, song, documentFonts);
            accidentalsConverted = MeasureMapper.map(score, song);

            // Restore the terminal invariant while tracking is still suspended so the
            // fix-ups are silent: the writer emits a line's closing barline only as a
            // real terminal, but a hand-authored or partial file may leave the last line
            // ending in a note or a non-terminal barline.
            song.installTerminalAfterParsing();

            // Likewise for the key invariant: the mappers set line keys under suspended tracking,
            // so the per-mutation propagation never ran for any of them, and a file written before
            // the no-restating rule existed can hold a key change that draws nothing.
            song.settleKeysAfterParsing();

            // Grace-host pairing is only settled once every <slide> has been resolved, so
            // the melisma repair runs over the finished song rather than per note. A file
            // written before the melisma was automatic may put the syllable on the host,
            // or leave the grace's syllable with no melisma at all.
            song.getLines().forEach(Line::repairGraceHostMelismas);
        } finally {
            song.endSuspendMutationTracking();
        }

        // Run here rather than in the UI: SongLoader's headless route runs it too, and a
        // song whose tuplets were settled in only one of the two routes would export a
        // different MIDI file than it displays.
        var tupletReport = TupletLoadPass.run(song);

        return new SongLoadResult.Success(
            song,
            documentFonts,
            List.of(),
            accidentalsConverted,
            tupletReport);
    }

    /**
     * Format gate: rejects a root element other than {@code <score-partwise>}, and a
     * {@code version} that is missing, unparseable, or older than
     * {@link MusicXmlTags#VERSION_VALUE}. {@code SongFileLoader.load} maps the exception
     * to {@code SongLoadResult.UnsupportedFileFormat}.
     *
     * <p>Both facts come off the raw stream rather than the graph — see
     * {@link MusicXmlSerializer.ParsedDocument}, which explains why neither is
     * recoverable from {@link ScorePartwise}.
     */
    private static void checkFormat(
        MusicXmlSerializer.ParsedDocument document
    ) throws MusicXmlReader.UnsupportedFormatException {
        var rootElement = document.rootElement();

        if (!MusicXmlTags.SCORE_PARTWISE.equals(rootElement)) {
            throw new MusicXmlReader.UnsupportedFormatException("root <" + rootElement + '>');
        }

        var version = document.version();

        if (version == null) {
            throw new MusicXmlReader.UnsupportedFormatException("missing version attribute");
        }

        int comparison;

        try {
            comparison = Utils.compareVersions(version, MusicXmlTags.VERSION_VALUE);
        } catch (NumberFormatException e) {
            throw new MusicXmlReader.UnsupportedFormatException("unparseable version '" + version + '\'');
        }

        if (comparison < 0) {
            throw new MusicXmlReader.UnsupportedFormatException(
                "unsupported MusicXML version '" + version +
                "'; requires " + MusicXmlTags.VERSION_VALUE + " or later"
            );
        }
    }

}
