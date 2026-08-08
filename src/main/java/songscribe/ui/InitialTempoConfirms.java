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

package songscribe.ui;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.InitialTempoTransfer;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

/**
 * Owns the user interaction for preserving the song's starting tempo when an edit changes which
 * element is first in the song: asking whether an incoming first element's own tempo change
 * should give way to the original starting tempo, applying that answer, and warning afterwards
 * when the new first element ends up carrying both a tempo and a beat change.
 *
 * <p>Unlike {@link TempoChangeGuards}, a refusal is never one of the outcomes here — the edit
 * that triggered the question is always allowed to proceed. The only "nothing happens" outcome
 * is the user's own Cancel.
 */
public final class InitialTempoConfirms {

    private InitialTempoConfirms() {}

    /** What the user decided about the starting tempo of the pending edit. */
    public enum Decision {
        /** Abandon the pending operation entirely, mutating nothing. */
        CANCEL,

        /**
         * Proceed, and let whatever ends up on the new first element stand as the song's
         * starting tempo. Also returned when no question needed asking.
         */
        KEEP_TARGET_TEMPO,

        /** Proceed, then put the original starting tempo back on the new first element. */
        RESTORE_ORIGINAL_TEMPO
    }

    /**
     * Decides what should become of the song's starting tempo when {@code
     * prospectiveNewFirstElement} takes over as the song's first element, asking the user only
     * when the answer is genuinely ambiguous — that is, when the incoming element already
     * carries a tempo change of its own and one of the two has to be discarded.
     *
     * <p>Call before mutating anything: {@link Decision#CANCEL} means the whole pending
     * operation is abandoned.
     *
     * @param parent                     the component to parent the dialog on, so it cannot be
     *                                   hidden behind a modal dialog the edit was triggered from
     * @param prospectiveNewFirstElement the element that will be first once the edit runs, or
     *                                   null when the edit leaves the song with no elements
     */
    public static Decision confirmTransfer(@Nullable Component parent, Song song,
                                           @Nullable StaffElement prospectiveNewFirstElement) {
        // Nothing to preserve, nowhere to preserve it, or a free spot to move it to.
        if (InitialTempoTransfer.currentInitialTempo(song) == null
            || prospectiveNewFirstElement == null
            || prospectiveNewFirstElement.findAttachment(TempoChangeAttachment.class) == null) {
            return Decision.KEEP_TARGET_TEMPO;
        }

        var cancelIndex = 0;
        var noIndex = 1;
        var yesIndex = 2;
        var yesLabel = Strings.get(Strings.BUTTON_YES);
        var answer = OptionDialogs.showOptionDialog(
            parent,
            Strings.CONFIRM_TITLE_INITIAL_TEMPO_REPLACE,
            Strings.CONFIRM_INITIAL_TEMPO_REPLACE,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new String[] {
                Strings.get(Strings.BUTTON_CANCEL),
                Strings.get(Strings.BUTTON_NO),
                yesLabel
            },
            yesLabel
        );

        if (answer == cancelIndex) {
            return Decision.CANCEL;
        } else if (answer == noIndex) {
            return Decision.KEEP_TARGET_TEMPO;
        } else if (answer == yesIndex) {
            return Decision.RESTORE_ORIGINAL_TEMPO;
        }

        // A dismissed dialog reports CLOSED_OPTION, as does a suppressed one; both abandon the
        // edit, matching how EndingConfirms treats a dialog the user closed.
        return Decision.CANCEL;
    }

    /**
     * Applies {@code decision} to the song after the edit that triggered it has run, and brings
     * {@link Song#getTempo} back in step with the anchor element.
     *
     * <p>Call inside the caller's already-open modification bracket, so the replacement lands in
     * the same undo step as the edit that triggered it.
     *
     * @param originalTempo the starting tempo as it was before the edit, needed only to restore it
     */
    public static void applyDecision(Song song, @Nullable Tempo originalTempo, Decision decision) {
        if (decision == Decision.RESTORE_ORIGINAL_TEMPO && originalTempo != null) {
            InitialTempoTransfer.replaceInitialTempo(song, originalTempo);
        }

        // Whatever the decision was, the anchor now holds the answer: "keep" covers both the
        // user's No and the case where nothing was asked, so reading the song tempo off the
        // anchor is the only formulation correct for all of No, Yes, a silent transfer, and a
        // song left with no elements at all.
        song.syncTempoFromAnchor();
    }

    /**
     * Warns when the song's new first element carries both a tempo change and a beat change,
     * which no longer notates a meaningful change of tempo and needs the user's attention.
     *
     * <p>Call <em>after</em> the modification bracket has closed, never inside it, because it
     * raises a modal dialog.
     */
    public static void warnIfTempoAndBeatChange(@Nullable Component parent, Song song) {
        var anchor = song.initialTempoAnchor();

        if (anchor == null
            || anchor.findAttachment(TempoChangeAttachment.class) == null
            || anchor.findAttachment(BeatChangeAttachment.class) == null) {
            return;
        }

        OptionDialogs.showWarningMessage(
            parent, Strings.ALERT_TITLE_INITIAL_TEMPO_BEAT_CHANGE,
            Strings.ALERT_INITIAL_TEMPO_BEAT_CHANGE);
    }
}
