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

package songscribe.dom;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.error.RuntimeError;

/**
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 *
 * <p>
 * An ending over {@code [anchor, end]} of a line is well formed when every invariant below
 * holds but the last two ({@link #illFormedReason} judges them over one line's elements). The
 * section rule reads the song across lines, and the last invariant holds between every two
 * endings on a line. Every attached ending meets all of them. {@link #spanning} is the one
 * way to create an ending: the editor calls it, and each file reader reaches it through
 * {@link EndingLoadPass}. {@link #outcomeFor} removes an ending that an edit makes ill formed
 * in the projection, and {@link EndingSectionRule} removes an ending whose split an edit
 * leaves closing no open repeat section, so no edit leaves either state in the song. No edit
 * can make two endings overlap, so only {@link #spanning} refuses an overlap.
 * <p>
 * Two endings may sit back to back on a line. When no repeat opens between them, the walk
 * back from the later ending's split crosses the earlier ending and reaches the earlier split,
 * which closes the section, so the section rule refuses the later ending.
 *
 * @invariant the anchor is a note or a rest ({@link ElementType#isDuration()}) or a
 *            {@link ElementType#SINGLE_BARLINE}; any other bar refuses the ending
 * @invariant in front of a note or rest anchor, the first element that is not
 *            {@link ElementType#isNonContentElement() non-content} is a
 *            {@code SINGLE_BARLINE}, a note or a rest, or there is none on the line; any other
 *            bar there closes or opens a repeated section, and an ending does not start
 *            immediately after one
 * @invariant a bar strictly inside the range has a type that
 *            {@link ElementType#closesRepeat()}; the first such bar is the split, where the
 *            two brackets part, and it is a {@link ElementType#REPEAT_RIGHT}; a
 *            {@link ElementType#REPEAT_LEFT_RIGHT} there is the split all the same, and makes
 *            the ending ill formed
 * @invariant {@link #isValidEnd} accepts the end's type
 * @invariant when the ending has an end bar ({@link #getEndBar}), the first element after it
 *            on the line that is not {@link ElementType#isNonContentElement() non-content} is
 *            not a bar; the first element of the next line is not read
 * @invariant each bracket ({@link #firstBracket}, {@link #secondBracket}) holds no bar and at
 *            least one note or rest; a grace note, a slide or a breath mark is not content
 * @invariant the split closes an open repeat section: {@link Song#repeatSectionStart} at the
 *            split is not {@code null} ({@link #closesOpenSection}), which reads across lines
 * @invariant the ranges of two endings on a line do not overlap
 */
public final class Ending extends Span<StaffElement> {

    /**
     * The answer {@link #spanning} gives for a range: the ending over it, or the reason there is
     * none.
     */
    public sealed interface Spanning permits Spanning.Spanned, Spanning.Refused {

        /**
         * The range holds an ending.
         *
         * @param ending the ending over the range, built but not added to its line
         */
        record Spanned(Ending ending) implements Spanning {}

        /**
         * The range holds no ending.
         *
         * @param reasonKey the {@code Strings} key of the reason, as the user is told it
         */
        record Refused(String reasonKey) implements Spanning {}
    }

    /**
     * Creates an ending bracket over two elements the caller has already checked span a
     * well-formed ending.
     *
     * @param anchorElement The first element of the ending
     * @param endElement    The last element of the ending
     */
    private Ending(StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
    }

    /**
     * Returns an ending over {@code span} of {@code line} when the class invariant allows one
     * there, or the reason it does not. This is the one way to create an ending. The editor
     * calls it, and so does {@link EndingLoadPass} for the endings a file reader found, so a
     * range holds an ending by the same rule whichever route it arrives by.
     * <p>
     * It judges, in order: the range over {@code line}'s elements ({@link #illFormedReason});
     * whether the range overlaps an ending already on {@code line}; and the section rule
     * ({@link #closesOpenSection}), which reads the live song across lines. The section rule is
     * not part of {@link #illFormedReason}, because {@link #outcomeFor} judges a one-line
     * projection of a pending change, which holds no other line.
     * <p>
     * The ending is built, not added: {@code line}'s span registry is unchanged.
     *
     * @param line the line the ending would be on, one of its song's lines
     * @param span the range the ending would cover; both of its indices are indices of
     *     {@code line}
     * @return {@link Spanning.Spanned} with an ending anchored on the element at
     *     {@code span.begin()} and ending on the element at {@code span.end()}; or
     *     {@link Spanning.Refused} with the reason {@link #illFormedReason} answers when the range
     *     is ill formed, {@link Strings#DIALOG_REPORT_REASON_ENDING_NOT_VALID} when it overlaps
     *     an ending already on {@code line}, or
     *     {@link Strings#DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION} when its split closes no
     *     open repeat section
     * @throws IllegalStateException if the range is well formed and overlaps no ending, and
     *     {@code line} is not one of its song's lines
     */
    public static Spanning spanning(Line line, ElementRange span) {
        var elements = line.getElements();
        var illFormed = illFormedReason(elements, span);

        if (illFormed != null) {
            return new Spanning.Refused(illFormed);
        }

        var overlapsAny = line.spanRegistry().findEndings().stream()
            .anyMatch(other -> other.range().overlaps(span));

        if (overlapsAny) {
            return new Spanning.Refused(Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID);
        }

        if (!closesOpenSection(line, splitIndexOf(elements, span).orElseThrow())) {
            return new Spanning.Refused(Strings.DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION);
        }

        return new Spanning.Spanned(new Ending(elements.get(span.begin()), elements.get(span.end())));
    }

    /**
     * Whether the split at {@code splitIndex} of {@code line} closes an open repeat section, as
     * the class invariant requires: {@link Song#repeatSectionStart} at the split is not
     * {@code null}. This is the one statement of the section rule; {@link #spanning} and
     * {@link EndingSectionRule} both apply it.
     *
     * @param line       a line of its song
     * @param splitIndex the index of an ending's split in {@code line}
     * @return {@code true} when a repeat section is open just before the split, however many
     *     lines back it opened
     * @throws IllegalStateException if {@code line} is not one of its song's lines
     */
    static boolean closesOpenSection(Line line, int splitIndex) {
        return line.getSong().repeatSectionStart(new ElementLocation(line.index(), splitIndex)) != null;
    }

    /**
     * Which rule of the class invariant an ending over {@code span} of {@code elements} breaks,
     * judged over those elements alone: every invariant but the section rule and the overlap
     * rule, which read beyond one ending on one line.
     *
     * @param elements a line's elements, as they are or as a pending change projects them
     * @param span     the range the ending covers in {@code elements}
     * @return {@code null} when the ending is well formed;
     *     {@link Strings#DIALOG_REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT} when its split is
     *     not a {@link ElementType#REPEAT_RIGHT}; {@link Strings#DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END}
     *     when a bar follows its end bar; otherwise, for every other broken rule,
     *     {@link Strings#DIALOG_REPORT_REASON_ENDING_NOT_VALID}
     */
    private static @Nullable String illFormedReason(List<StaffElement> elements, ElementRange span) {
        var anchorIndex = span.begin();
        var endIndex = span.end();
        var anchorType = elements.get(anchorIndex).getType();

        if (!isAnchorType(anchorType) || !hasOpeningPredecessor(elements, anchorIndex)) {
            return Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID;
        }

        var split = splitIndexOf(elements, span);

        if (split.isEmpty()) {
            return Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID;
        }

        var splitIndex = split.getAsInt();

        if (elements.get(splitIndex).getType() != ElementType.REPEAT_RIGHT) {
            return Strings.DIALOG_REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT;
        }

        var endType = elements.get(endIndex).getType();

        if (!isValidEnd(endType)) {
            return Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID;
        }

        var first = firstBracketOf(anchorType, anchorIndex, splitIndex);
        var second = secondBracketOf(splitIndex, endType, endIndex);
        var bracketsWellFormed = first != null && second != null
            && isWellFormedBracket(elements, first) && isWellFormedBracket(elements, second);

        if (!bracketsWellFormed) {
            return Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID;
        }

        return barFollowsEndBar(elements, endIndex)
            ? Strings.DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END
            : null;
    }

    /**
     * Whether a bar follows the end bar of an ending ending at {@code endIndex} of
     * {@code elements}: the first element after the end bar that is not
     * {@link ElementType#isNonContentElement() non-content} is a bar. The walk stops at the end
     * of {@code elements}, so it never reads the next line.
     *
     * @return {@code false} when the ending has no end bar, or nothing but non-content follows it
     */
    private static boolean barFollowsEndBar(List<StaffElement> elements, int endIndex) {
        var endBarIndex = endBarIndexOf(elements, endIndex);

        if (endBarIndex == null) {
            return false;
        }

        var following = followingContentIndex(elements, endBarIndex);

        return following != null && elements.get(following).getType().isBar();
    }

    /**
     * Whether an element of {@code type} may anchor an ending: a note, a rest, or a bar a
     * bracket opens on ({@link #opensOnItsOwn}).
     */
    private static boolean isAnchorType(ElementType type) {
        return type.isDuration() || opensOnItsOwn(type);
    }

    /**
     * Whether the anchor at {@code anchorIndex} of {@code elements} has in front of it what the
     * class invariant requires: a bar anchor always does, and a note or rest anchor does when
     * the first content element in front of it is a {@link ElementType#SINGLE_BARLINE}, a note
     * or a rest, or when it has none.
     *
     * @param elements    a line's elements, as they are or as a pending change projects them
     * @param anchorIndex the anchor's index in {@code elements}
     */
    private static boolean hasOpeningPredecessor(List<StaffElement> elements, int anchorIndex) {
        if (!elements.get(anchorIndex).getType().isDuration()) {
            return true;
        }

        var preceding = precedingContentIndex(elements, anchorIndex);

        if (preceding == null) {
            return true;
        }

        var type = elements.get(preceding).getType();

        return type.isDuration() || opensOnItsOwn(type);
    }

    /**
     * The index of the first element in front of {@code index} that is not
     * {@link ElementType#isNonContentElement() non-content}, or {@code null} when there is none.
     */
    private static @Nullable Integer precedingContentIndex(List<StaffElement> elements, int index) {
        for (var i = index - 1; i >= 0; i--) {
            if (!elements.get(i).getType().isNonContentElement()) {
                return i;
            }
        }

        return null;
    }

    /**
     * The index of the first element after {@code index} that is not
     * {@link ElementType#isNonContentElement() non-content}, or {@code null} when there is none.
     */
    private static @Nullable Integer followingContentIndex(List<StaffElement> elements, int index) {
        for (var i = index + 1; i < elements.size(); i++) {
            if (!elements.get(i).getType().isNonContentElement()) {
                return i;
            }
        }

        return null;
    }

    /**
     * Returns the elements of this ending's first bracket: from the anchor when it is a note or
     * a rest, or from the element after it when it is a bar, through the element before the
     * split.
     *
     * @return the first bracket's range in this ending's line, which the class invariant makes
     *     hold at least one note or rest and no bar
     */
    public ElementRange firstBracket() {
        var anchorIndex = requireAnchorElementIndex();

        return requireBracket(
            firstBracketOf(line().getElement(anchorIndex).getType(), anchorIndex, getSplitIndex()));
    }

    /**
     * Returns the elements of this ending's second bracket: from the element after the split
     * through the end when it is a note or a rest, or through the element before it when it is
     * a bar.
     *
     * @return the second bracket's range in this ending's line, which the class invariant makes
     *     hold at least one note or rest and no bar
     */
    public ElementRange secondBracket() {
        var endIndex = requireEndElementIndex();

        return requireBracket(
            secondBracketOf(getSplitIndex(), line().getElement(endIndex).getType(), endIndex));
    }

    /**
     * The first bracket of an ending anchored at {@code anchorIndex} on an element of
     * {@code anchorType} and split at {@code splitIndex}: a note or rest anchor is the
     * bracket's first element, and a bar anchor is not.
     */
    private static @Nullable ElementRange firstBracketOf(
        ElementType anchorType, int anchorIndex, int splitIndex
    ) {
        var before = anchorType.isDuration() ? anchorIndex - 1 : anchorIndex;

        return elementsBetween(before, splitIndex);
    }

    /**
     * The second bracket of an ending split at {@code splitIndex} and ending at
     * {@code endIndex} on an element of {@code endType}: a note or rest end is the bracket's
     * last element, and a bar end is not. Layout draws the bracket over an end note, so the
     * bracket holds it.
     */
    private static @Nullable ElementRange secondBracketOf(
        int splitIndex, ElementType endType, int endIndex
    ) {
        var after = endType.isDuration() ? endIndex + 1 : endIndex;

        return elementsBetween(splitIndex, after);
    }

    /**
     * The indices strictly between {@code before} and {@code after}, or {@code null} when there
     * are none.
     */
    private static @Nullable ElementRange elementsBetween(int before, int after) {
        var begin = before + 1;
        var end = after - 1;

        return begin <= end ? new ElementRange(begin, end) : null;
    }

    private static ElementRange requireBracket(@Nullable ElementRange bracket) {
        if (bracket == null) {
            throw RuntimeError.exit("an attached ending has an empty bracket");
        }

        return bracket;
    }

    /** Whether {@code bracket} of {@code elements} holds no bar and some content. */
    private static boolean isWellFormedBracket(List<StaffElement> elements, ElementRange bracket) {
        var bracketElements = elementsIn(elements, bracket);

        return bracketElements.stream().noneMatch(element -> element.getType().isBar())
            && holdsContent(bracketElements);
    }

    /** Whether {@code elements} hold content: at least one note or rest. */
    private static boolean holdsContent(List<StaffElement> elements) {
        return elements.stream().anyMatch(element -> element.getType().isDuration());
    }

    /** The elements of {@code elements} that {@code range} covers, as a view. */
    private static List<StaffElement> elementsIn(List<StaffElement> elements, ElementRange range) {
        return elements.subList(range.begin(), range.end() + 1);
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Ending(newAnchor, newEnd);
    }

    /**
     * Returns the element index of the bar that separates the first and second sub-spans of
     * this ending — the one {@link #getSplitElement} answers — computed live from the line
     * rather than from a value cached during layout, so it is reliable during MIDI generation.
     *
     * @return the split bar's index in this ending's line
     */
    public int getSplitIndex() {
        return splitIndexOf(line().getElements(), range()).orElseThrow();
    }

    /**
     * Returns the bar that separates the first and second sub-spans of this ending — the
     * element {@link #getSplitIndex} indexes — computed live from the line.
     *
     * @return the first bar between the anchor and the end whose type
     *     {@link ElementType#closesRepeat()}, which the class invariant guarantees exists
     */
    public BarElement getSplitElement() {
        // Only a bar's type closes a repeat, and a bar's type makes its element a BarElement.
        return (BarElement) line().getElement(getSplitIndex());
    }

    /**
     * The index of the first element strictly inside {@code span} of {@code elements} whose
     * type {@link ElementType#closesRepeat() closes a repeat}: the split of an ending over
     * {@code span}.
     *
     * @return the split's index, or empty when no such element stands inside {@code span}
     */
    private static OptionalInt splitIndexOf(List<StaffElement> elements, ElementRange span) {
        return IntStream.range(span.begin() + 1, span.end())
            .filter(i -> elements.get(i).getType().closesRepeat())
            .findFirst();
    }

    /**
     * Returns the index of the element this ending's first bracket opens on: the
     * {@link ElementType#SINGLE_BARLINE} immediately in front of a note or rest anchor, or the
     * anchor itself. The editor anchors a new ending on this element, and layout starts the
     * bracket's left edge at it, so both apply the one rule.
     * <p>
     * Grace notes, slides and breath marks are transparent here, as they are everywhere else
     * an ending reasons about its neighbours. The walk back starts only from a note or a rest,
     * and it stops at the first element that is not transparent: when that element is not a
     * {@code SINGLE_BARLINE}, the anchor opens the bracket itself. An anchor that is a bar is
     * already the opening element, so asking twice gives the same answer as asking once —
     * which is what lets the editor move a selection's anchor onto the bar at creation and
     * layout ask again at paint time without walking back a second bar.
     *
     * @return the opening element's index, or the anchor's own index when the anchor is a bar
     *     or the walk back meets no {@code SINGLE_BARLINE}
     */
    public int getOpeningElementIndex() {
        return openingElementIndex(line(), requireAnchorElementIndex());
    }

    /**
     * Returns the index of the element a bracket anchored at {@code anchorIndex} opens on.
     * <p>
     * The static form exists for the editor, which asks the question while deciding where an
     * ending's anchor belongs — before there is an ending to ask. See
     * {@link #getOpeningElementIndex} for the rule.
     *
     * @param line        the line to search
     * @param anchorIndex the index a bracket is anchored at
     * @return the opening element's index, or {@code anchorIndex} when the element there is
     *     not a note or a rest, or the walk back meets no {@link ElementType#SINGLE_BARLINE}
     */
    public static int openingElementIndex(Line line, int anchorIndex) {
        return openingElementIndex(line.getElements(), anchorIndex);
    }

    /**
     * Returns the index of the element a bracket anchored at {@code anchorIndex} of
     * {@code elements} opens on, by the rule {@link #getOpeningElementIndex} states.
     *
     * @param elements    a line's elements, as they are or as a pending change projects them
     * @param anchorIndex the index a bracket is anchored at
     */
    private static int openingElementIndex(List<StaffElement> elements, int anchorIndex) {
        if (!elements.get(anchorIndex).getType().isDuration()) {
            return anchorIndex;
        }

        var preceding = precedingContentIndex(elements, anchorIndex);

        return preceding != null && opensOnItsOwn(elements.get(preceding).getType())
            ? preceding
            : anchorIndex;
    }

    /**
     * Whether {@code type} is a bar a bracket opens on: a plain barline only. Every other bar
     * begins a repeated section, closes one, or ends the piece at a Fine or the final bar, and
     * no ending opens on one of those.
     */
    private static boolean opensOnItsOwn(ElementType type) {
        return type == ElementType.SINGLE_BARLINE;
    }

    /**
     * Returns the bar this ending's second bracket runs to: the end element when it is a bar,
     * else the element right after the end when that is a bar. With no such bar the bracket
     * stops after the end note. Whether the bracket closes on the bar is
     * {@link #isSecondBracketClosed()}.
     *
     * @return the end bar, or {@code null} when the second bracket stops after a note
     */
    public @Nullable BarElement getEndBar() {
        var line = line();
        var endBarIndex = endBarIndexOf(line.getElements(), requireEndElementIndex());

        // Only a bar's type makes its element a BarElement.
        return endBarIndex == null ? null : (BarElement) line.getElement(endBarIndex);
    }

    /**
     * The index of the end bar of an ending ending at {@code endIndex} of {@code elements}, by
     * the rule {@link #getEndBar} states: the end when it is a bar, else the element right after
     * the end when that is a bar.
     *
     * @param elements a line's elements, as they are or as a pending change projects them
     * @param endIndex the ending's end in {@code elements}
     * @return the end bar's index, or {@code null} when there is none
     */
    private static @Nullable Integer endBarIndexOf(List<StaffElement> elements, int endIndex) {
        if (elements.get(endIndex).getType().isBar()) {
            return endIndex;
        }

        var next = endIndex + 1;

        return next < elements.size() && elements.get(next).getType().isBar() ? next : null;
    }

    /**
     * Whether this ending's second bracket closes with a stroke down to its
     * {@link #getEndBar() end bar}. A single barline leaves the bracket open; every other bar
     * closes it. A bracket with no end bar is open.
     *
     * @return {@code true} when the end bar exists and is not a single barline
     */
    public boolean isSecondBracketClosed() {
        var endBar = getEndBar();
        return endBar != null && endBar.getType() != ElementType.SINGLE_BARLINE;
    }

    /**
     * Whether an ending may end on an element of {@code endType}.
     * <p>
     * The split is a {@link ElementType#REPEAT_RIGHT}, which closes the repeated section, so
     * the second bracket ends wherever a section may end: on a note or a rest, or on a bar that
     * {@link ElementType#isEndingTerminal() ends an ending}.
     *
     * @param endType the type of the element the ending ends on
     * @return {@code true} when {@code endType} is a note, a rest, or an ending terminal
     */
    public static boolean isValidEnd(ElementType endType) {
        return endType.isDuration() || endType.isEndingTerminal();
    }

    /** {@link Displacement#COEXIST}: adding an ending leaves every other ending in place. */
    @Override
    protected Displacement displacement() {
        return Displacement.COEXIST;
    }

    /**
     * Decides what happens to this ending when {@code change} lands. It removes the ending
     * exactly when the ending is not well formed in the projection, as the class invariant
     * states for one ending: when an endpoint is not present after the change, or when the
     * range from the anchor's projected position to the end's projected position of
     * {@link ElementChange#projectedElements} is ill formed ({@link #illFormedReason}). A
     * replaced endpoint is read as its replacement
     * ({@link ElementChange#projectedEndpointIndexOf}). The rule is the same for every kind of
     * change.
     * <p>
     * The section rule is not judged here: the projection holds one line, and the rule reads
     * the song across lines, so {@link EndingSectionRule} judges it once the edit is over. No
     * edit can make two endings on a line overlap, so there is no overlap rule here either.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} carrying the reason the ending is not well formed in
     *     the projection ({@link #illFormedReasonAfter}), otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
        var reasonKey = illFormedReasonAfter(change);

        return reasonKey == null ? SpanOutcome.Keep.INSTANCE : new SpanOutcome.Remove(reasonKey);
    }

    @Override
    protected String reportHeadingKey() {
        return Strings.DIALOG_REPORT_ENDINGS_REMOVED;
    }

    /**
     * Which rule this ending breaks in {@code change}'s projection, judged by
     * {@link #illFormedReason} over the range between its endpoints' projected positions.
     *
     * @return {@code null} when the ending is well formed in the projection;
     *     {@link Strings#DIALOG_REPORT_REASON_ENDPOINT_DELETED} when an endpoint is not present
     *     after the change; otherwise the reason {@link #illFormedReason} answers
     */
    private @Nullable String illFormedReasonAfter(ElementChange change) {
        var anchorIndex = change.projectedEndpointIndexOf(getAnchorElement());
        var endIndex = change.projectedEndpointIndexOf(getEndElement());

        if (anchorIndex == null || endIndex == null) {
            return Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED;
        }

        return illFormedReason(change.projectedElements(), new ElementRange(anchorIndex, endIndex));
    }

}
