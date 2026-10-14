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

import org.audiveris.proxymusic.Extend;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.StartStopContinue;
import org.audiveris.proxymusic.Syllabic;

import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.io.LyricNumbers;

/**
 * Builds the {@code <lyric>} children of a {@link Note} from a {@link StaffElement}'s per-script
 * {@link Lyric}s.
 *
 * <p>{@code <end-line/>} is deliberately not emitted here.
 */
final class LyricBuilder {

    private LyricBuilder() {}

    static void addLyrics(BuildContext context, StaffElement element, Note pmNote) {
        var lyrics = element.getLyrics();

        if (lyrics.isEmpty()) {
            return;
        }

        var factory = context.factory();

        for (var lyric : lyrics) {
            var extend = lyric.extend();
            var pmLyric = factory.createLyric();
            pmLyric.setNumber(Integer.toString(LyricNumbers.number(lyric.script())));

            if (lyric.isCarrier()) {
                // A carrier has no text of its own: the <extend> is the whole element.
                pmLyric.setExtend(buildExtend(factory, extend));
            } else {
                // ORDER IS LOAD-BEARING, AND NOTHING BUT THE SCHEMA CHECK ENFORCES IT.
                // getElisionAndSyllabicAndText() is a heterogeneous List<Object> whose
                // @XmlElements annotation admits Elision, Syllabic and TextElementData, and
                // JAXB marshals it in list order. Adding the text first produces
                // <text/><syllabic/>, which JAXB writes without complaint and our own
                // unmarshal reads straight back into the same object graph — so no
                // round-trip test can see it. Only MusicXmlSchemaValidator does:
                // "Element 'syllabic': This element is not expected."
                var choiceList = pmLyric.getElisionAndSyllabicAndText();
                choiceList.add(Syllabic.fromValue(SyllabicMapping.forSyllabic(lyric.syllabic())));

                var pmText = factory.createTextElementData();
                pmText.setValue(syllableText(lyric));
                choiceList.add(pmText);

                if (extend == Lyric.Extend.START) {
                    // A melisma START rides on the text-bearing syllable it starts from.
                    // <extend> is not a member of the choice list above: XJC collapsed the
                    // schema's two positionally equivalent extend occurrences into one typed
                    // property, so start, stop and continue all go through setExtend, and
                    // JAXB places the element after <text> on its own.
                    pmLyric.setExtend(buildExtend(factory, Lyric.Extend.START));
                }
            }

            pmNote.getLyric().add(pmLyric);
        }
    }

    private static Extend buildExtend(ObjectFactory factory, Lyric.Extend extend) {
        var pmExtend = factory.createExtend();
        pmExtend.setType(StartStopContinue.fromValue(SyllabicMapping.forExtend(extend)));

        return pmExtend;
    }

    private static String syllableText(Lyric lyric) {
        var text = lyric.text();

        return lyric.compound() ? text + Lyric.COMPOUND_WORD_MARKER : text;
    }
}
