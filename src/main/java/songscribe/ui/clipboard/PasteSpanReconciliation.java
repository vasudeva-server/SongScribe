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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElementRun;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;

/**
 * Decides which spans survive a paste: the destination spans the paste lands <em>inside</em>
 * of (#614), and the fragment spans the destination refuses where they land (#604).
 *
 * <p>The two rules are independent and run in that order, at two different moments of
 * the paste — see {@link #reconcile} and {@link #settleAgainstTarget}.
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
 *     <td>target (#604)</td>
 *     <td>after the fragment's elements are in, before any of its spans is added</td>
 *     <td>fragment tuplets, endings and hairpins that their kind's own rule refuses
 *         where they landed</td>
 *   </tr>
 * </table>
 *
 * <p>A paste never cancels because of a span. It leaves out what either rule drops, and the
 * caller reports every span in {@code targetSpansToRemove}, {@code fragmentSpansDropped} and
 * {@code fragmentSpansRejected}.
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
 * usually removes the destination ending too, and the paste is confirmed on those terms.
 *
 * <p><b>Only a straddle counts.</b> #614 words the tuplet/beam rule as "if the paste
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
 * paste is the only moment that is knowable. Three kinds have a rule for that, and each is
 * judged by the same rule that judges creating that kind by hand there:
 *
 * <table border="1">
 *   <caption>Target policy by span kind</caption>
 *   <tr><th>Kind</th><th>Judged by</th><th>What is added when accepted</th></tr>
 *   <tr><td>{@link Tuplet}</td><td>{@link TupletValidator}, strictly, with the tuplet's
 *       own ratio</td><td>the fragment's tuplet</td></tr>
 *   <tr><td>{@link Ending}</td><td>{@link Ending#spanning}</td><td>the ending the factory
 *       built, carrying the fragment ending's user state</td></tr>
 *   <tr><td>{@link Hairpin}</td><td>{@link Hairpin#spanning}</td><td>the hairpin the
 *       factory built, carrying the fragment hairpin's user state</td></tr>
 *   <tr><td>{@link Beam}, {@link Trill}</td><td>nothing</td><td>the fragment's span</td></tr>
 * </table>
 *
 * <p>An ending or hairpin that the factory accepts is built by the factory, never taken
 * from the fragment, so every ending and hairpin a paste adds is one its kind's own rule
 * made. That rule covers what the straddle rule does not see: an ending pasted after a
 * {@code ||}, or in front of a barline; a hairpin whose range now holds a repeat; an
 * ending that would overlap one the destination keeps.
 *
 * <p><b>A paste that merely abuts a hairpin is not a refusal.</b> Two same-type hairpins
 * left nose to tail are one hairpin, and {@link Hairpin#spanning} judges the range the
 * converged hairpin will hold, which {@link Span#addTo} then widens the added hairpin to —
 * the same rule that applies when the user draws a hairpin flush against an existing one.
 * A different type merges with nothing, and the two hairpins stand side by side, again
 * exactly as when drawn.
 *
 * @param targetSpansToRemove   Spans on the destination line to remove before inserting
 * @param fragmentSpans         The fragment's spans that should still be added
 * @param fragmentSpansDropped  The fragment's spans the straddle rule dropped, named so the
 *                              caller can report them
 * @param fragmentSpansRejected The fragment's spans the target rule refused, each with the
 *                              reason it was refused; empty until
 *                              {@link #settleAgainstTarget} has run
 */
public record PasteSpanReconciliation(
    List<Span<?>> targetSpansToRemove,
    List<Span<?>> fragmentSpans,
    List<Span<?>> fragmentSpansDropped,
    List<RejectedSpan> fragmentSpansRejected
) {

    /**
     * A fragment span the destination refused.
     *
     * @param span      the fragment span, never added to any line
     * @param reasonKey the {@code Strings} key of the reason it was refused, as the user reads it
     */
    public record RejectedSpan(Span<?> span, String reasonKey) {}

    /**
     * Reconciles {@code fragmentSpans} against the spans already on {@code line} — the
     * straddle rule. The returned reconciliation carries no rejected spans yet; that is
     * {@link #settleAgainstTarget}'s job, later in the same paste.
     *
     * <p>Must be called on the <em>pre-mutation</em> line: {@code target} indexes the line as
     * it stands before the paste's delete and insert.
     *
     * @param line          The destination line, before any paste mutation
     * @param target        Where the fragment lands, and the range a paste-replace deletes
     *                      first
     * @param fragmentSpans The spans the instantiated fragment carries
     * @return the straddled destination spans to remove, and the fragment spans split into
     *     those kept and those dropped; its {@link #fragmentSpansRejected} is empty
     */
    public static PasteSpanReconciliation reconcile(
        Line line,
        StaffElementRun.InsertionTarget target,
        List<Span<?>> fragmentSpans
    ) {
        var toRemove = new ArrayList<Span<?>>();
        var dropTuplets = false;
        var dropBeams = false;
        var dropEndings = false;

        // Hairpin kinds whose straddled destination hairpin survives, and which the
        // fragment therefore must not also contribute.
        var keptStraddledHairpinKinds = EnumSet.noneOf(Hairpin.Kind.class);

        for (var span : line.spanRegistry().findSpans(Span.straddling(target))) {
            switch (span) {
                case Tuplet tuplet -> {
                    toRemove.add(tuplet);
                    dropTuplets = true;
                }
                case Beam beam -> {
                    toRemove.add(beam);
                    dropBeams = true;
                }
                case Trill trill -> toRemove.add(trill);
                case Hairpin hairpin -> {
                    if (fragmentContradictsHairpin(fragmentSpans, hairpin)) {
                        toRemove.add(hairpin);
                    } else {
                        keptStraddledHairpinKinds.add(hairpin.getKind());
                    }
                }
                case Ending ignored -> dropEndings = true;
            }
        }

        var keptFragmentSpans = new ArrayList<Span<?>>(fragmentSpans.size());
        var droppedFragmentSpans = new ArrayList<Span<?>>();

        for (var span : fragmentSpans) {
            var dropped = (dropTuplets && span instanceof Tuplet)
                || (dropBeams && span instanceof Beam)
                || (dropEndings && span instanceof Ending)
                || (span instanceof Hairpin hairpin && keptStraddledHairpinKinds.contains(hairpin.getKind()));

            if (dropped) {
                droppedFragmentSpans.add(span);
            } else {
                keptFragmentSpans.add(span);
            }
        }

        return new PasteSpanReconciliation(toRemove, keptFragmentSpans, droppedFragmentSpans, List.of());
    }

    /**
     * Judges every one of {@link #fragmentSpans} against {@code line} as the paste leaves it —
     * the target rule — and returns the spans to add in place of the fragment's.
     *
     * <p><b>Call this after the fragment's elements have been inserted into {@code line} and
     * before any fragment span is added.</b> Every index a judge reads resolves through the
     * span's anchor element's own line, so the elements must already sit where they will
     * finally sit. Every span is judged against the destination as it stood before any
     * fragment span was added, so no fragment span is judged against another. Refusing a span
     * that was never added is also what keeps this rule free of any mutation of its own:
     * there is nothing for the paste's bracket to record, so the paste's single undo step
     * covers the refusal by construction.
     *
     * <p>A tuplet travels through the clipboard with its ratio, so it is judged with that
     * ratio rather than one re-derived from the destination's beat. Re-deriving would destroy
     * the tuplets the model goes out of its way to preserve — a document may state a ratio the
     * convention would not choose, and pasting such a tuplet back where it came from would
     * refuse it. What the destination can break is its placement: a beat barrier, or a
     * barline, repeat or breath mark, now inside it.
     *
     * <p>The straddle rule's accounting is left alone: {@link #targetSpansToRemove} and
     * {@link #fragmentSpansDropped} are carried over unchanged.
     *
     * @param line The destination line, with the fragment's elements already inserted and
     *             none of its spans added
     * @return a reconciliation whose {@link #fragmentSpans} holds, in the order of this one's,
     *     the span to add for each fragment span accepted, and whose
     *     {@link #fragmentSpansRejected} names each fragment span refused with its reason
     * @invariant each of this reconciliation's {@link #fragmentSpans} is either accepted, and
     *     contributes exactly one span to the result's {@link #fragmentSpans}, or refused, and
     *     named in the result's {@link #fragmentSpansRejected}; never both
     * @invariant a tuplet, beam or trill is added as the fragment's own span; an ending or
     *     hairpin is added as the span {@link Ending#spanning} or {@link Hairpin#spanning}
     *     built, never as the fragment's
     * @invariant a refused tuplet has the reason
     *     {@link Strings#DIALOG_REPORT_REASON_PASTE_TUPLET_REJECTED}; a refused ending or
     *     hairpin has the reason of its {@link Spanning.Refused}
     * @invariant an accepted ending or hairpin carries the fragment span's user state, taken
     *     through {@link Span#copyUserStateFrom}
     * @effects none; {@code line} and its span registry are unchanged
     */
    public PasteSpanReconciliation settleAgainstTarget(Line line) {
        var keptSpans = new ArrayList<Span<?>>(fragmentSpans.size());
        var rejectedSpans = new ArrayList<RejectedSpan>();

        for (var span : fragmentSpans) {
            switch (span) {
                case Tuplet tuplet -> {
                    if (isRejectedByTarget(line, tuplet)) {
                        rejectedSpans.add(new RejectedSpan(
                            tuplet, Strings.DIALOG_REPORT_REASON_PASTE_TUPLET_REJECTED));
                    } else {
                        keptSpans.add(tuplet);
                    }
                }
                case Ending ending -> {
                    switch (Ending.spanning(line, ending.range())) {
                        case Spanning.Spanned<Ending>(var spanned) -> {
                            spanned.copyUserStateFrom(ending);
                            keptSpans.add(spanned);
                        }
                        case Spanning.Refused<Ending>(var reasonKey, var ignored) ->
                            rejectedSpans.add(new RejectedSpan(ending, reasonKey));
                    }
                }
                case Hairpin hairpin -> {
                    switch (Hairpin.spanning(line, hairpin.getKind(), hairpin.range())) {
                        case Spanning.Spanned<Hairpin>(var spanned) -> {
                            spanned.copyUserStateFrom(hairpin);
                            keptSpans.add(spanned);
                        }
                        case Spanning.Refused<Hairpin>(var reasonKey, var ignored) ->
                            rejectedSpans.add(new RejectedSpan(hairpin, reasonKey));
                    }
                }
                case Beam beam -> keptSpans.add(beam);
                case Trill trill -> keptSpans.add(trill);
            }
        }

        return new PasteSpanReconciliation(
            targetSpansToRemove, keptSpans, fragmentSpansDropped, rejectedSpans);
    }

    /**
     * Whether the destination refuses the tuplet now spanning {@code line}: its placement,
     * checked strictly with its own ratio, exactly as creating the tuplet by hand there
     * would be.
     */
    private static boolean isRejectedByTarget(Line line, Tuplet tuplet) {
        var context = TupletValidator.describeSpan(line, tuplet.range());
        var result = TupletValidator.validateStated(
            context, tuplet.getGrade(), tuplet.getRatio(), TupletValidator.Strictness.STRICT);

        return result instanceof TupletValidator.Result.Invalid;
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
