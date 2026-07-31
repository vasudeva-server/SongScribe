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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;
import songscribe.layout.Ending;
import songscribe.layout.InsertionSpacingCalculator;

/**
 * Decides which spans survive a paste: the ones the paste lands <em>inside</em> of on
 * the destination line (#614), and the pasted tuplets whose span the destination breaks
 * (#604).
 *
 * <p>The two rules are independent and run in that order, at two different moments of
 * the paste — see {@link #reconcile} and {@link #dropTupletsRejectedByTarget}.
 *
 * <table border="1">
 *   <caption>The two drop rules</caption>
 *   <tr><th>Rule</th><th>Runs</th><th>Drops</th></tr>
 *   <tr>
 *     <td>straddle (#614)</td>
 *     <td>before the paste mutates anything</td>
 *     <td>straddled destination spans, and the fragment spans of a kind that a
 *         straddle invalidates</td>
 *   </tr>
 *   <tr>
 *     <td>target beat context (#604)</td>
 *     <td>after the fragment's elements are in, before its spans are added</td>
 *     <td>pasted tuplets {@link TupletValidator} rejects where they landed</td>
 *   </tr>
 * </table>
 *
 * <p>{@link Fragment#capture} already guarantees the source side is internally
 * consistent — a span is captured only when both its endpoints are inside the copied
 * range — and the deletion sweep in {@link Line#removeRange} already drops any target
 * span whose anchor or end is deleted by a paste-replace. What neither covers is the
 * span that <em>straddles</em> the paste: its anchor sits before the paste region and
 * its end sits after, so both endpoints survive and the span silently stretches over
 * material the user never put under it.
 *
 * <p>A straddling target span is reconciled by kind:
 *
 * <table border="1">
 *   <caption>Straddle policy by span kind</caption>
 *   <tr><th>Kind</th><th>Target</th><th>Fragment</th></tr>
 *   <tr><td>{@link Tuplet}</td><td>removed</td><td>tuplets dropped</td></tr>
 *   <tr><td>{@link Beam}</td><td>removed</td><td>beams dropped</td></tr>
 *   <tr><td>{@link Tie}</td><td>removed</td><td>kept</td></tr>
 *   <tr><td>{@link Trill}</td><td>removed</td><td>kept</td></tr>
 *   <tr><td>{@link Hairpin}</td><td colspan="2">by type — see below</td></tr>
 *   <tr><td>{@link Ending}</td><td>kept</td><td>endings dropped</td></tr>
 * </table>
 *
 * <p>Tuplets and beams are rhythmic groupings: a group that no longer covers the
 * notes it was written for is not merely misplaced but wrong, and a pasted group
 * dropped into the wreckage of a broken one is equally wrong — so both sides go.
 * Ties and trills bind specific adjacent notes, so a straddled one is dropped, but
 * a fragment's own tie or trill still binds its own notes and is kept.
 *
 * <p><b>Hairpins reconcile by type</b>, because a crescendo and a diminuendo say
 * opposite things. A straddled destination hairpin is kept — and silently widened by
 * the insertion, which reads correctly over any span of notes — only while the
 * fragment says nothing that contradicts it: if the fragment carries a hairpin of a
 * <em>different</em> type, the destination's is removed and the fragment's own
 * hairpins win, since a diminuendo nested inside a crescendo is a contradiction no
 * widening can fix. Otherwise the destination's wins and the fragment's — necessarily
 * a shorter hairpin of the same type inside it — is dropped as redundant.
 *
 * <p><b>A paste that merely abuts a hairpin is not this class's problem.</b> Two
 * same-type hairpins left nose to tail are one hairpin, but that is the same rule
 * that applies when the user draws a hairpin flush against an existing one, so it
 * lives where drawing already handles it: {@link Line#addCrescendo} and
 * {@link Line#addDiminuendo} absorb an overlapping <em>or adjacent</em> same-type
 * hairpin, and {@code tryInsertFragment} adds pasted spans through
 * {@link Line#addPastedRangeElement}, which routes hairpins to them. A different type
 * merges with nothing and the two hairpins stand side by side, again exactly as when
 * drawn. Nothing here needs to know about it.
 *
 * <p><b>Only a straddle counts.</b> #614 words the tuplet/beam rule as "if the paste
 * does not completely replace a group, remove it from both sides", which literally
 * also covers a paste-replace that clips a group at its edge — deleting one endpoint
 * and orphaning notes on the other side. That case is deliberately excluded: the
 * destination group dies anyway (the deletion sweep sees the lost endpoint), and the
 * pasted group lands contiguous and self-consistent at the boundary rather than
 * interleaved with the orphaned remains, which is the mess the rule exists to prevent.
 * Dropping it there would strip beaming from a paste merely for landing next to a
 * beamed note. The orphaned notes' own beaming is left to the user, matching the
 * "beams at the seams" note in {@code docs/clipboard.md}.
 *
 * <p>An ending straddling the paste follows the hairpin rule for the same reason in
 * reverse: an ending bracket covering a few extra notes is still valid notation, but
 * an ending <em>nested inside</em> another one never is — and nothing else in the
 * codebase rejects a nested ending, since {@code makeFirstSecondEnding} validates
 * repeat context rather than existing-ending overlap. So the destination ending is
 * kept and the fragment's is dropped. Whether the destination ending survives the
 * pasted <em>content</em> is a separate and more precise question that
 * {@link Line#addElement} already answers per clone via
 * {@code Ending.isInvalidatedByInsertion}, the ending's own barline/repeat-aware
 * rule; this class does not second-guess it. A fragment carrying a whole ending
 * necessarily carries that ending's own barlines, so in practice that rule usually
 * removes the destination ending too, and the paste is confirmed on those terms.
 *
 * @param targetSpansToRemove     Spans on the destination line to remove before inserting
 * @param fragmentSpans           The fragment's spans that should still be added
 * @param tupletsRejectedByTarget The pasted tuplets the destination rejected, named
 *                                separately so the two drop rules stay tellable apart and
 *                                so the caller can warn the user about this one; empty
 *                                until {@link #dropTupletsRejectedByTarget} has run
 */
public record PasteSpanReconciliation(
    List<RangeElement> targetSpansToRemove,
    List<RangeElement> fragmentSpans,
    List<Tuplet> tupletsRejectedByTarget
) {

    /**
     * Reconciles {@code fragmentSpans} against the spans already on {@code line} — the
     * straddle rule. The returned reconciliation carries no rejected tuplets yet; that
     * is {@link #dropTupletsRejectedByTarget}'s job, later in the same paste.
     *
     * <p>Must be called on the <em>pre-mutation</em> line: every index below is a
     * live index into the line as it stands before the paste's delete and insert.
     *
     * @param line          The destination line, before any paste mutation
     * @param insertIndex   The index the fragment's first element will land at
     * @param deleteRange   The range a paste-replace deletes first, or null for a pure insertion
     * @param fragmentSpans The spans the instantiated fragment carries
     */
    public static PasteSpanReconciliation reconcile(
        Line line,
        int insertIndex,
        InsertionSpacingCalculator.@Nullable DeletedRange deleteRange,
        List<RangeElement> fragmentSpans
    ) {
        // The first index after the paste region. For a pure insertion the region is
        // empty, so it collapses onto insertIndex itself.
        var firstIndexAfterRegion = deleteRange == null ? insertIndex : deleteRange.end() + 1;

        var toRemove = new ArrayList<RangeElement>();
        var dropTuplets = false;
        var dropBeams = false;
        var dropEndings = false;

        // Hairpin types whose straddled destination hairpin survives, and which the
        // fragment therefore must not also contribute.
        var keptStraddledHairpinTypes = new HashSet<Class<?>>();

        for (var span : line.getRangeElements()) {
            var anchorIndex = span.getAnchorElementIndex();
            var endIndex = span.getEndElementIndex();

            // A span straddles the paste when its anchor lies strictly before the
            // region and its end lies at or after the first index past it. That
            // simultaneously excludes spans fully inside the deleted range (whose
            // endpoints the deletion sweep removes) and spans clear of it entirely.
            if (anchorIndex < 0 || endIndex < 0
                    || anchorIndex >= insertIndex
                    || endIndex < firstIndexAfterRegion) {
                continue;
            }

            switch (span) {
                case Tuplet tuplet -> {
                    toRemove.add(tuplet);
                    dropTuplets = true;
                }
                case Beam beam -> {
                    toRemove.add(beam);
                    dropBeams = true;
                }
                case Tie tie -> toRemove.add(tie);
                case Trill trill -> toRemove.add(trill);
                case Hairpin hairpin -> {
                    if (fragmentContradictsHairpin(fragmentSpans, hairpin)) {
                        toRemove.add(hairpin);
                    } else {
                        keptStraddledHairpinTypes.add(hairpin.getClass());
                    }
                }
                case Ending ignored -> dropEndings = true;
                default -> { }
            }
        }

        var keptFragmentSpans = new ArrayList<RangeElement>(fragmentSpans.size());

        for (var span : fragmentSpans) {
            var dropped = (dropTuplets && span instanceof Tuplet)
                || (dropBeams && span instanceof Beam)
                || (dropEndings && span instanceof Ending)
                || (span instanceof Hairpin && keptStraddledHairpinTypes.contains(span.getClass()));

            if (!dropped) {
                keptFragmentSpans.add(span);
            }
        }

        return new PasteSpanReconciliation(toRemove, keptFragmentSpans, List.of());
    }

    /**
     * Drops the pasted tuplets the destination rejects (#604) — the second drop rule,
     * independent of the straddle rule above and applied after it.
     *
     * <p>A tuplet travels through the clipboard with the notes it brackets <em>and</em>
     * with its ratio, so what it means does not change on the way. What the destination
     * can change is whether the span still holds together: a beat barrier, or a barline,
     * repeat or breath mark, now sitting inside it splits the group in two. The paste is
     * the only moment that is knowable, and nothing else fires for it — left alone, such a
     * tuplet would render and play until the next save-and-reopen dropped it, with no
     * explanation the user could connect to the paste. Only the bracket goes; the pasted
     * notes are untouched, and the caller warns the user through
     * {@code Song.noteTupletsWereRemoved}.
     *
     * <p><b>Call this after the fragment's elements have been inserted into
     * {@code line} and before its spans are added.</b> Every index the validator reads
     * resolves through the anchor element's own line, so the elements must already sit
     * where they will finally sit. Dropping a bracket that was never added is also what
     * keeps this rule free of any mutation of its own: there is nothing for the paste's
     * bracket to record, so the paste's single undo step covers the drop by construction,
     * and the companion-ordering rule in {@code .agents/guides/mutations.md} is satisfied
     * without ordering anything.
     *
     * <p>The straddle rule's accounting is left alone: the fragment tuplets it dropped
     * are already absent from {@link #fragmentSpans()}, and the ones dropped here are
     * named in {@link #tupletsRejectedByTarget()}.
     *
     * @param line The destination line, with the fragment's elements already inserted
     * @return This reconciliation when nothing is rejected, otherwise one without the
     *         rejected tuplets among its fragment spans
     */
    public PasteSpanReconciliation dropTupletsRejectedByTarget(Line line) {
        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);

        // A line outside its song offers no beat context to validate against. Replay
        // re-applies a recorded batch that already reflects every drop this rule made
        // when the paste first ran, so re-deriving them there could only double-apply.
        if (lineIndex < 0 || song.isReplaying()) {
            return this;
        }

        var keptSpans = new ArrayList<RangeElement>(fragmentSpans.size());
        var rejectedTuplets = new ArrayList<Tuplet>();

        for (var span : fragmentSpans) {
            if (span instanceof Tuplet tuplet && isRejectedByTarget(song, line, lineIndex, tuplet)) {
                rejectedTuplets.add(tuplet);
            } else {
                keptSpans.add(span);
            }
        }

        if (rejectedTuplets.isEmpty()) {
            return this;
        }

        return new PasteSpanReconciliation(targetSpansToRemove, keptSpans, rejectedTuplets);
    }

    /**
     * Whether the destination refuses the tuplet now spanning {@code line}.
     * <p>
     * A tuplet on the clipboard already carries its ratio, so the ratio travels with it and
     * is not re-derived from the destination's beat. Re-deriving would destroy exactly the
     * tuplets the model goes out of its way to preserve — a document may legitimately state
     * a ratio the convention would not choose, and copying such a tuplet and pasting it back
     * where it came from would delete it. What the destination can still break is the span's
     * <em>placement</em>: a beat barrier or a barline, repeat or breath mark now inside it.
     * Those are checked strictly, exactly as creating the tuplet by hand there would be.
     * <p>
     * A tuplet with no ratio yet cannot occur on the clipboard — the load pass resolves or
     * drops every one before a song is ever shown — but if one arrives, its ratio is derived
     * from the destination as creation would.
     */
    private static boolean isRejectedByTarget(Song song, Line line, int lineIndex, Tuplet tuplet) {
        var beginIndex = tuplet.getAnchorElementIndex();
        var endIndex = tuplet.getEndElementIndex();

        // Endpoints that do not resolve mean a malformed span, which is out of scope
        // here: there is nothing for the validator to measure and nothing this rule
        // could say about it that would be better than saying nothing.
        if (beginIndex < 0 || endIndex < beginIndex) {
            return false;
        }

        var context = TupletValidator.describeSpan(song, line, lineIndex, beginIndex, endIndex);
        var noteValue = tuplet.getNoteValue();
        TupletValidator.Result result;

        if (noteValue == null) {
            result = TupletValidator.validate(
                context, tuplet.getGrade(), TupletValidator.Strictness.STRICT);
        } else {
            result = TupletValidator.validateStated(
                context, tuplet.getGrade(), tuplet.getNormalNotes(),
                noteValue, tuplet.getNoteValueDots(), TupletValidator.Strictness.STRICT);
        }

        return !result.valid();
    }

    /**
     * Whether the fragment carries a hairpin of a type other than {@code hairpin}'s —
     * the one thing that can beat a straddled destination hairpin, since the pasted
     * notes would then be reading two opposite dynamics at once.
     */
    private static boolean fragmentContradictsHairpin(
        List<RangeElement> fragmentSpans,
        Hairpin hairpin
    ) {
        for (var span : fragmentSpans) {
            if (span instanceof Hairpin fragmentHairpin
                    && fragmentHairpin.getClass() != hairpin.getClass()) {
                return true;
            }
        }

        return false;
    }
}
