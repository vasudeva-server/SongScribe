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
 * Every answer is a pure function of {@code (line, begin, end)}; none reads or writes what is
 * currently selected.
 */
public final class RangeQueries {

    private static final int TIE_SELECTION_SIZE_BOUNDARY = 1;

    private static final int TIE_SELECTION_SIZE_WITHOUT_SEPARATOR = 2;

    private static final int TIE_SELECTION_SIZE_WITH_SEPARATOR = 3;

    private static final int GLISSANDO_SELECTION_SIZE_WITH_TARGET = 2;

    private static final int GLISSANDO_SELECTION_SIZE_SOURCE_ONLY = 1;

    private RangeQueries() {}

    /** A tie candidate spanning a line boundary; {@code anchor} precedes {@code end} in document order. */
    public record BoundaryTie(NoteElement anchor, NoteElement end) {}

    /**
     * @return {@code null} when no tie across the line boundary is possible at {@code index}
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

    /** The one statement of the tie rule, shared by {@link #canToggleTie} and {@link #boundaryTieAt}. */
    private static boolean canBeTied(NoteElement anchor, NoteElement end) {
        return Tie.between(anchor, end) != null && anchor.getPitch() == end.getPitch();
    }

    public sealed interface BeamToggle {

        /** @param beam built by {@link Beam#spanning}, not yet added to its line */
        record Add(Beam beam) implements BeamToggle {}

        record Remove(Beam beam) implements BeamToggle {}

        record Ineligible() implements BeamToggle {}
    }

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

    public static boolean canToggleBeaming(ElementSelection selection) {
        return !(beamToggle(selection) instanceof BeamToggle.Ineligible);
    }

    /**
     * A tie joins two notes of the same pitch, adjacent or separated by a single non-duration
     * element such as a barline or repeat. A single selected element is accepted only as a
     * cross-line tie: at a line edge, with a same-pitch note at the adjacent line's edge.
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
     * {@code null} when no pair qualifies. Grace notes are not transparent here, unlike for
     * beams: they are refused at either endpoint and are not a legal separator. A single element
     * is accepted only when the boundary tie runs backward into {@code index}, never forward to
     * a note on the next line.
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

    /** {@code null} when the range runs off the front of the line or {@link #canToggleTie} refuses it. */
    private static @Nullable ElementSelection tieCandidateEndingAt(Line line, int index, int size) {
        var begin = index - (size - 1);

        if (begin < 0) {
            return null;
        }

        var candidate = new ElementSelection(line, new ElementRange(begin, index));

        return canToggleTie(candidate) ? candidate : null;
    }

    /**
     * {@code null} when the range is ineligible. A single selected element is read as the
     * glissando's target (its source is the element before it), a pair as source and target in
     * order. Removing an existing glissando is never blocked, because a MusicXML import can
     * place one where adding is refused.
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

    public static boolean canToggleGlissando(ElementSelection range) {
        return glissandoSourceIndex(range) != null;
    }

    /**
     * Includes any element that already carries a fall, whatever its type, so one from a
     * MusicXML import can still be toggled off. The one statement of which notes a fall applies
     * to, shared by the predicate that offers the toggle and the kernel that performs it.
     */
    public static int[] fallIndices(ElementSelection range) {
        var line = range.line();
        var bounds = range.range();

        return IntStream.rangeClosed(bounds.begin(), bounds.end())
            .filter(i -> line.getElement(i) instanceof NoteElement note
                && (note.hasFall() || note.getType().isPitchedNote()))
            .toArray();
    }

    public static boolean canToggleFall(ElementSelection range) {
        return fallIndices(range).length > 0;
    }

    public sealed interface TupletOverlap {

        record NoTuplet() implements TupletOverlap {}

        record Covers(Tuplet tuplet) implements TupletOverlap {}

        /** @param tuplet the tuplet of the first range element that belongs to one */
        record PartlyInside(Tuplet tuplet) implements TupletOverlap {}
    }

    /**
     * A grace note never joins a tuplet, so the site spans the first to the last non-grace element.
     *
     * @return {@code null} when every element of {@code selection} is a grace note
     */
    public static Tuplet.@Nullable Site tupletSite(ElementSelection selection) {
        var line = selection.line();
        var grouped = line.nonGraceRange(selection.range());

        return (grouped != null) ? Tuplet.Site.at(line, grouped) : null;
    }

    /** @param range a range of {@code line} whose first and last elements are not grace notes */
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
     * A grade is offered only when {@link Tuplet#spanning} accepts it under
     * {@link TupletValidator.Strictness#STRICT}, the judgment the edit applies. A selection
     * holding part of a tuplet offers none, since a new tuplet would destroy it.
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
     * What one hairpin menu item does for a selection; the crescendo and diminuendo items are
     * resolved separately and may disagree. {@link Ineligible}: the selection itself cannot
     * carry a hairpin. {@link Blocked}: another hairpin is in the way.
     */
    public sealed interface HairpinResolution {

        Hairpin.Kind kind();

        /** Also names the undo entry of an applied hairpin. */
        default String nameKey() {
            return switch (kind()) {
                case CRESCENDO -> Strings.ACTION_HAIRPIN_CRESCENDO;
                case DIMINUENDO -> Strings.ACTION_HAIRPIN_DIMINUENDO;
            };
        }

        default String tooltipKey() {
            return switch (kind()) {
                case CRESCENDO -> Strings.ACTION_HAIRPIN_CRESCENDO_TOOLTIP;
                case DIMINUENDO -> Strings.ACTION_HAIRPIN_DIMINUENDO_TOOLTIP;
            };
        }

        sealed interface Applicable extends HairpinResolution {

            /** Not yet added to its line; adding it absorbs the same-kind hairpins it reaches. */
            Hairpin hairpin();

            @Override
            default Hairpin.Kind kind() {
                return hairpin().getKind();
            }
        }

        record Add(Hairpin hairpin) implements Applicable {}

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

        record Ineligible(Hairpin.Kind kind) implements HairpinResolution {}

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
     * A selection ending just before the terminal is extended over it, since a multi-element
     * selection never reaches it. The anchor moves to {@link Ending#openingElementIndex} so it
     * cannot drift from where layout draws the bracket.
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

    public static boolean canToggleTrill(ElementSelection range) {
        return selectedElements(range).stream()
            .anyMatch(element -> element.getType().isPitchedNote());
    }

    private static List<StaffElement> selectedElements(ElementSelection range) {
        return range.line().getElements(range.range());
    }

    /** Only notes that carry a stem qualify; rests and whole notes have none. */
    public static boolean canModifyStemDirection(ElementSelection range) {
        return selectedElements(range).stream()
            .anyMatch(element -> element.getType().isNoteWithStem());
    }

    private static boolean shouldConnectTieSelection(Line line, ElementRange range) {
        return !(line.getElement(range.begin()) instanceof NoteElement first
            && line.getElement(range.end()) instanceof NoteElement second
            && first.isTiedTo(second));
    }
}
