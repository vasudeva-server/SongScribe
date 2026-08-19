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
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.SongScribe;
import songscribe.dom.Song;
import songscribe.message.MessageBusScope;
import songscribe.message.MessageCenter;
import songscribe.ui.component.ScoreView;

public final class Converter {

    private static final Logger LOG = LoggerFactory.getLogger(Converter.class);

    private Converter() {}

    /**
     * Runs a headless conversion: parses {@code args} into an instance of {@code type} and, if
     * they parse, hands it to {@code convert}.
     * <p>
     * The conversion runs inside a {@link MessageBusScope} for its error handler: a throwing
     * {@code @Handler} is reported here rather than routed to the application bus's fatal
     * handler, which shows a dialog no headless process can display.
     * <p>
     * MBassador swallows what its error handler throws and aborts delivery to the remaining
     * subscribers of the affected post, so such an error would otherwise leave a silently
     * incomplete conversion reported as a success. They are collected and logged instead.
     *
     * @param <T>     the converter type, which {@link ArgumentReader} instantiates reflectively
     * @param args    the process arguments
     * @param type    the converter class to populate from {@code args}. Must have a no-argument
     *                constructor and annotated public fields for {@link ArgumentReader} to fill;
     *                anything else fails at runtime rather than at the call site
     * @param convert runs the conversion on the populated converter
     * @effects reconfigures logging for the whole process, and replaces the message bus every
     *          {@link MessageCenter} call reaches for the duration of the conversion
     * @log error when {@code args} do not parse, and when any {@code @Handler} threw during the
     *      conversion
     */
    public static <T> void run(String[] args, Class<T> type, Consumer<? super T> convert) {
        SongScribe.configureLogging();

        var converter = new ArgumentReader<>(args, type).getObj();

        if (converter == null) {
            LOG.error("Failed to parse arguments");
            return;
        }

        // Synchronized because a conversion may post from threads other than the one that
        // opened the scope, and MBassador reports the error on whichever thread published.
        var publicationErrors = Collections.synchronizedList(new ArrayList<String>());

        try (var scope = new MessageBusScope(error -> publicationErrors.add(MessageCenter.describe(error)))) {
            convert.accept(converter);
        }

        if (!publicationErrors.isEmpty()) {
            LOG.error(
                "Conversion finished with {} unhandled @Handler exception(s); each aborted "
                    + "delivery to the remaining subscribers of its post:\n\n{}",
                publicationErrors.size(),
                String.join("\n\n", publicationErrors)
            );
        }
    }

    /**
     * Loads a song from a file into the given score.
     *
     * @param file  the song file to read
     * @param score the score view to load it into, which becomes the song's view
     * @return the loaded song
     * @effects replaces whatever song {@code score} was showing
     */
    public static Song loadSong(File file, ScoreView score) {
        score.openFile(file, false);
        return score.getSong();
    }

    /**
     * Applies export exclusions by mutating the song directly.
     * <p>
     * TODO: Pass ExportOptions through the export pipeline instead of mutating
     * Song. This is a temporary measure until the rendering pipeline
     * supports ExportOptions natively (for PDF and SVG export). The two adjacent booleans
     * become fields of {@code ExportOptions} at that point; until then a call site can
     * transpose them without the compiler noticing.
     *
     * @param song             the song to strip, mutated in place
     * @param withoutLyrics    when true, clears the under-lyrics and the translated lyrics
     * @param withoutSongTitle when true, clears the title in the song's metadata
     * @effects mutates {@code song}
     */
    public static void applyExportExclusions(
        Song song,
        boolean withoutLyrics,
        boolean withoutSongTitle
    ) {
        if (withoutLyrics) {
            song.setUnderLyrics("");
            song.setTranslatedLyrics("");
        }

        if (withoutSongTitle) {
            song.setMetadata(song.getMetadata().withTitle(""));
        }
    }
}
