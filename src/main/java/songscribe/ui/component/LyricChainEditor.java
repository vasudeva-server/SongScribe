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
import songscribe.error.RuntimeError;
import songscribe.message.mutation.ElementField;
import songscribe.util.LogUtils;

/**
 * The hyphen-chain and melisma-chain algebra an open {@link LyricEditor} session performs on
 * one verse of one {@link Line}. Every operation here is expressed in element indices and
 * reads or writes only the model, so it can be exercised without a Swing component.
 *
 * <p>This is a level above the repairs on {@link LyricRun}, not a replacement for them: those
 * answer "an element changed, make the chains around it well-formed again," while these are
 * the specific chain rewrites the editor's keystrokes ask for — clearing a placeholder ends
 * the word or gives up the carrier, {@code _} builds a chain backward or forward. Each one is
 * written in terms of the {@code LyricRun} repairs and {@link LyricRun#modifyElement}, so every
 * write lands inside the mutation bracket and undo sees it.
 *
 * <p><b>Bracketing:</b> only {@link #buildBackwardChain} opens its own modification bracket —
 * it is the whole of what the {@code _} keystroke writes. Every other mutating method here must
 * be called inside a bracket the editor has already opened, because the editor pairs them with
 * its own commit in a single undoable step.
 */
final class LyricChainEditor {

    private static final Logger LOG = LoggerFactory.getLogger(LyricChainEditor.class);

    private final Line line;

    /** The verse the owning editor session captured when it opened. */
    private final int verse;

    LyricChainEditor(Line line, int verse) {
        this.line = line;
        this.verse = verse;
    }

    /**
     * Whether the element at {@code currentIndex} carries no syllable of its own but sits between
     * a syllable that continues into the next word part and the syllable that part lands on — the
     * gap a word's hyphen is drawn across. Opening such an element prefills the hyphen placeholder.
     * A gap with no syllable after it is not one: the chain dangles there, which is the editor's
     * dismiss adjustment to repair.
     */
    boolean isInsideHyphenChain(int currentIndex) {
        if (line.getElement(currentIndex).getLyricForVerse(verse) != null) {
            return false;
        }

        var backIndex = line.previousLyricBearingIndex(currentIndex, verse);

        if (backIndex < 0) {
            return false;
        }

        var backLyric = line.getElement(backIndex).getLyricForVerse(verse);

        return backLyric != null
            && Lyric.syllabicContinues(backLyric.syllabic())
            && line.hasFollowingTextBearingLyric(currentIndex, verse);
    }

    /**
     * Ends the hyphenated word that ran through the element at {@code currentIndex}, called when
     * the user clears the placeholder standing for its hyphen. Making the predecessor word-final
     * also drops the continuation from the syllable that followed, via
     * {@link LyricRun#setSyllableBoundary}.
     *
     * <p>Must be called inside an open modification bracket.
     */
    void breakHyphenChain(int currentIndex) {
        var backIndex = line.previousLyricBearingIndex(currentIndex, verse);

        if (backIndex < 0) {
            trace("breakHyphenChain: no predecessor to end the word at");
            return;
        }

        var backLyric = line.getElement(backIndex).getLyricForVerse(verse);

        // The chain may have been broken from elsewhere while the editor was open.
        if (backLyric == null || !Lyric.syllabicContinues(backLyric.syllabic())) {
            trace("breakHyphenChain: predecessor {} no longer continues, nothing to break",
                backIndex);
            return;
        }

        trace("breakHyphenChain: ending the word at {}", backIndex);
        line.setSyllableBoundary(backIndex, verse, true, false);
    }

    /**
     * Ends the melisma that ran through the element at {@code currentIndex}, called when the user
     * clears the placeholder standing for its extender. The element gives up its carrier lyric,
     * then {@link #breakChainAtCurrentElement} closes the chain behind it and clears the carriers
     * ahead of it.
     *
     * <p>Must be called inside an open modification bracket.
     */
    void breakMelismaChain(int currentIndex) {
        trace("breakMelismaChain: giving up the carrier at {}", currentIndex);

        var currentElement = line.getElement(currentIndex);
        line.modifyElement(currentIndex, ElementField.LYRIC, () ->
            currentElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.NONE));
        breakChainAtCurrentElement(currentIndex);
    }

    /**
     * Severs the melisma chain running through {@code currentIndex} in both directions: the
     * predecessor stops handing one on, and the carriers ahead are cleared.
     *
     * <p>Must be called inside an open modification bracket.
     */
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
        var backIndex = line.previousLyricBearingIndex(currentIndex, verse);

        if (backIndex < 0) {
            return;
        }

        var backLyric = line.getElement(backIndex).getLyricForVerse(verse);

        if (backLyric == null) {
            return;
        }

        var backExtend = backLyric.extend();

        if (backExtend == Lyric.Extend.CONTINUE) {
            trace("terminatePrecedingChain: {} CONTINUE -> STOP", backIndex);
            rewriteLyricExtend(backIndex, backLyric, Lyric.Extend.STOP);
        } else if (backExtend == Lyric.Extend.START) {
            // START directly precedes the break point — the whole chain collapses.
            trace("terminatePrecedingChain: {} START -> NONE, the chain collapses", backIndex);
            rewriteLyricExtend(backIndex, backLyric, Lyric.Extend.NONE);
        }
    }

    /**
     * Clears every melisma carrier after {@code currentIndex}, stopping at the first
     * text-bearing syllable or once the chain's {@code STOP} has been cleared.
     *
     * <p>Must be called inside an open modification bracket.
     */
    void clearForwardCarriers(int currentIndex) {
        var effectiveCount = line.effectiveElementCount();

        for (var i = currentIndex + 1; i < effectiveCount; i++) {
            var forwardElement = line.getElement(i);
            var forwardLyric = forwardElement.getLyricForVerse(verse);

            if (forwardLyric == null) {
                continue;
            }

            var extend = forwardLyric.extend();

            if (extend != Lyric.Extend.CONTINUE && extend != Lyric.Extend.STOP) {
                // Text-bearing (extend NONE or START): halt without modification.
                trace("clearForwardCarriers: stopped at the syllable on {}", i);
                return;
            }

            trace("clearForwardCarriers: clearing the {} carrier on {}", extend, i);
            line.modifyElement(i, ElementField.LYRIC, () ->
                forwardElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.NONE));

            if (extend == Lyric.Extend.STOP) {
                return;
            }
        }
    }

    /**
     * Retroactively builds a {@code CONTINUE} chain from the lyric-bearing element at
     * {@code backIndex} through {@code currentIndex}, which becomes the chain's {@code STOP}
     * carrier. This is the whole of what the empty-editor {@code _} keystroke writes, so it
     * opens its own modification bracket.
     *
     * @param currentIndex the element the editor is open on, which becomes the chain's carrier
     * @param backIndex    the lyric-bearing element the chain runs back to, never negative
     */
    void buildBackwardChain(int currentIndex, int backIndex) {
        var backElement = line.getElement(backIndex);
        var backLyric = backElement.getLyricForVerse(verse);

        // Invariant: previousLyricBearingIndex only returns indices with non-null lyrics.
        if (backLyric == null) {
            throw RuntimeError.exit("Predecessor at " + backIndex + " lost verse " + verse + " lyric between scan and rewrite");
        }

        trace("extendChainBackward: chain root {} ({}), carrier at {}",
            backIndex, backLyric.extend(), currentIndex);

        var currentElement = line.getElement(currentIndex);

        line.withModification(() -> {
            var backExtend = backLyric.extend();

            if (backExtend == Lyric.Extend.STOP) {
                // STOP carrier: flip back to CONTINUE so the new chain extends through it.
                line.modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.CONTINUE));
            } else if (backExtend != Lyric.Extend.CONTINUE) {
                // Text-bearing (NONE or START): rewrite extend to START, preserving the text.
                // The syllable it hyphenated to is now a carrier, so the word ends here — and a
                // syllable that opens a hyphen cannot also open a melisma, since the layout draws
                // the hyphen and drops the extender. Compound follows: it requires a continuing
                // syllabic.
                var backSyllabic = wordFinalSyllabic(backLyric.syllabic());
                line.modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(verse,
                        backSyllabic, false,
                        backLyric.text(), Lyric.Extend.START));
            }
            // CONTINUE carrier: leave unchanged — the chain already extends through it.

            for (var i = backIndex + 1; i < currentIndex; i++) {
                var midElement = line.getElement(i);
                line.modifyElement(i, ElementField.LYRIC, () ->
                    midElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.CONTINUE));
            }

            line.modifyElement(currentIndex, ElementField.LYRIC, () ->
                currentElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.STOP));

            // Any syllable this element hyphenated to now follows a melisma, so it starts a word.
            line.adjustSuccessorAfterMelismaCarrier(currentIndex, verse);
        });
    }

    /**
     * Runs the chain forward from a melisma-starting syllable at {@code currentIndex} to its
     * {@code STOP} carrier at {@code nextIndex}, then repairs the syllable past the chain. The
     * start itself is the editor's commit, so it is not written here.
     *
     * <p>Must be called inside an open modification bracket — the same one the commit runs in.
     */
    void markMelismaCarrierRun(int currentIndex, int nextIndex) {
        // Elements skipped between here and the carrier — such as the host of a paired
        // grace note, which the commit's melisma sync just marked STOP — now sit inside
        // the chain rather than ending it.
        for (var i = currentIndex + 1; i < nextIndex; i++) {
            var midElement = line.getElement(i);

            if (midElement.getLyricForVerse(verse) == null) {
                continue;
            }

            line.modifyElement(i, ElementField.LYRIC, () ->
                midElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.CONTINUE));
        }

        var nextElement = line.getElement(nextIndex);
        line.modifyElement(nextIndex, ElementField.LYRIC, () ->
            nextElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.STOP));

        // The commit's boundary fix stops at the carrier; the first syllable past the chain
        // still has to lose any word continuation that ran through it.
        line.adjustSuccessorAfterMelismaCarrier(nextIndex, verse);
    }

    private void rewriteLyricExtend(int index, Lyric existing, Lyric.Extend newExtend) {
        var indexElement = line.getElement(index);
        line.modifyElement(index, ElementField.LYRIC, () ->
            indexElement.setLyricForVerse(verse,
                existing.syllabic(), existing.compound(), existing.text(), newExtend));
    }

    /** Whether a lyric with this extender state hands a melisma on to the elements after it. */
    static boolean sustainsMelisma(Lyric.Extend extend) {
        return extend == Lyric.Extend.START || extend == Lyric.Extend.CONTINUE;
    }

    /**
     * Returns the word-final counterpart of a text-bearing syllable's {@code syllabic}:
     * {@code BEGIN} collapses to {@code SINGLE} and {@code MIDDLE} to {@code END}; already
     * word-final values pass through.
     */
    private static Lyric.Syllabic wordFinalSyllabic(Lyric.@Nullable Syllabic syllabic) {
        return switch (syllabic) {
            case BEGIN -> Lyric.Syllabic.SINGLE;
            case MIDDLE -> Lyric.Syllabic.END;
            case SINGLE, END -> syllabic;
            case null -> throw RuntimeError.exit("Text-bearing lyric is missing syllabic");
        };
    }

    /**
     * A one-line rendering of every element's lyric in the edited verse, for the editor's trace.
     * Each element appears as its index followed by its state: {@code -} for no lyric, {@code ~}
     * for a carrier (no syllable of its own), otherwise the quoted text and its syllabic. The
     * extender state and the compound flag follow when they are set. For example:
     *
     * <pre>0="a"/SINGLE/START 1=~/STOP 2="b"/SINGLE 3=-</pre>
     *
     * <p>Called only from a caller that has already checked that tracing is on — this walks the
     * whole line and builds a string every time.
     */
    String lyricRowDescription() {
        var description = new StringBuilder();
        var elements = line.getElements();

        for (var i = 0; i < elements.size(); i++) {
            if (i > 0) {
                description.append(' ');
            }

            description.append(i).append('=');
            var lyric = elements.get(i).getLyricForVerse(verse);

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

    /**
     * Records one decision the chain machinery made. Carries no editor state of its own, so the
     * editor pairs these with its own {@code logState} where the state that drove the decision
     * also matters.
     */
    private static void trace(String format, @Nullable Object... args) {
        if (LogUtils.isTracingLyrics(LOG)) {
            LOG.debug(format, args);
        }
    }
}
