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

package songscribe.ui.component;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.LyricRun;
import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.message.mutation.ElementField;
import songscribe.util.LogUtils;

/**
 * The hyphen-chain and melisma-chain operations an open {@link LyricEditor} session performs on
 * one script of one {@link Line}. Reads or writes only the model, so it can be exercised
 * without a Swing component.
 *
 * <p><b>Bracketing:</b> only {@link #buildBackwardChain} opens its own modification bracket —
 * it is the whole of what the {@code _} keystroke writes. Every other mutating method here must
 * be called inside a bracket the editor has already opened, because the editor pairs them with
 * its own commit in a single undoable step.
 */
final class LyricChainEditor {

    private static final Logger LOG = LoggerFactory.getLogger(LyricChainEditor.class);

    private final Line line;

    private final Song.Script script;

    LyricChainEditor(Line line, Song.Script script) {
        this.line = line;
        this.script = script;
    }

    /**
     * Whether the element at {@code currentIndex} carries no syllable of its own but sits between
     * a continuing syllable and the one it lands on. A gap with no syllable after it is not one:
     * the chain dangles there, for the editor's dismiss adjustment to repair.
     */
    boolean isInsideHyphenChain(int currentIndex) {
        if (line.getElement(currentIndex).getLyricForScript(script) != null) {
            return false;
        }

        var backIndex = line.previousLyricBearingIndex(currentIndex, script);

        if (backIndex == null) {
            return false;
        }

        var backLyric = line.getElement(backIndex).getLyricForScript(script);

        return backLyric != null
            && Lyric.syllabicContinues(backLyric.syllabic())
            && line.hasFollowingTextBearingLyric(currentIndex, script);
    }

    void breakHyphenChain(int currentIndex) {
        var backIndex = line.previousLyricBearingIndex(currentIndex, script);

        if (backIndex == null) {
            trace("breakHyphenChain: no predecessor to end the word at");
            return;
        }

        var backLyric = line.getElement(backIndex).getLyricForScript(script);

        // The chain may have been broken from elsewhere while the editor was open.
        if (backLyric == null || !Lyric.syllabicContinues(backLyric.syllabic())) {
            trace("breakHyphenChain: predecessor {} no longer continues, nothing to break",
                backIndex);
            return;
        }

        trace("breakHyphenChain: ending the word at {}", backIndex);
        line.setSyllableBoundary(backIndex, script, true, false);
    }

    void breakMelismaChain(int currentIndex) {
        trace("breakMelismaChain: giving up the carrier at {}", currentIndex);

        var currentElement = line.getElement(currentIndex);
        line.modifyElement(currentIndex, ElementField.LYRIC, () ->
            currentElement.removeLyricForScript(script));
        breakChainAtCurrentElement(currentIndex);
    }

    void breakChainAtCurrentElement(int currentIndex) {
        terminatePrecedingContinueChain(currentIndex);
        clearForwardCarriers(currentIndex);

        // When the current element is a paired grace note, the carrier cleared just above may
        // have been its host's, which would leave the grace's own melisma without its STOP.
        // The sync converges whichever state the clearing landed in.
        if (line.isPairedGraceNote(currentIndex)) {
            line.syncGraceHostMelisma(currentIndex);
        }
    }

    private void terminatePrecedingContinueChain(int currentIndex) {
        var backIndex = line.previousLyricBearingIndex(currentIndex, script);

        if (backIndex == null) {
            return;
        }

        var backLyric = line.getElement(backIndex).getLyricForScript(script);

        if (backLyric == null) {
            return;
        }

        var backExtend = backLyric.extend();

        if (backExtend == Lyric.Extend.CONTINUE) {
            trace("terminatePrecedingChain: {} CONTINUE -> STOP", backIndex);
            rewriteLyricExtend(backIndex, backLyric, Lyric.Extend.STOP);
        } else if (backExtend == Lyric.Extend.START) {
            trace("terminatePrecedingChain: {} START -> NONE, the chain collapses", backIndex);
            rewriteLyricExtend(backIndex, backLyric, Lyric.Extend.NONE);
        }
    }

    void clearForwardCarriers(int currentIndex) {
        var effectiveCount = line.effectiveElementCount();

        for (var i = currentIndex + 1; i < effectiveCount; i++) {
            var forwardElement = line.getElement(i);
            var forwardLyric = forwardElement.getLyricForScript(script);

            if (forwardLyric == null) {
                continue;
            }

            var extend = forwardLyric.extend();

            if (extend != Lyric.Extend.CONTINUE && extend != Lyric.Extend.STOP) {
                trace("clearForwardCarriers: stopped at the syllable on {}", i);
                return;
            }

            trace("clearForwardCarriers: clearing the {} carrier on {}", extend, i);
            line.modifyElement(i, ElementField.LYRIC, () ->
                forwardElement.removeLyricForScript(script));

            if (extend == Lyric.Extend.STOP) {
                return;
            }
        }
    }

    /**
     * @param backIndex never negative
     */
    void buildBackwardChain(int currentIndex, int backIndex) {
        var backElement = line.getElement(backIndex);
        var backLyric = backElement.getLyricForScript(script);

        // Invariant: previousLyricBearingIndex only returns indices with non-null lyrics.
        if (backLyric == null) {
            throw RuntimeError.exit("Predecessor at " + backIndex + " lost script " + script + " lyric between scan and rewrite");
        }

        trace("extendChainBackward: chain root {} ({}), carrier at {}",
            backIndex, backLyric.extend(), currentIndex);

        var currentElement = line.getElement(currentIndex);

        line.getSong().withModification(() -> {
            var backExtend = backLyric.extend();

            if (backExtend == Lyric.Extend.STOP) {
                line.modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.putLyric(Lyric.carrier(script, Lyric.Extend.CONTINUE)));
            } else if (backExtend != Lyric.Extend.CONTINUE) {
                // The syllable it hyphenated to is now a carrier, so the word ends here — and a
                // syllable that opens a hyphen cannot also open a melisma, since the layout draws
                // the hyphen and drops the extender. Compound follows: it requires a continuing
                // syllabic.
                var backSyllabic = wordFinalSyllabic(backLyric.syllabic());
                line.modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.putLyric(
                        new Lyric(script, backLyric.text(), Lyric.Extend.START, backSyllabic, false)));
            }

            for (var i = backIndex + 1; i < currentIndex; i++) {
                var midElement = line.getElement(i);
                line.modifyElement(i, ElementField.LYRIC, () ->
                    midElement.putLyric(Lyric.carrier(script, Lyric.Extend.CONTINUE)));
            }

            line.modifyElement(currentIndex, ElementField.LYRIC, () ->
                currentElement.putLyric(Lyric.carrier(script, Lyric.Extend.STOP)));

            // Any syllable this element hyphenated to now follows a melisma, so it starts a word.
            line.adjustSuccessorAfterMelismaCarrier(currentIndex, script);
        });
    }

    /**
     * The start itself is the editor's commit, so it is not written here.
     */
    void markMelismaCarrierRun(int currentIndex, int nextIndex) {
        // Elements skipped between here and the carrier — such as the host of a paired
        // grace note, which the commit's melisma sync just marked STOP — now sit inside
        // the chain rather than ending it.
        for (var i = currentIndex + 1; i < nextIndex; i++) {
            var midElement = line.getElement(i);

            if (midElement.getLyricForScript(script) == null) {
                continue;
            }

            line.modifyElement(i, ElementField.LYRIC, () ->
                midElement.putLyric(Lyric.carrier(script, Lyric.Extend.CONTINUE)));
        }

        var nextElement = line.getElement(nextIndex);
        line.modifyElement(nextIndex, ElementField.LYRIC, () ->
            nextElement.putLyric(Lyric.carrier(script, Lyric.Extend.STOP)));

        // The commit's boundary fix stops at the carrier; the first syllable past the chain
        // still has to lose any word continuation that ran through it.
        line.adjustSuccessorAfterMelismaCarrier(nextIndex, script);
    }

    private void rewriteLyricExtend(int index, Lyric existing, Lyric.Extend newExtend) {
        var indexElement = line.getElement(index);
        line.modifyElement(index, ElementField.LYRIC, () ->
            indexElement.putLyric(existing.withExtend(newExtend)));
    }

    static boolean sustainsMelisma(Lyric.Extend extend) {
        return extend == Lyric.Extend.START || extend == Lyric.Extend.CONTINUE;
    }

    private static Lyric.Syllabic wordFinalSyllabic(Lyric.@Nullable Syllabic syllabic) {
        return switch (syllabic) {
            case BEGIN -> Lyric.Syllabic.SINGLE;
            case MIDDLE -> Lyric.Syllabic.END;
            case SINGLE, END -> syllabic;
            case null -> throw RuntimeError.exit("Text-bearing lyric is missing syllabic");
        };
    }

    /**
     * Call only with tracing on: it walks the whole line.
     */
    String lyricRowDescription() {
        var description = new StringBuilder();
        var elements = line.getElements();

        for (var i = 0; i < elements.size(); i++) {
            if (i > 0) {
                description.append(' ');
            }

            description.append(i).append('=');
            var lyric = elements.get(i).getLyricForScript(script);

            if (lyric == null) {
                description.append('-');
                continue;
            }

            if (lyric.syllabic() == null) {
                description.append('~');
            } else {
                description.append('"').append(lyric.text()).append("\"/").append(lyric.syllabic());
            }

            if (lyric.extend() != Lyric.Extend.NONE) {
                description.append('/').append(lyric.extend());
            }

            if (lyric.compound()) {
                description.append("/compound");
            }
        }

        return description.toString();
    }

    private static void trace(String format, @Nullable Object... args) {
        if (LogUtils.isTracingLyrics(LOG)) {
            LOG.debug(format, args);
        }
    }
}
