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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.error.RuntimeError;

/**
 * A first or second ending bracket above a repeated section. Each bracket is at most one
 * measure, so the whole ending is at most two.
 *
 * <p>
 * {@link #illFormedReason} judges every invariant below but the last two over one line's
 * elements: the section rule reads the song across lines, and the last invariant holds between
 * every two endings on a line. Every attached ending meets all of them. {@link #spanning} is the
 * one way to make an ending for a line.
 * <p>
 * Two endings may sit back to back on a line; when no repeat opens between them, the section
 * rule refuses the later one.
 *
 * @invariant the anchor is a note or a rest ({@link ElementType#isDuration()}) or a
 *            {@link ElementType#SINGLE_BARLINE}; any other bar refuses the ending
 * @invariant in front of a note or rest anchor, the first element that is not
 *            {@link ElementType#isNonContentElement() non-content} is a
 *            {@code SINGLE_BARLINE}, a note or a rest, or there is none on the line; any other
 *            bar there closes or opens a repeated section, and an ending does not start
 *            immediately after one
 * @invariant exactly one bar stands strictly inside the range: the split, where the two
 *            brackets part, and it is a {@link ElementType#REPEAT_RIGHT}; a
 *            {@link ElementType#REPEAT_LEFT_RIGHT} there is refused with
 *            {@link Strings#REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT}
 * @invariant the end is a note, a rest, a {@link ElementType#SINGLE_BARLINE}, a
 *            {@link ElementType#DOUBLE_BARLINE}, a {@link ElementType#FINAL_DOUBLE_BARLINE} or a
 *            {@link ElementType#REPEAT_LEFT}
 * @invariant when the ending has an end bar ({@link #getEndBar}), the first element after it
 *            on the line that is not {@link ElementType#isNonContentElement() non-content} is
 *            not a bar; the first element of the next line is not read
 * @invariant each bracket (the first, {@link #firstBracket}, and the second) holds no bar and at
 *            least one note or rest; a grace note, a breath mark or a key change is not content
 * @invariant the split closes an open repeat section: {@link RepeatSections#sectionStart} at
 *            the split is not {@code null} ({@link #closesOpenSection}), which reads across
 *            lines
 * @invariant the ranges of two endings on a line do not overlap
 */
public final class Ending extends Span<StaffElement> {

    private static final String REPORT_HEADING_KEY = Strings.REPORT_ENDINGS_REMOVED;

    private Ending(StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
    }

    /**
     * Returns an ending over {@code range} of {@code line} when the class invariant allows one
     * there, or the reason it does not. This is the one way to create an ending for a line. The
     * editor calls it, {@link SpanLoadPass} calls it for the endings a file reader found, and
     * the paste calls it for each ending a clipboard fragment carries, so a range holds an ending
     * by the same rule whichever route it arrives by.
     * <p>
     * It judges the range over {@code line}'s elements ({@link #illFormedReason}), whether the
     * range overlaps an ending already on {@code line}, and the section rule
     * ({@link #closesOpenSection}), which reads the live song across lines. The section rule is
     * not part of {@link #illFormedReason}, because {@link #outcomeFor} judges a one-line
     * projection of a pending change, which holds no other line.
     * <p>
     * The ending is built, not added: {@code line}'s span registry is unchanged.
     *
     * @param line the line the ending would be on, one of its song's lines
     * @param range the range the ending would cover; both of its indices are indices of
     *     {@code line}
     * @return {@link Spanning.Spanned} with an ending anchored on the element at
     *     {@code range.begin()} and ending on the element at {@code range.end()}, absorbing
     *     nothing; or
     *     {@link Spanning.Refused} with {@link Spanning.Cause#RANGE} and the reason
     *     {@link #illFormedReason} answers when the range is ill formed; with
     *     {@link Spanning.Cause#OTHER_SPAN} and {@link Strings#REPORT_REASON_ENDING_NOT_VALID}
     *     when it overlaps an ending already on {@code line}; or with {@link Spanning.Cause#RANGE}
     *     and {@link Strings#REPORT_REASON_ENDING_NO_OPEN_SECTION} when its split closes no
     *     open repeat section. Every refusal carries {@link Strings#REPORT_ENDINGS_REMOVED}
     *     as its heading.
     * @throws IllegalStateException if the range is well formed and overlaps no ending, and
     *     {@code line} is not one of its song's lines
     */
    public static Spanning<Ending> spanning(Line line, ElementRange range) {
        var elements = line.getElements();
        var illFormed = illFormedReason(elements, range);

        if (illFormed != null) {
            return refused(illFormed, Spanning.Cause.RANGE);
        }

        if (line.spanRegistry().hasSpan(Ending.class, Span.overlapping(range))) {
            return refused(Strings.REPORT_REASON_ENDING_NOT_VALID, Spanning.Cause.OTHER_SPAN);
        }

        var splitIndex = requireSplitIndex(elements, range);

        if (!closesOpenSection(RepeatSections.of(line.getSong()), line, splitIndex)) {
            return refused(Strings.REPORT_REASON_ENDING_NO_OPEN_SECTION, Spanning.Cause.RANGE);
        }

        return new Spanning.Spanned<>(new Ending(elements.get(range.begin()), elements.get(range.end())), List.of());
    }

    private static Spanning<Ending> refused(String reasonKey, Spanning.Cause cause) {
        return new Spanning.Refused<>(reasonKey, cause, REPORT_HEADING_KEY);
    }

    /**
     * The one statement of the section rule; {@link #spanning} and {@link EndingSectionRule}
     * both apply it.
     *
     * <p>In {@code |: |: :| :|} a split at the last {@code :|} closes the outer section, so an
     * ending there is legal. The nested {@code |:} is an error of the song's repeats, not of the
     * ending.
     */
    static boolean closesOpenSection(RepeatSections sections, Line line, int splitIndex) {
        return sections.sectionStart(new ElementLocation(line.index(), splitIndex)) != null;
    }

    /**
     * Judged over {@code elements} alone, so not the section rule or the overlap rule.
     *
     * @return {@code null} when the ending is well formed
     */
    private static @Nullable String illFormedReason(List<StaffElement> elements, ElementRange span) {
        var anchorIndex = span.begin();
        var endIndex = span.end();
        var anchorType = elements.get(anchorIndex).getType();

        if (!isAnchorType(anchorType) || !hasOpeningPredecessor(elements, anchorIndex)) {
            return Strings.REPORT_REASON_ENDING_NOT_VALID;
        }

        var splitIndex = splitIndexOf(elements, span);

        if (splitIndex == null) {
            return Strings.REPORT_REASON_ENDING_NOT_VALID;
        }

        if (elements.get(splitIndex).getType() != ElementType.REPEAT_RIGHT) {
            return Strings.REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT;
        }

        var endType = elements.get(endIndex).getType();

        if (!isValidEnd(endType)) {
            return Strings.REPORT_REASON_ENDING_NOT_VALID;
        }

        var first = firstBracketOf(elements, span, splitIndex);
        var second = secondBracketOf(elements, span, splitIndex);
        var bracketsWellFormed = first != null && second != null
            && isWellFormedBracket(elements, first) && isWellFormedBracket(elements, second);

        if (!bracketsWellFormed) {
            return Strings.REPORT_REASON_ENDING_NOT_VALID;
        }

        return barFollowsEndBar(elements, endIndex)
            ? Strings.REPORT_REASON_ENDING_BAR_AFTER_END
            : null;
    }

    /**
     * The walk stops at the end of {@code elements}, so it never reads the next line.
     */
    private static boolean barFollowsEndBar(List<StaffElement> elements, int endIndex) {
        var endBarIndex = endBarIndexOf(elements, endIndex);

        if (endBarIndex == null) {
            return false;
        }

        var following = followingContentIndex(elements, endBarIndex);

        return following != null && elements.get(following).getType().isBar();
    }

    private static boolean isAnchorType(ElementType type) {
        return type.isDuration() || opensOnItsOwn(type);
    }

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

    private static @Nullable Integer precedingContentIndex(List<StaffElement> elements, int index) {
        return StaffElementRun.lastIndexBefore(elements, index, type -> !type.isNonContentElement());
    }

    private static @Nullable Integer followingContentIndex(List<StaffElement> elements, int index) {
        return StaffElementRun.firstIndexFrom(elements, index + 1, type -> !type.isNonContentElement());
    }

    public ElementRange firstBracket() {
        return requireBracket(firstBracketOf(line().getElements(), range(), getSplitIndex()));
    }

    private static @Nullable ElementRange firstBracketOf(
        List<StaffElement> elements, ElementRange span, int splitIndex
    ) {
        var anchorIndex = span.begin();
        var before = elements.get(anchorIndex).getType().isDuration() ? anchorIndex - 1 : anchorIndex;

        return elementsBetween(before, splitIndex);
    }

    /**
     * Layout draws the bracket over an end note, so the bracket holds it.
     */
    private static @Nullable ElementRange secondBracketOf(
        List<StaffElement> elements, ElementRange span, int splitIndex
    ) {
        var endIndex = span.end();
        var after = elements.get(endIndex).getType().isDuration() ? endIndex + 1 : endIndex;

        return elementsBetween(splitIndex, after);
    }

    /**
     * The bounds stay two ints because a bracket bound of {@code -1}, a note anchor at
     * index 0, is not an {@link ElementRange}.
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

    private static boolean isWellFormedBracket(List<StaffElement> elements, ElementRange bracket) {
        var bracketElements = elementsIn(elements, bracket);

        return bracketElements.stream().noneMatch(element -> element.getType().isBar())
            && holdsContent(bracketElements);
    }

    private static boolean holdsContent(List<StaffElement> elements) {
        return elements.stream().anyMatch(element -> element.getType().isDuration());
    }

    private static List<StaffElement> elementsIn(List<StaffElement> elements, ElementRange range) {
        return elements.subList(range.begin(), range.end() + 1);
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        return new Ending(newAnchor, newEnd);
    }

    public int getSplitIndex() {
        return requireSplitIndex(line().getElements(), range());
    }

    public BarElement getSplitElement() {
        // Only a bar's type closes a repeat, and a bar's type makes its element a BarElement.
        return (BarElement) line().getElement(getSplitIndex());
    }

    /**
     * @return {@code null} when no such element stands inside {@code span}
     */
    private static @Nullable Integer splitIndexOf(List<StaffElement> elements, ElementRange span) {
        // The view ends just before span.end() and starts at 0, so its indices are elements' own.
        return StaffElementRun.firstIndexFrom(
            elements.subList(0, span.end()), span.begin() + 1, ElementType::closesRepeat);
    }

    private static int requireSplitIndex(List<StaffElement> elements, ElementRange span) {
        var splitIndex = splitIndexOf(elements, span);

        if (splitIndex == null) {
            throw RuntimeError.exit("a well-formed ending has no split");
        }

        return splitIndex;
    }

    /**
     * The editor anchors a new ending on this element, and layout starts the bracket's left edge
     * at it, so both apply the one rule. Asking twice gives the same answer as asking once, so
     * the editor can move a selection's anchor onto the bar at creation and layout ask again at
     * paint time without walking back a second bar.
     */
    public int getOpeningElementIndex() {
        return openingElementIndex(line(), requireAnchorElementIndex());
    }

    /**
     * The static form is for the editor, which asks before there is an ending.
     */
    public static int openingElementIndex(Line line, int anchorIndex) {
        return openingElementIndex(line.getElements(), anchorIndex);
    }

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
     * Every other bar begins or closes a repeated section or ends the piece, and no ending opens
     * on one of those.
     */
    private static boolean opensOnItsOwn(ElementType type) {
        return type == ElementType.SINGLE_BARLINE;
    }

    /**
     * @return {@code null} when the second bracket stops after a note
     */
    public @Nullable BarElement getEndBar() {
        var line = line();
        var endBarIndex = endBarIndexOf(line.getElements(), requireEndElementIndex());

        // Only a bar's type makes its element a BarElement.
        return endBarIndex == null ? null : (BarElement) line.getElement(endBarIndex);
    }

    private static @Nullable Integer endBarIndexOf(List<StaffElement> elements, int endIndex) {
        if (elements.get(endIndex).getType().isBar()) {
            return endIndex;
        }

        var next = endIndex + 1;

        return next < elements.size() && elements.get(next).getType().isBar() ? next : null;
    }

    public boolean isSecondBracketClosed() {
        var endBar = getEndBar();
        return endBar != null && endBar.getType() != ElementType.SINGLE_BARLINE;
    }

    /**
     * The split closes the repeated section, so the second bracket ends wherever a section may
     * end: on a note or a rest, or on a bar that {@link ElementType#isEndingTerminal() ends an
     * ending}.
     */
    private static boolean isValidEnd(ElementType endType) {
        return endType.isDuration() || endType.isEndingTerminal();
    }

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
        return REPORT_HEADING_KEY;
    }

    /**
     * @return {@code null} when the ending is well formed in the projection
     */
    private @Nullable String illFormedReasonAfter(ElementChange change) {
        var anchorIndex = change.projectedEndpointIndexOf(getAnchorElement());
        var endIndex = change.projectedEndpointIndexOf(getEndElement());

        if (anchorIndex == null || endIndex == null) {
            return Strings.REPORT_REASON_ENDPOINT_DELETED;
        }

        return illFormedReason(change.projectedElements(), new ElementRange(anchorIndex, endIndex));
    }

}
