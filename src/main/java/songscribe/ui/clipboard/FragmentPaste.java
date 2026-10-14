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

package songscribe.ui.clipboard;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ChangeLines;
import songscribe.dom.KeyChangeStranding;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;

/**
 * Lands a fragment on a line: the paste itself, once the gestures around it have settled what
 * lands and where, and that it fits.
 *
 * <p>The order of the steps is forced. The straddle rule and the destination removals read
 * indices of the line as it stood before the paste, so they precede the deletion and the
 * insertion. The target rule and the report entries read the fragment's spans through their
 * anchors' line, so they follow the insertion. Every clone is in before the first span is added,
 * because a span added while its endpoints carry no line resolves its indices against nothing.
 *
 * <p>A fragment with no elements places nothing, and the straddle rule does not run.
 */
public final class FragmentPaste {

    private FragmentPaste() {
    }

    /**
     * Pastes {@code reduction}'s fragment into {@code line} at {@code target}, as one edit of the
     * caller's open modification bracket, and reports every span the paste removes or leaves out.
     *
     * <p>Every span the paste adds is the one its kind's factory built where the fragment
     * landed, carrying the fragment span's user state. {@link PasteSpanReconciliation#settle}
     * states the rule.
     *
     * <p>The fragment's elements and spans are taken as they are: a fresh instantiation, which
     * this paste consumes, already reduced by {@link Fragment#withoutRedundantKeyChanges} for the
     * key in effect at {@code target}, and without a leading breath mark the line cannot take
     * there. At the start of the song the caller has also dropped the fragment's leading bar
     * lines ({@link Fragment.Reduction#withoutLeadingBars}) and already made the key of a key
     * change it dropped {@code line}'s own key, so the key in effect at {@code target} — which
     * every stranding here is measured from — is the one the fragment's elements sound in. The
     * paste is not fit-gated here; the caller has already decided that the fragment fits.
     *
     * @param line      the destination line, one of its song's lines
     * @param target    where the fragment lands, and the range a paste-replace deletes first;
     *                  indices of {@code line} as it stands when this is called
     * @param reduction the fragment to land, and the spans its key-change reduction left out
     * @throws IllegalStateException if the song has neither an open modification bracket nor
     *     suspended mutation tracking
     * @effects within the caller's bracket: removes the stranded key changes on every line the
     *     fragment's key reaches; removes each destination span the paste straddles, reported
     *     with {@link PasteSpanReconciliation.LostSpan#reasonKey}; deletes {@code target}'s
     *     range when it has one; inserts the fragment's elements at {@code target}'s insert
     *     index; records through {@link songscribe.dom.Song#report} one entry per span of
     *     {@link Fragment.Reduction#strandedSpans} and one per entry of
     *     {@link PasteSpanReconciliation.Settlement#reports}; adds every span of
     *     {@link PasteSpanReconciliation.Settlement#spansToAdd}
     * @invariant a stranded span's entry names {@code line} when some element it covered landed,
     *     and has no place when none did
     */
    public static void paste(Line line, StaffElementRun.InsertionTarget target, Fragment.Reduction reduction) {
        var fragment = reduction.fragment();
        var clones = fragment.elements();
        var insertIndex = target.insertIndex();
        var deleteRange = target.deleteRange();

        // The first index this paste leaves standing after itself, where the fragment's key
        // starts to strand key changes.
        var successorIndex = deleteRange == null ? insertIndex : deleteRange.range().end() + 1;
        var keyAfterFragment = fragment.keyAtEndUnder(line.keyBefore(insertIndex));
        var stranding = KeyChangeStranding.ofMidLineEdit(
            line,
            line.redundantKeyChangeRanges(successorIndex, keyAfterFragment),
            line.keyAtEndUnder(successorIndex, keyAfterFragment));

        if (clones.isEmpty()) {
            // The ranges name indices past the deleted range, which the deletion would move.
            stranding.sweep();

            if (deleteRange != null) {
                line.deleteRange(deleteRange);
            }

            reportStranded(line, reduction.strandedSpans());
            return;
        }

        stranding.sweepExcept(line);

        var reconciliation = PasteSpanReconciliation.reconcile(line, target, fragment.spans());

        for (var lost : reconciliation.targetSpansToRemove()) {
            lost.span().removeReported(lost.reasonKey());
        }

        if (deleteRange != null) {
            line.deleteRange(deleteRange);
        }

        // The addElement inside insertRun also drops the destination spans the inserted element
        // types invalidate, each by its own rule: the ending's barline/repeat-aware one, which is
        // more precise than the straddle test, so a destination ending is left to it. A tie is
        // not a span: it travels with the pasted notes, and the tie rule judges every tie the
        // paste touches when the caller's bracket closes.
        line.insertRun(insertIndex, clones);

        var settlement = reconciliation.settle(line);
        var song = line.getSong();

        reportStranded(line, reduction.strandedSpans());

        for (var report : settlement.reports()) {
            song.report(report);
        }

        for (var span : settlement.spansToAdd()) {
            span.addTo();
        }

        // Every range stranded on this line begins at or past the successor, so all of them moved
        // by exactly what the successor moved by.
        stranding.sweepDeferred(line, insertIndex + clones.size() - successorIndex);
    }

    private static void reportStranded(Line line, List<Fragment.StrandedSpan> strandedSpans) {
        var song = line.getSong();

        for (var stranded : strandedSpans) {
            song.report(stranded.span().reportedAt(stranded.reasonKey(), landedLines(line, stranded.covered())));
        }
    }

    /**
     * {@code line}, when some element of {@code covered} is on it; {@code null} when none is.
     */
    private static @Nullable ChangeLines landedLines(Line line, List<StaffElement> covered) {
        return covered.stream().anyMatch(line::contains) ? ChangeLines.on(line) : null;
    }
}
