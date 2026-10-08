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

package songscribe.ui.selection;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletValidator;

/**
 * What an {@link ElementSelection} may become: whether it can be beamed, tied, tupleted,
 * trilled, given a hairpin or a first/second ending, or have its stem direction changed.
 * <p>
 * Every answer here is a pure function of {@code (line, begin, end)} and nothing else, which
 * is why they are static, why they no longer live beside the selection field, and why they take
 * {@link ElementSelection} rather than {@link Selection.Range}: the anchor a {@code Range}
 * additionally carries records where a click landed, and no answer here depends on it. Asking
 * one neither reads nor writes what is currently selected — pass any range and get that range's
 * answer.
 */
public final class RangeQueries {

    /** A single element selected at a line boundary — the cross-line tie candidate. */
    private static final int TIE_SELECTION_SIZE_BOUNDARY = 1;

    /** Two adjacent notes, with nothing between them. */
    private static final int TIE_SELECTION_SIZE_WITHOUT_SEPARATOR = 2;

    /** Two notes with a single non-duration element between them. */
    private static final int TIE_SELECTION_SIZE_WITH_SEPARATOR = 3;

    /** A glissando's source and its target, both selected. */
    private static final int GLISSANDO_SELECTION_SIZE_WITH_TARGET = 2;

    /** A single selected element, read as the glissando's target — its source precedes it. */
    private static final int GLISSANDO_SELECTION_SIZE_SOURCE_ONLY = 1;

    private RangeQueries() {}

    /**
     * A tie candidate spanning a line boundary, with {@code anchor} the endpoint earlier in
     * document order and {@code end} the endpoint later.
     */
    public record BoundaryTie(NoteElement anchor, NoteElement end) {}

    /**
     * Returns the cross-line tie candidate for the element at {@code index} in {@code line}, or
     * {@code null} when the element is not a note, is at no line edge a tie can cross, or the
     * note across that edge cannot be tied to it.
     *
     * <p>All of these must hold: {@code index} is the element a tie would leave or enter
     * {@code line} through — the first non-separator of the line
     * ({@link Tie#firstNonSeparatorIndexFrom}) for the entry side, the last
     * ({@link Tie#lastNonSeparatorIndexBefore}) for the exit side — an adjacent line exists on
     * that side and offers such an element of its own, both elements pass the pitched-note
     * check, and the two share a pitch. The entry side is checked when the element is the
     * line's first non-separator and the line is not its song's first; otherwise the exit side,
     * when the element is the line's last non-separator and the line is not its song's last. A
     * line with only one non-separator, after another line, therefore never offers the boundary
     * after it.
     *
     * @param line  the line holding the element
     * @param index the element's position in {@code line}
     * @return the candidate, or {@code null} when the element at {@code index} is not a
     *     {@link NoteElement}; neither side is checked for it; the adjacent line on the side
     *     checked has no non-separator, or its edge element is not a {@link NoteElement}; or the
     *     two notes fail the pitched-note check or differ in pitch
     */
    public static @Nullable BoundaryTie boundaryTieAt(Line line, int index) {
        var song = line.getSong();

        if (!(line.getElement(index) instanceof NoteElement note)) {
            return null;
        }

        BoundaryTie candidate = null;

        var entryIndex = Tie.firstNonSeparatorIndexFrom(line.getElements(), 0);
        var exitIndex = Tie.lastNonSeparatorIndexBefore(line.getElements(), line.elementCount());

        if (entryIndex != null && index == entryIndex && !line.isFirst()) {
            var previousLine = song.getLine(line.index() - 1);
            var previousElements = previousLine.getElements();
            var anchorIndex = Tie.lastNonSeparatorIndexBefore(previousElements, previousElements.size());

            if (anchorIndex != null && previousLine.getElement(anchorIndex) instanceof NoteElement anchor) {
                candidate = new BoundaryTie(anchor, note);
            }
        } else if (exitIndex != null && index == exitIndex && !line.isLast()) {
            var nextLine = song.getLine(line.index() + 1);
            var endIndex = Tie.firstNonSeparatorIndexFrom(nextLine.getElements(), 0);

            if (endIndex != null && nextLine.getElement(endIndex) instanceof NoteElement end) {
                candidate = new BoundaryTie(note, end);
            }
        }

        if (candidate == null) {
            return null;
        }

        return canBeTied(candidate.anchor(), candidate.end()) ? candidate : null;
    }

    /**
     * Returns whether two notes are eligible to be joined by a tie at all: both pass the
     * pitched-note check ({@link Tie#between}), and they share a pitch.
     * <p>
     * The only statement of that rule. Both paths that offer a tie ask — {@link #canToggleTie}
     * for a pair selected within one line, {@link #boundaryTieAt} for a pair straddling a line
     * break — and a rule written twice is a rule that changes in one place only.
     */
    private static boolean canBeTied(NoteElement anchor, NoteElement end) {
        return Tie.between(anchor, end) != null && anchor.getPitch() == end.getPitch();
    }

    /**
     * What a beam toggle over a selection does: add a beam, remove one, or nothing.
     * {@link #beamToggle} answers it.
     */
    public sealed interface BeamToggle {

        /**
         * The selection is not joined by one beam, and a beam may join it.
         *
         * @param beam the beam {@link Beam#spanning} built over the selection, not yet added to
         *             its line
         */
        record Add(Beam beam) implements BeamToggle {}

        /**
         * One beam already joins the selection's first and last non-grace elements.
         *
         * @param beam that beam, on its line
         */
        record Remove(Beam beam) implements BeamToggle {}

        /** The selection can be neither beamed nor unbeamed. */
        record Ineligible() implements BeamToggle {}
    }

    /**
     * Returns what a beam toggle over {@code selection} does.
     * <p>
     * A leading or trailing grace note is left outside: the toggle acts on the range from the
     * selection's first non-grace element to its last. Whether that range may hold a
     * beam, the tie rule included, is {@link Beam#spanning}'s question alone. On top of its
     * answer this adds only what a toggle needs: a range one beam already joins is a removal.
     *
     * @param selection the elements to toggle the beam over
     * @return {@link BeamToggle.Remove} with the beam that holds both the first and the last
     *     non-grace element, when {@link Beam#spanning} accepts that range; otherwise
     *     {@link BeamToggle.Add} with the beam {@link Beam#spanning} built;
     *     {@link BeamToggle.Ineligible} when {@link Beam#spanning} refuses the range or the
     *     selection holds only grace notes
     */
    public static BeamToggle beamToggle(ElementSelection selection) {
        var line = selection.line();
        var joined = line.nonGraceRange(selection.range());

        if (joined == null || !(Beam.spanning(line, joined) instanceof Spanning.Spanned<Beam>(var beam, var _))) {
            return new BeamToggle.Ineligible();
        }

        // Looked up rather than asked via LineSpans.sameBeamAt: the removal needs the beam
        // itself.
        var spans = line.spanRegistry();
        var beginBeam = spans.findBeamAt(joined.begin());

        //noinspection ObjectEquality
        if (beginBeam != null && beginBeam == spans.findBeamAt(joined.end())) {
            return new BeamToggle.Remove(beginBeam);
        }

        return new BeamToggle.Add(beam);
    }

    /**
     * Returns whether a beam toggle over {@code selection} would change the line.
     *
     * @param selection the elements to toggle the beam over
     * @return {@code false} exactly when {@link #beamToggle} answers
     *     {@link BeamToggle.Ineligible}
     */
    public static boolean canToggleBeaming(ElementSelection selection) {
        return !(beamToggle(selection) instanceof BeamToggle.Ineligible);
    }

    /**
     * Returns whether the range can toggle a tie.
     *
     * <p>A tie joins two notes of the same pitch, which may be adjacent or separated by a
     * single non-duration element such as a barline or repeat.
     *
     * <p>A <b>single</b> selected element is also accepted, but only when it sits at a line
     * edge and the adjacent line offers a same-pitch note at its own edge — the cross-line
     * tie. That case shares none of the same-line checks below: there is no second
     * selected element to pair with, and the partner is found by
     * {@link #boundaryTieAt} rather than by looking inside {@code range}.
     */
    public static boolean canToggleTie(ElementSelection range) {
        var bounds = range.range();
        var selectionSize = bounds.size();

        // A single boundary element ties to the adjacent line's matching edge note, not to
        // anything else in range.line() — none of the same-line checks below apply.
        if (selectionSize == TIE_SELECTION_SIZE_BOUNDARY) {
            return boundaryTieAt(range.line(), bounds.begin()) != null;
        }

        if (selectionSize != TIE_SELECTION_SIZE_WITHOUT_SEPARATOR
            && selectionSize != TIE_SELECTION_SIZE_WITH_SEPARATOR) {
            return false;
        }

        var line = range.line();
        var begin = bounds.begin();
        var end = bounds.end();

        if (!(line.getElement(begin) instanceof NoteElement anchorNote)
            || !(line.getElement(end) instanceof NoteElement endNote)
            || !canBeTied(anchorNote, endNote)) {
            return false;
        }

        if (selectionSize == TIE_SELECTION_SIZE_WITH_SEPARATOR
            && !Tie.isLegalSeparator(line.getElement(begin + 1).getType())) {
            return false;
        }

        // Tied notes are never beamed together, so a tie is not added over two notes one beam
        // holds. A removal is never blocked by a beam.
        return !shouldConnectTieSelection(line, bounds) || Tie.beamHoldingBoth(line, begin) == null;
    }

    /**
     * Returns the range a tie toggle on the element at {@code index} would act on, or
     * {@code null} when no pair qualifies.
     *
     * <p>This proposes shapes and lets {@link #canToggleTie} judge them; it restates no tie
     * rule of its own. The shapes, in order: the adjacent pair ending at {@code index}, the
     * same pair with one element between them (a barline, repeat or breath mark),
     * and finally {@code index} alone, read as a cross-line tie.
     *
     * <p>Order is what keeps the separator rule unstated here. A barline at {@code index - 1}
     * is not a pitched note, so the adjacent pair fails inside {@code canToggleTie} and the
     * separator shape is reached only when the adjacent one is genuinely ineligible; the
     * separator shape's own size-3 branch then decides whether what sits between is legal. A
     * wrong-pitch <em>note</em> at {@code index - 1} fails both, because a note is not a legal
     * separator — a refusal, which is what the user should get.
     *
     * <p>There is no widen-or-break branch, unlike the beam side: ties never coalesce, since a
     * tie always joins a note to the next one ({@link NoteElement#tie()}), so the toggle is a
     * plain add or remove of the tie over the pair.
     *
     * <p>Grace notes are not transparent here, though they are for beams. A grace note is not a
     * legal separator, so the walk that derives a tie's end stops at one, and a grace note is
     * refused at either endpoint, so nothing walks past one.
     *
     * <p>The single-element shape is accepted only when the boundary tie it would create runs
     * <em>backward</em> into {@code index} — {@link #boundaryTieAt} returns the forward
     * candidate when {@code index} is the line's tie-exit element, and tying forward would join
     * the note to one on the next line that the user has not reached yet.
     *
     * @param line  the line holding the element
     * @param index a position in {@code line}; one outside it has no candidate
     * @return the first shape {@link #canToggleTie} admits — the adjacent pair, the pair with
     *     one element between, or the single element when its boundary tie ends on it — or
     *     {@code null} when {@code index} is outside {@code line} or no shape is admitted
     */
    public static @Nullable ElementSelection tieCandidateWithPredecessor(Line line, int index) {
        if (!line.hasIndex(index)) {
            return null;
        }

        var adjacent = tieCandidateEndingAt(line, index, TIE_SELECTION_SIZE_WITHOUT_SEPARATOR);

        if (adjacent != null) {
            return adjacent;
        }

        var separated = tieCandidateEndingAt(line, index, TIE_SELECTION_SIZE_WITH_SEPARATOR);

        if (separated != null) {
            return separated;
        }

        var boundaryTie = boundaryTieAt(line, index);

        //noinspection ObjectEquality
        if (boundaryTie != null && boundaryTie.end() == line.getElement(index)) {
            return ElementSelection.single(line, index);
        }

        return null;
    }

    /**
     * The {@code size}-element range ending at {@code index}, or {@code null} when it runs off
     * the front of the line or {@link #canToggleTie} refuses it.
     *
     * <p>A negative begin index names no element of the line, so a shape running off the front
     * is skipped before it is built: {@link #canToggleTie} would fetch that element and fail.
     */
    private static @Nullable ElementSelection tieCandidateEndingAt(Line line, int index, int size) {
        var begin = index - (size - 1);

        if (begin < 0) {
            return null;
        }

        var candidate = new ElementSelection(line, new ElementRange(begin, index));

        return canToggleTie(candidate) ? candidate : null;
    }

    /**
     * Returns the index of the element a glissando toggle would act on, or {@code null} when the
     * range is ineligible.
     *
     * <p>A glissando lives on its <b>source</b> note and connects forward to the element that
     * immediately follows it, so the source is not always inside the range: a single selected
     * element is read as the glissando's <em>target</em>, whose source is the element before it,
     * while a selected pair is read as source and target in order. Any other size has no
     * unambiguous source.
     *
     * <p>Removing an existing glissando is never blocked. The {@code hasGlissando()} test
     * therefore comes first, ahead of the bounds, type and pitch gates, which apply to the add
     * branch only: a glissando that reached the model through a MusicXML import may sit on a
     * note whose follower is a rest or a grace note, and gating removal on those rules would
     * leave it impossible to toggle off.
     *
     * <p>Adding one requires two adjacent {@link songscribe.dom.ElementType#isPitchedNote()
     * pitched notes} — which excludes rests, barlines, repeats, breath marks and grace notes —
     * of different pitch, as {@link Line#isSamePitchAsFollower} defines that condition.
     *
     * <p>A glissando lives on its <em>source</em> and connects forward, so the source is resolved
     * from the selection's size first: a single-element selection takes {@code begin() - 1} (the
     * element before it, which the glissando would run from), a two-element selection takes
     * {@code begin()} itself, and any other size yields null.
     *
     * <p>With the source resolved, a source that already {@code hasGlissando()} is returned
     * immediately — removal needs no further gate. Otherwise the addition gate applies: the source
     * must be in range with a successor, both it and its successor must be pitched notes, and
     * {@code isSamePitchAsFollower(source)} must be false. Failing any of those yields null.
     */
    public static @Nullable Integer glissandoSourceIndex(ElementSelection range) {
        var line = range.line();
        var bounds = range.range();
        int sourceIndex;

        if (bounds.size() == GLISSANDO_SELECTION_SIZE_SOURCE_ONLY) {
            sourceIndex = bounds.begin() - 1;
        } else if (bounds.size() == GLISSANDO_SELECTION_SIZE_WITH_TARGET) {
            sourceIndex = bounds.begin();
        } else {
            return null;
        }

        if (sourceIndex < 0) {
            return null;
        }

        var sourceElement = line.getElement(sourceIndex);

        // Removal answers before any add-only gate runs.
        if (sourceElement instanceof NoteElement sourceNote && sourceNote.hasGlissando()) {
            return sourceIndex;
        }

        if (sourceIndex + 1 >= line.elementCount()) {
            return null;
        }

        if (!sourceElement.getType().isPitchedNote()
            || !line.getElement(sourceIndex + 1).getType().isPitchedNote()) {
            return null;
        }

        return line.isSamePitchAsFollower(sourceIndex) ? null : sourceIndex;
    }

    /**
     * Returns whether the range can toggle a glissando.
     *
     * @see #glissandoSourceIndex
     */
    public static boolean canToggleGlissando(ElementSelection range) {
        return glissandoSourceIndex(range) != null;
    }

    /**
     * Returns every index in the range whose element may carry a fall — that is, every
     * {@link songscribe.dom.ElementType#isPitchedNote() pitched note}, which excludes rests,
     * barlines, repeats, breath marks and grace notes, plus any element that already carries a
     * fall regardless of type: a fall that reached the model through a MusicXML import may sit
     * on a non-pitched element the add rules would never have allowed one on, and gating removal
     * on those rules would leave it impossible to toggle off (mirrors
     * {@link #glissandoSourceIndex}'s removal bypass).
     *
     * <p>The one statement of which notes a fall toggle applies to, so the predicate that
     * offers the toggle and the kernel that performs it cannot disagree.
     */
    public static int[] fallIndices(ElementSelection range) {
        var line = range.line();
        var bounds = range.range();

        return IntStream.rangeClosed(bounds.begin(), bounds.end())
            .filter(i -> line.getElement(i) instanceof NoteElement note
                && (note.hasFall() || note.getType().isPitchedNote()))
            .toArray();
    }

    /**
     * Returns whether the range can toggle a fall — that is, whether it holds any note a fall
     * could hang off.
     *
     * @see #fallIndices
     */
    public static boolean canToggleFall(ElementSelection range) {
        return fallIndices(range).length > 0;
    }

    /**
     * How a selection's non-grace range meets the tuplets already on its line.
     * {@link #tupletOverlap} answers it.
     */
    public sealed interface TupletOverlap {

        /** No element of the range belongs to a tuplet. */
        record NoTuplet() implements TupletOverlap {}

        /**
         * The range is exactly one tuplet's range.
         *
         * @param tuplet that tuplet, on its line
         */
        record Covers(Tuplet tuplet) implements TupletOverlap {}

        /**
         * Some element of the range belongs to a tuplet, and the range is not exactly that
         * tuplet's range: it lies strictly inside the tuplet, crosses one of its ends, or reaches
         * into a second tuplet.
         *
         * @param tuplet the tuplet of the first element of the range that belongs to one
         */
        record PartlyInside(Tuplet tuplet) implements TupletOverlap {}
    }

    /**
     * Returns where a tuplet over {@code selection} would sit: its range from the first non-grace
     * element to the last, since a grace note never joins a tuplet, with the beat in
     * effect there.
     *
     * @param selection the elements a tuplet would group
     * @return the site, or {@code null} when every element of {@code selection} is a grace note
     */
    public static Tuplet.@Nullable Site tupletSite(ElementSelection selection) {
        var line = selection.line();
        var grouped = line.nonGraceRange(selection.range());

        return (grouped != null) ? Tuplet.Site.at(line, grouped) : null;
    }

    /**
     * Returns how {@code range} of {@code line} meets the tuplets on {@code line}. A grace note
     * inside the range is passed over: it rides along inside a tuplet without joining it.
     *
     * @param line  the line the range is on
     * @param range a range of {@code line} whose first and last elements are not grace notes
     * @return {@link TupletOverlap.Covers} when one tuplet's range equals {@code range};
     *     {@link TupletOverlap.NoTuplet} when no non-grace element of {@code range} belongs to
     *     a tuplet; {@link TupletOverlap.PartlyInside} otherwise
     */
    public static TupletOverlap tupletOverlap(Line line, ElementRange range) {
        var spans = line.spanRegistry();

        for (var i = range.begin(); i <= range.end(); i++) {
            if (line.getElement(i).getType().isGraceNote()) {
                continue;
            }

            var tuplet = spans.findTupletAt(i);

            if (tuplet != null) {
                return tuplet.range().equals(range)
                    ? new TupletOverlap.Covers(tuplet)
                    : new TupletOverlap.PartlyInside(tuplet);
            }
        }

        return new TupletOverlap.NoTuplet();
    }

    /**
     * Returns what the tuplet menu offers for {@code selection}: the grades a tuplet over it
     * may take, and the grade of the tuplet it already carries.
     * <p>
     * A grade is offered when {@link Tuplet#spanning} accepts it over {@link #tupletSite} under
     * {@link TupletValidator.Strictness#STRICT}, the judgment the edit applies. Rests are
     * welcome inside a tuplet; anything else the range may hold that a tuplet may not (a
     * barline, a breath mark, a fermata) is the factory's to refuse. A selection that holds
     * part of a tuplet offers no grade: a tuplet over it would destroy the one it reaches into.
     *
     * @param selection the elements a tuplet would group
     * @return {@link TupletToggleInfo#NONE} when {@code selection} holds only grace notes;
     *     otherwise the grades {@link Tuplet#spanning} accepts, empty when
     *     {@link #tupletOverlap} answers {@link TupletOverlap.PartlyInside}, together with the
     *     grade of the tuplet {@link #tupletOverlap} names, or {@code null} when it answers
     *     {@link TupletOverlap.NoTuplet}
     */
    public static TupletToggleInfo canToggleTuplet(ElementSelection selection) {
        var site = tupletSite(selection);

        if (site == null) {
            return TupletToggleInfo.NONE;
        }

        return switch (tupletOverlap(site.line(), site.range())) {
            case TupletOverlap.NoTuplet() -> new TupletToggleInfo(validGradesAt(site), null);
            case TupletOverlap.Covers(var tuplet) -> new TupletToggleInfo(validGradesAt(site), tuplet.getGrade());
            case TupletOverlap.PartlyInside(var tuplet) -> new TupletToggleInfo(Set.of(), tuplet.getGrade());
        };
    }

    /**
     * Returns the grades {@link Tuplet#spanning} accepts at {@code site}.
     * <p>
     * The candidates come from the model, not from the menu's list of actions: what a tuplet
     * number may be is a fact about tuplets, and a selection query must not change because
     * someone reorders a menu.
     */
    private static Set<TupletGrade> validGradesAt(Tuplet.Site site) {
        var grades = EnumSet.noneOf(TupletGrade.class);

        for (var grade : TupletGrade.values()) {
            if (Tuplet.spanning(site, grade, TupletValidator.Strictness.STRICT) instanceof Spanning.Spanned<Tuplet>) {
                grades.add(grade);
            }
        }

        return grades;
    }

    /**
     * What one hairpin menu item does for a selection, and the wording it shows.
     * {@link #resolveHairpin} answers it.
     * <p>
     * The answer is per menu item, not a joint verdict about both: the crescendo and
     * diminuendo items are resolved separately and may disagree. Two answers mean "not now",
     * and they differ in why: {@link Ineligible} means the selection itself cannot carry a
     * hairpin, whatever else is on the line; {@link Blocked} means another hairpin is the
     * obstacle. Both read "Add …", like {@link Add}; only {@link Extend} reads "Extend …".
     * The undo entry of an applied hairpin is named by the same key as the menu item, so the
     * two read alike: "Extend Crescendo", then "Undo Extend Crescendo".
     */
    public sealed interface HairpinResolution {

        /** @return the hairpin kind the menu item adds */
        Hairpin.Kind kind();

        /**
         * @return the {@code Strings} key of the menu item's text, which also names the undo
         *     entry of an applied hairpin
         */
        default String nameKey() {
            return switch (kind()) {
                case CRESCENDO -> Strings.ACTION_HAIRPIN_CRESCENDO;
                case DIMINUENDO -> Strings.ACTION_HAIRPIN_DIMINUENDO;
            };
        }

        /** @return the {@code Strings} key of the menu item's tooltip */
        default String tooltipKey() {
            return switch (kind()) {
                case CRESCENDO -> Strings.ACTION_HAIRPIN_CRESCENDO_TOOLTIP;
                case DIMINUENDO -> Strings.ACTION_HAIRPIN_DIMINUENDO_TOOLTIP;
            };
        }

        /** An answer the menu item acts on: it adds {@link #hairpin}. */
        sealed interface Applicable extends HairpinResolution {

            /**
             * @return the hairpin {@link Hairpin#spanning} built over the selection, not yet
             *     added to its line; adding it absorbs the same-kind hairpins it reaches
             */
            Hairpin hairpin();

            @Override
            default Hairpin.Kind kind() {
                return hairpin().getKind();
            }
        }

        /**
         * The item reads "Add …", enabled: no same-kind hairpin is in reach.
         *
         * @param hairpin the hairpin over the selection
         */
        record Add(Hairpin hairpin) implements Applicable {}

        /**
         * The item reads "Extend …", enabled: the hairpin absorbs at least one same-kind
         * hairpin, none of which already covers the selection.
         *
         * @param hairpin the hairpin over the selection widened over the hairpins it absorbs
         */
        record Extend(Hairpin hairpin) implements Applicable {

            @Override
            public String nameKey() {
                return switch (kind()) {
                    case CRESCENDO -> Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND;
                    case DIMINUENDO -> Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND;
                };
            }

            @Override
            public String tooltipKey() {
                return switch (kind()) {
                    case CRESCENDO -> Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND_TOOLTIP;
                    case DIMINUENDO -> Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND_TOOLTIP;
                };
            }
        }

        /**
         * The item reads "Add …", disabled: the selection cannot carry a hairpin of
         * {@code kind}.
         *
         * @param kind the hairpin kind the menu item adds
         */
        record Ineligible(Hairpin.Kind kind) implements HairpinResolution {}

        /**
         * The item reads "Add …", disabled: a hairpin already on the line is in the way.
         *
         * @param kind the hairpin kind the menu item adds
         */
        record Blocked(Hairpin.Kind kind) implements HairpinResolution {}
    }

    /**
     * Resolves what the {@code kind} hairpin menu item does for {@code selection}.
     * <p>
     * The resolution is type-aware, and each menu item asks for its own: with a crescendo
     * already on the notes just before the selection, the crescendo item extends while the
     * diminuendo item adds a back-to-back wedge. One shared verdict could not say both.
     * <p>
     * {@link Hairpin#spanning} is the one judge of whether the selection holds a hairpin; this
     * method adds only what the menu needs on top of its answer. A same-kind hairpin that
     * already covers the whole selection leaves nothing to extend, so the item is blocked. That
     * is tested against the selection, never against the range the hairpin was widened to: a
     * hairpin absorbed on a later widening pass covers the widened range by construction, and
     * would block every extension that reached it.
     * <p>
     * It is recomputed on every call. A cache keyed on the selection would go stale the moment
     * a hairpin is added or undone without the selection moving.
     * <p>
     * <b>Worked examples.</b>
     * <ul>
     *   <li>Crescendo on {@code [0, 4]}, selection {@code [4, 8]}: {@code CRESCENDO} resolves
     *       to {@link HairpinResolution.Extend} over {@code [0, 8]}, {@code DIMINUENDO} to
     *       {@link HairpinResolution.Add} over {@code [4, 8]}.</li>
     *   <li>Crescendo on {@code [0, 4]}, selection {@code [2, 8]}: {@code DIMINUENDO} is
     *       {@link HairpinResolution.Blocked}, since the overlap is more than the one shared
     *       endpoint.</li>
     *   <li>One note plus the rest after it, no hairpin nearby:
     *       {@link HairpinResolution.Ineligible}, since a hairpin never ends on a rest.</li>
     * </ul>
     *
     * @param selection the elements the hairpin would cover
     * @param kind      the hairpin kind the menu item adds
     * @return when {@link Hairpin#spanning} answers {@link Spanning.Spanned}:
     *     {@link HairpinResolution.Add} with its hairpin when it absorbs nothing,
     *     {@link HairpinResolution.Blocked} when a hairpin it absorbs already holds the whole
     *     selection, and {@link HairpinResolution.Extend} with its hairpin otherwise; when it
     *     answers {@link Spanning.Refused}: {@link HairpinResolution.Ineligible} for
     *     {@link Spanning.Cause#RANGE} and {@link HairpinResolution.Blocked} for
     *     {@link Spanning.Cause#OTHER_SPAN}
     */
    public static HairpinResolution resolveHairpin(ElementSelection selection, Hairpin.Kind kind) {
        var range = selection.range();

        return switch (Hairpin.spanning(selection.line(), kind, range)) {
            case Spanning.Spanned<Hairpin>(var hairpin, var absorbed) -> {
                if (absorbed.isEmpty()) {
                    yield new HairpinResolution.Add(hairpin);
                }

                if (absorbed.stream().anyMatch(existing -> existing.range().contains(range))) {
                    yield new HairpinResolution.Blocked(kind);
                }

                yield new HairpinResolution.Extend(hairpin);
            }

            case Spanning.Refused<Hairpin> refused -> switch (refused.cause()) {
                case RANGE -> new HairpinResolution.Ineligible(kind);
                case OTHER_SPAN -> new HairpinResolution.Blocked(kind);
            };
        };
    }

    /**
     * Returns the first/second ending a selection would make.
     * <p>
     * The ending is judged over the selection widened two ways. A multi-element selection never
     * reaches the line's terminal, since the selection coordinator clamps it out, so a
     * selection that ends just before the terminal is extended over it, unless its last element
     * already ends an ending ({@link songscribe.dom.ElementType#isEndingTerminal}). And the
     * anchor moves to where a bracket anchored at the selection's first element opens
     * ({@link Ending#openingElementIndex}), so the anchor this sets and the element layout later
     * draws from cannot drift apart.
     *
     * @param selection the elements the ending would cover
     * @return what {@link Ending#spanning} answers over the widened range: an ending that
     *     overlaps one already on the line, or whose split closes no open repeated section, is
     *     refused there
     */
    public static Spanning<Ending> firstSecondEnding(ElementSelection selection) {
        var line = selection.line();
        var bounds = selection.range();
        var end = bounds.end();
        var extendedEnd = end + 1;

        if (!line.getElement(end).getType().isEndingTerminal() && line.isTerminalAt(extendedEnd)) {
            end = extendedEnd;
        }

        var anchorIndex = Ending.openingElementIndex(line, bounds.begin());

        return Ending.spanning(line, new ElementRange(anchorIndex, end));
    }

    /**
     * Returns whether the range can toggle trill.
     */
    public static boolean canToggleTrill(ElementSelection range) {
        return selectedElements(range).stream()
            .anyMatch(element -> element.getType().isPitchedNote());
    }

    private static List<StaffElement> selectedElements(ElementSelection range) {
        return range.line().getElements(range.range());
    }

    /**
     * Returns whether the stem direction can be modified, either by flipping it
     * or by restoring it to automatic. Only notes that actually carry a stem
     * qualify — rests and whole notes have none.
     */
    public static boolean canModifyStemDirection(ElementSelection range) {
        return selectedElements(range).stream()
            .anyMatch(element -> element.getType().isNoteWithStem());
    }


    /**
     * Returns whether a new tie should connect the span endpoints (add mode), as opposed
     * to the span already being covered by an existing tie (remove mode).
     */
    private static boolean shouldConnectTieSelection(Line line, ElementRange range) {
        return !(line.getElement(range.begin()) instanceof NoteElement first
            && line.getElement(range.end()) instanceof NoteElement second
            && first.isTiedTo(second));
    }
}
