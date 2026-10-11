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
import java.util.EnumSet;
import java.util.List;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.RecordedChange;
import songscribe.dom.Span;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;

/**
 * Decides which spans survive a paste: the destination spans the paste lands <em>inside</em>
 * of, and the fragment spans the destination refuses where they land.
 *
 * <p>The two rules are independent and run in that order, at two different moments of
 * the paste, and each answers with a type of its own: {@link #reconcile} with this record,
 * {@link #settle} with a {@link Settlement}. {@link FragmentPaste} runs the paste around them.
 *
 * <table border="1">
 *   <caption>The two drop rules</caption>
 *   <tr><th>Rule</th><th>Runs</th><th>Drops</th></tr>
 *   <tr>
 *     <td>straddle</td>
 *     <td>before the paste mutates anything</td>
 *     <td>straddled destination spans, and the fragment spans of a kind that a
 *         straddle invalidates</td>
 *   </tr>
 *   <tr>
 *     <td>target</td>
 *     <td>after the fragment's elements are in, before any of its spans is added</td>
 *     <td>fragment tuplets, beams, endings and hairpins that their kind's own rule
 *         refuses where they landed</td>
 *   </tr>
 * </table>
 *
 * <p>A paste never cancels because of a span. It leaves out what either rule drops, and every
 * span it leaves out reaches the paste's report: a straddled destination span through
 * {@link Span#removeReported}, with the reason its {@link LostSpan} carries, and a fragment
 * span either rule drops through {@link Settlement#reports}.
 *
 * <h2>The straddle rule</h2>
 *
 * <p>{@link Fragment#capture} already guarantees the source side is internally
 * consistent — a span is captured only when both its endpoints are inside the copied
 * range — and the deletion sweep in {@link Line#removeRange} already drops any target
 * span whose anchor or end is deleted by a paste-replace. What neither covers is the
 * span that <em>straddles</em> the paste: its anchor sits before the paste region and
 * its end sits after, so both endpoints survive and the span silently stretches over
 * material the user never put under it. {@link Span#straddling} is the test.
 *
 * <p>A straddling target span is reconciled by kind:
 *
 * <table border="1">
 *   <caption>Straddle policy by span kind</caption>
 *   <tr><th>Kind</th><th>Target</th><th>Fragment</th></tr>
 *   <tr><td>{@link Tuplet}</td><td>removed</td><td>tuplets dropped</td></tr>
 *   <tr><td>{@link Beam}</td><td>removed</td><td>beams dropped</td></tr>
 *   <tr><td>{@link Trill}</td><td>removed</td><td>kept</td></tr>
 *   <tr><td>{@link Hairpin}</td><td colspan="2">by type — see below</td></tr>
 *   <tr><td>{@link Ending}</td><td>kept</td><td>endings dropped</td></tr>
 * </table>
 *
 * <p>Tuplets and beams are rhythmic groupings: a group that no longer covers the
 * notes it was written for is not merely misplaced but wrong, and a pasted group
 * dropped into the wreckage of a broken one is equally wrong — so both sides go.
 * A trill binds specific adjacent notes, so a straddled one is dropped, but a
 * fragment's own trill still binds its own notes and is kept. A tie is not a span
 * and this class does not judge one: it is state the anchor note carries, travels
 * with the pasted notes however they land, and is judged by the tie rule when the
 * paste's modification bracket closes, the same as any other tie in a line the
 * bracket touched — including one that spans the paste point.
 *
 * <p><b>Hairpins reconcile by type</b>, because a crescendo and a diminuendo say
 * opposite things when they <em>overlap</em> — which is all the straddle rule judges,
 * since it only reconciles a destination hairpin the paste straddles. A straddled
 * destination hairpin is kept — and silently widened by the insertion, which reads
 * correctly over any span of notes — only while the fragment says nothing that
 * contradicts it: if the fragment carries a hairpin of a <em>different</em> type, the
 * destination's is removed and the fragment's own hairpins win, since a diminuendo nested
 * inside a crescendo is a contradiction no widening can fix. Otherwise the destination's
 * wins and the fragment's — necessarily a shorter hairpin of the same type inside it — is
 * dropped as redundant.
 *
 * <p>An ending straddling the paste follows the hairpin rule for the same reason in
 * reverse: an ending bracket covering a few extra notes is still valid notation, but an
 * ending <em>nested inside</em> another one never is. So the destination ending is kept and
 * the fragment's is dropped. Whether the destination ending survives the pasted
 * <em>content</em> is a separate and more precise question that {@link Line#addElement}
 * already answers per clone via {@link Ending#outcomeFor}, the ending's own
 * barline/repeat-aware rule; this class does not second-guess it. A fragment carrying a
 * whole ending necessarily carries that ending's own barlines, so in practice that rule
 * usually removes the destination ending too.
 *
 * <p><b>Only a straddle counts.</b> The tuplet/beam rule reads as "if the paste
 * does not completely replace a group, remove it from both sides", which literally
 * also covers a paste-replace that clips a group at its edge — deleting one endpoint
 * and orphaning notes on the other side. That case is excluded: the destination group
 * dies anyway (the deletion sweep sees the lost endpoint), and the pasted group lands
 * contiguous and self-consistent at the boundary rather than interleaved with the
 * orphaned remains, which is the mess the rule exists to prevent. Dropping it there
 * would strip beaming from a paste merely for landing next to a beamed note. The
 * orphaned notes' own beaming is left to the user, matching the "beams at the seams"
 * note in {@code docs/clipboard.md}.
 *
 * <h2>The target rule</h2>
 *
 * <p>A fragment span is a copy of a span that was valid where it was captured. What the
 * destination can change is whether its range is still valid where it lands, and the
 * paste is the only moment that is knowable. Every kind with a factory is judged by it, the
 * same rule that judges creating that kind by hand there:
 *
 * <table border="1">
 *   <caption>Target policy by span kind</caption>
 *   <tr><th>Kind</th><th>Judged by</th><th>What is added when accepted</th></tr>
 *   <tr><td>{@link Tuplet}</td><td>{@link Tuplet#spanningStated}, strictly, with the
 *       tuplet's own grade and ratio</td><td rowspan="4">the span the factory built,
 *       carrying the fragment span's user state</td></tr>
 *   <tr><td>{@link Beam}</td><td>{@link Beam#spanning}</td></tr>
 *   <tr><td>{@link Ending}</td><td>{@link Ending#spanning}</td></tr>
 *   <tr><td>{@link Hairpin}</td><td>{@link Hairpin#spanning}</td></tr>
 *   <tr><td>{@link Trill}</td><td>nothing</td><td>the fragment's trill</td></tr>
 * </table>
 *
 * <p>A span that its factory accepts is built by the factory, never taken from the fragment,
 * so every tuplet, beam, ending and hairpin a paste adds is one its kind's own rule made. That
 * rule covers what the straddle rule does not see: a tuplet that now crosses a beat barrier or
 * a barline; an ending pasted after a {@code ||}, or in front of a barline; a hairpin whose
 * range now holds a repeat; an ending that would overlap one the destination keeps. A trill
 * has no rule a destination can break: it binds the fragment's own notes, which land
 * together.
 *
 * <p>A tuplet travels through the clipboard with its ratio, so it is judged with that ratio
 * rather than one re-derived from the destination's beat. Re-deriving would destroy the
 * tuplets the model goes out of its way to preserve — a document may state a ratio the
 * convention would not choose, and pasting such a tuplet back where it came from would refuse
 * it. What the destination can break is its placement.
 *
 * <p><b>A paste that merely abuts a hairpin is not a refusal.</b> Two same-type hairpins
 * left nose to tail are one hairpin, and {@link Hairpin#spanning} judges and builds the range
 * the converged hairpin will hold, whose absorbed hairpins {@link Span#addTo} then removes —
 * the same rule that applies when the user draws a hairpin flush against an existing one.
 * A different type merges with nothing, and the two hairpins stand side by side, again
 * exactly as when drawn.
 *
 * @param targetSpansToRemove  the spans on the destination line the paste straddles, each with
 *                             the reason it is removed; still on the line
 * @param fragmentSpansKept    the fragment's spans the straddle rule kept, in the fragment's
 *                             order; not yet judged by the target rule, which {@link #settle}
 *                             applies
 * @param fragmentSpansDropped the fragment's spans the straddle rule dropped, each with the
 *                             reason it is reported with
 */
public record PasteSpanReconciliation(
    List<LostSpan> targetSpansToRemove,
    List<Span<?>> fragmentSpansKept,
    List<LostSpan> fragmentSpansDropped
) {

    public PasteSpanReconciliation {
        targetSpansToRemove = List.copyOf(targetSpansToRemove);
        fragmentSpansKept = List.copyOf(fragmentSpansKept);
        fragmentSpansDropped = List.copyOf(fragmentSpansDropped);
    }

    /**
     * A span the paste does not keep, paired with the reason the user is told.
     *
     * @param span      the span: a destination span still on its line, or a fragment span never
     *                  added to any line
     * @param reasonKey the {@code Strings} key of the reason, whose value is plain text
     */
    public record LostSpan(Span<?> span, String reasonKey) {}

    /**
     * What the target rule leaves of the fragment's spans: the spans to add, and the report
     * entry for every fragment span the paste leaves out.
     *
     * @param spansToAdd the span to add for each fragment span accepted, in the fragment's order;
     *                   none of them is on a line yet
     * @param reports    one entry per fragment span the straddle rule dropped or the target rule
     *                   refused, naming the line the span's elements landed on, ready to record
     */
    public record Settlement(List<Span<?>> spansToAdd, List<RecordedChange> reports) {

        public Settlement {
            spansToAdd = List.copyOf(spansToAdd);
            reports = List.copyOf(reports);
        }
    }

    /**
     * Reconciles {@code fragmentSpans} against the spans already on {@code line} — the
     * straddle rule.
     *
     * <p>Must be called on the <em>pre-mutation</em> line: {@code target} indexes the line as
     * it stands before the paste's delete and insert.
     *
     * @param line          The destination line, before any paste mutation
     * @param target        Where the fragment lands, and the range a paste-replace deletes
     *                      first
     * @param fragmentSpans The spans the instantiated fragment carries
     * @return the straddled destination spans to remove, and the fragment spans split into
     *     those kept and those dropped
     * @invariant every one of {@code fragmentSpans} is in exactly one of
     *     {@link #fragmentSpansKept} and {@link #fragmentSpansDropped}
     * @invariant every destination span to remove has the reason
     *     {@link Strings#REPORT_REASON_PASTE_STRADDLED}, and every dropped fragment span
     *     the reason {@link Strings#REPORT_REASON_PASTE_FRAGMENT_DROPPED}
     * @effects none; {@code line} and its span registry are unchanged
     */
    public static PasteSpanReconciliation reconcile(
        Line line,
        StaffElementRun.InsertionTarget target,
        List<Span<?>> fragmentSpans
    ) {
        var toRemove = new ArrayList<LostSpan>();
        var dropTuplets = false;
        var dropBeams = false;
        var dropEndings = false;

        // Hairpin kinds whose straddled destination hairpin survives, and which the
        // fragment therefore must not also contribute.
        var keptStraddledHairpinKinds = EnumSet.noneOf(Hairpin.Kind.class);

        for (var span : line.spanRegistry().findSpans(Span.straddling(target))) {
            switch (span) {
                case Tuplet tuplet -> {
                    toRemove.add(straddled(tuplet));
                    dropTuplets = true;
                }
                case Beam beam -> {
                    toRemove.add(straddled(beam));
                    dropBeams = true;
                }
                case Trill trill -> toRemove.add(straddled(trill));
                case Hairpin hairpin -> {
                    if (fragmentContradictsHairpin(fragmentSpans, hairpin)) {
                        toRemove.add(straddled(hairpin));
                    } else {
                        keptStraddledHairpinKinds.add(hairpin.getKind());
                    }
                }
                case Ending ignored -> dropEndings = true;
            }
        }

        var keptFragmentSpans = new ArrayList<Span<?>>(fragmentSpans.size());
        var droppedFragmentSpans = new ArrayList<LostSpan>();

        for (var span : fragmentSpans) {
            var dropped = (dropTuplets && span instanceof Tuplet)
                || (dropBeams && span instanceof Beam)
                || (dropEndings && span instanceof Ending)
                || (span instanceof Hairpin hairpin && keptStraddledHairpinKinds.contains(hairpin.getKind()));

            if (dropped) {
                droppedFragmentSpans.add(new LostSpan(span, Strings.REPORT_REASON_PASTE_FRAGMENT_DROPPED));
            } else {
                keptFragmentSpans.add(span);
            }
        }

        return new PasteSpanReconciliation(toRemove, keptFragmentSpans, droppedFragmentSpans);
    }

    /**
     * Judges every one of {@link #fragmentSpansKept} against {@code line} as the paste leaves it
     * — the target rule — and returns the spans to add in their place, with the report entries
     * for every fragment span the paste leaves out.
     *
     * <p><b>Call this after the fragment's elements have been inserted into {@code line} and
     * before any fragment span is added.</b> Every index a judge reads, and the line a report
     * entry names, resolves through the span's anchor element's own line, so the elements
     * must already sit where they will finally sit. Every span is judged against the destination
     * as it stood before any fragment span was added, so no fragment span is judged against
     * another. Refusing a span that was never added is also what keeps this rule free of any
     * mutation of its own: there is nothing for the paste's bracket to record, so the paste's
     * single undo step covers the refusal by construction.
     *
     * @param line The destination line, with the fragment's elements already inserted and
     *             none of its spans added
     * @return the spans to add and the report entries; see {@link Settlement}
     * @invariant each of {@link #fragmentSpansKept} is either accepted, and contributes exactly
     *     one span to {@link Settlement#spansToAdd}, or refused, and contributes exactly one
     *     entry to {@link Settlement#reports}; never both
     * @invariant each of {@link #fragmentSpansDropped} contributes exactly one entry to
     *     {@link Settlement#reports}, with the reason it carries
     * @invariant a trill is added as the fragment's own span; a tuplet, beam, ending or hairpin
     *     is added as the span its factory built ({@link Tuplet#spanningStated},
     *     {@link Beam#spanning}, {@link Ending#spanning}, {@link Hairpin#spanning}), carrying
     *     the fragment span's user state through {@link LineElement#copyBaseStateFrom}, never as the
     *     fragment's
     * @invariant a refused span is reported with the reason of its factory's
     *     {@link Spanning.Refused}
     * @effects none; {@code line} and its span registry are unchanged
     */
    public Settlement settle(Line line) {
        var spansToAdd = new ArrayList<Span<?>>(fragmentSpansKept.size());
        var reports = new ArrayList<RecordedChange>();

        for (var dropped : fragmentSpansDropped) {
            reports.add(dropped.span().reportedAs(dropped.reasonKey()));
        }

        for (var span : fragmentSpansKept) {
            switch (span) {
                case Tuplet tuplet -> settleWith(tuplet, Tuplet.spanningStated(
                    Tuplet.Site.at(line, tuplet.range()), tuplet.getGrade(), tuplet.getRatio(),
                    TupletValidator.Strictness.STRICT), spansToAdd, reports);
                case Beam beam -> settleWith(beam, Beam.spanning(line, beam.range()), spansToAdd, reports);
                case Ending ending -> settleWith(ending, Ending.spanning(line, ending.range()), spansToAdd, reports);
                case Hairpin hairpin -> settleWith(
                    hairpin, Hairpin.spanning(line, hairpin.getKind(), hairpin.range()), spansToAdd, reports);
                case Trill trill -> spansToAdd.add(trill);
            }
        }

        return new Settlement(spansToAdd, reports);
    }

    /**
     * Settles {@code fragmentSpan} on its factory's {@code answer}: the span the factory built,
     * taking {@code fragmentSpan}'s user state, joins {@code spansToAdd}; a refusal joins
     * {@code reports}, naming the line {@code fragmentSpan}'s elements landed on.
     */
    private static <E extends StaffElement, S extends Span<E>> void settleWith(
        S fragmentSpan,
        Spanning<S> answer,
        List<Span<?>> spansToAdd,
        List<RecordedChange> reports
    ) {
        switch (answer) {
            case Spanning.Spanned<S>(var spanned, var ignored) -> {
                spanned.copyBaseStateFrom(fragmentSpan);
                spansToAdd.add(spanned);
            }
            case Spanning.Refused<S> refused -> reports.add(fragmentSpan.reportedAs(refused.reasonKey()));
        }
    }

    /** The removal of {@code span}, a destination span the paste straddles. */
    private static LostSpan straddled(Span<?> span) {
        return new LostSpan(span, Strings.REPORT_REASON_PASTE_STRADDLED);
    }

    /**
     * Whether the fragment carries a hairpin of a type other than {@code hairpin}'s —
     * the one thing that can beat a straddled destination hairpin, since the pasted
     * notes would then be reading two opposite dynamics at once.
     */
    private static boolean fragmentContradictsHairpin(
        List<Span<?>> fragmentSpans,
        Hairpin hairpin
    ) {
        for (var span : fragmentSpans) {
            if (span instanceof Hairpin fragmentHairpin
                    && fragmentHairpin.getKind() != hairpin.getKind()) {
                return true;
            }
        }

        return false;
    }
}
