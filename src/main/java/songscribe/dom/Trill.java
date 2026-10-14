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
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * A trill marking over one or more notes; a single-note trill has the same anchor and end.
 * <p>
 * Both endpoints are pitched notes, never grace notes. No element from the anchor to the end is
 * a rest or a breath mark.
 */
public final class Trill extends Span<NoteElement> implements IntrinsicHeight {

    public static final double TRILL_GLYPH_WIDTH_SS;
    private static final double TRILL_GLYPH_HEIGHT_SS;

    private static final String REPORT_HEADING_KEY = Strings.REPORT_TRILLS_REMOVED;

    static {
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.ORNAMENT_TRILL);
        TRILL_GLYPH_WIDTH_SS = bbox.widthSs();
        TRILL_GLYPH_HEIGHT_SS = bbox.heightSs();
    }

    private Trill(NoteElement anchorElement, NoteElement endElement) {
        super(NoteElement.class, anchorElement, endElement);
    }

    /**
     * Returns a trill over {@code range} of {@code line} when the class invariant allows one
     * there, or the reason it does not. With {@link #createCopy}, this is the one way to make a
     * trill: the editor, the paste and both file readers ask it, so a range holds a trill by the
     * same rule whichever route it arrives by.
     * <p>
     * The trill is built, not added: {@code line}'s span registry is unchanged. A trill that
     * overlaps it is not a refusal, because {@link #addTo} replaces it
     * ({@link Displacement#REPLACE_OVERLAPPING}).
     *
     * @param line  the line the trill would be on
     * @param range the range the trill would cover; both of its indices are indices of
     *              {@code line}
     * @return {@link Spanning.Spanned} with a new trill anchored on the element at
     *     {@code range.begin()} and ending on the element at {@code range.end()}, absorbing
     *     nothing; or {@link Spanning.Refused} with {@link Spanning.Cause#RANGE}, under
     *     {@link Strings#REPORT_TRILLS_REMOVED}, and the reason {@link #illFormedReason}
     *     answers for {@code line}'s elements
     */
    public static Spanning<Trill> spanning(Line line, ElementRange range) {
        var illFormed = illFormedReason(line.getElements(), range);

        if (illFormed != null) {
            return new Spanning.Refused<>(illFormed, Spanning.Cause.RANGE, REPORT_HEADING_KEY);
        }

        return new Spanning.Spanned<>(new Trill(line.noteAt(range.begin()), line.noteAt(range.end())), List.of());
    }

    /**
     * The class invariant, judged over {@code elements}, so that {@link #spanning} can judge a
     * line and {@link #outcomeFor} the projection of a pending change by the same rule.
     *
     * @param range indices of {@code elements}
     * @return {@code null} when a trill may cover {@code range};
     *     {@link Strings#REPORT_REASON_TRILL_ENDPOINT_UNUSABLE} when the element at either end
     *     is not a pitched note or is a grace note; otherwise
     *     {@link Strings#REPORT_REASON_TRILL_REST_OR_BREATH_MARK} when an element of
     *     {@code range} is a rest or a breath mark
     */
    private static @Nullable String illFormedReason(List<? extends StaffElement> elements, ElementRange range) {
        var anchorType = elements.get(range.begin()).getType();
        var endType = elements.get(range.end()).getType();

        if (!anchorType.isPitchedNote() || !endType.isPitchedNote()) {
            return Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE;
        }

        for (var index = range.begin(); index <= range.end(); index++) {
            var type = elements.get(index).getType();

            if (type.isRest() || type.isBreathMark()) {
                return Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK;
            }
        }

        return null;
    }

    /**
     * Decides what happens to this trill when {@code change} lands. It removes the trill exactly
     * when the class invariant fails in the projection: when an endpoint is not present after
     * the change, or when {@link #illFormedReason} refuses the range from the anchor's projected
     * position to the end's. A replaced endpoint is read as its replacement
     * ({@link ElementChange#projectedEndpointIndexOf}). The rule is the same for every kind of
     * change.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} whose reason is
     *     {@link Strings#REPORT_REASON_ENDPOINT_DELETED} when an endpoint is not present after
     *     the change, or the reason {@link #illFormedReason} answers for the projection;
     *     otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
        var anchorIndex = change.projectedEndpointIndexOf(getAnchorElement());
        var endIndex = change.projectedEndpointIndexOf(getEndElement());

        if (anchorIndex == null || endIndex == null) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_ENDPOINT_DELETED);
        }

        var reasonKey = illFormedReason(change.projectedElements(), new ElementRange(anchorIndex, endIndex));

        return reasonKey == null ? SpanOutcome.Keep.INSTANCE : new SpanOutcome.Remove(reasonKey);
    }

    @Override
    protected Displacement displacement() {
        return Displacement.REPLACE_OVERLAPPING;
    }

    @Override
    protected String reportHeadingKey() {
        return REPORT_HEADING_KEY;
    }

    @Override
    protected Trill createCopy(NoteElement newAnchor, NoteElement newEnd) {
        return new Trill(newAnchor, newEnd);
    }

    public double intrinsicWidthSs() {
        return TRILL_GLYPH_WIDTH_SS;
    }

    @Override
    public double intrinsicHeightSs() {
        return TRILL_GLYPH_HEIGHT_SS;
    }
}
