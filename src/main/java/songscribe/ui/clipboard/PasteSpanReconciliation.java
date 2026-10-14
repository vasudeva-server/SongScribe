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
 * <p>The straddle rule ({@link #reconcile}) runs before the paste mutates anything; the target
 * rule ({@link #settle}) runs after the fragment's elements are in and before any of its spans
 * is added. A paste never cancels because of a span: every span it leaves out reaches the
 * paste's report, a straddled destination span through {@link Span#removeReported} and a
 * fragment span through {@link Settlement#reports}.
 *
 * <h2>The straddle rule</h2>
 *
 * <p>A span that <em>straddles</em> the paste has its anchor before the paste region and its end
 * after, so it would silently stretch over material the user never put under it
 * ({@link Span#straddling}). Straddling target spans are reconciled by kind:
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
 * <p>Tuplets and beams are rhythmic groupings, so a broken group and a group pasted into its
 * wreckage are both wrong. A trill binds specific adjacent notes, so a straddled one is dropped,
 * but a fragment's own trill is kept. A tie is not a span and is judged by the tie rule when the
 * paste's modification bracket closes.
 *
 * <p><b>Hairpins reconcile by type.</b> A straddled destination hairpin is kept, and widened by
 * the insertion, unless the fragment carries a hairpin of a different type: then the
 * destination's is removed and the fragment's win, since a diminuendo nested inside a crescendo
 * is a contradiction no widening can fix. Otherwise the destination's wins and the fragment's
 * is dropped as redundant.
 *
 * <p>A straddling ending is kept and the fragment's dropped, because an ending nested inside
 * another is never valid.
 *
 * <p><b>Only a straddle counts.</b> A paste-replace that merely clips a group at its edge is not
 * one: the destination group dies in the deletion sweep, and dropping the pasted group would
 * strip beaming from a paste merely for landing next to a beamed note.
 *
 * <h2>The target rule</h2>
 *
 * <p>Every kind with a factory is judged by the same rule that judges creating it by hand where
 * it lands, and a span its factory accepts is built by the factory, never taken from the
 * fragment. A tuplet is judged with its own ratio,
 * not one re-derived from the destination's beat. A paste that merely abuts a hairpin is not a
 * refusal: same-type hairpins converge as when the user draws one flush against another, and
 * different types stand side by side.
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
     * @param span a destination span still on its line, or a fragment span never added to any line
     */
    public record LostSpan(Span<?> span, String reasonKey) {}

    /**
     * What the target rule leaves of the fragment's spans.
     *
     * @param spansToAdd in the fragment's order; none of them is on a line yet
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
     * @invariant every span is added as the span its factory built
     *     ({@link Tuplet#spanningStated}, {@link Beam#spanning}, {@link Ending#spanning},
     *     {@link Hairpin#spanning}, {@link Trill#spanning}), carrying
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
                case Trill trill -> settleWith(trill, Trill.spanning(line, trill.range()), spansToAdd, reports);
            }
        }

        return new Settlement(spansToAdd, reports);
    }

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

    private static LostSpan straddled(Span<?> span) {
        return new LostSpan(span, Strings.REPORT_REASON_PASTE_STRADDLED);
    }

    /** The one thing that can beat a straddled destination hairpin. */
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
