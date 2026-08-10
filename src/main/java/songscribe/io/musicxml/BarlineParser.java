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
package songscribe.io.musicxml;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import songscribe.dom.Annotation;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;

/**
 * Accumulates one {@code <barline>}'s in-progress state ({@code <bar-style>},
 * {@code <repeat>}, location, and the collected {@code <ending>} markers), then
 * on {@code </barline>} resolves the {@link ElementType}, appends it, and hands
 * the barline element plus its collected markers to
 * {@link EndingResolver#attachBarlineEndings}.
 *
 * <p>The caller ({@link MusicXmlReader}) owns the current line; this class
 * appends elements and reads the current line through the reader reference
 * rather than duplicating that ownership.
 */
final class BarlineParser {

    private final MusicXmlReader reader;
    private final EndingResolver endings;
    private final AnnotationResolver annotations;

    BarlineParser(MusicXmlReader reader, EndingResolver endings, AnnotationResolver annotations) {
        this.reader = reader;
        this.endings = endings;
        this.annotations = annotations;
    }

    // -------------------------------------------------------------------------
    // Barline-in-progress fields — valid while where == BARLINE or its children
    // -------------------------------------------------------------------------

    @Nullable
    private String barlineLocation = null;

    @Nullable
    private String barStyle = null;

    @Nullable
    private String repeatDirection = null;

    // true while a REPEAT_RIGHT is held pending the next barline.
    private boolean pendingRepeatRight = false;

    // <ending> markers collected while parsing the current <barline> (reset at
    // every <barline> start). Resolved to the barline's StaffElement once it is
    // appended — see EndingResolver.attachBarlineEndings.
    private final List<EndingResolver.EndingMarker> currentBarlineEndings = new ArrayList<>();

    // State held alongside a deferred REPEAT_RIGHT (pendingRepeatRight): a
    // REPEAT_RIGHT is not appended at parse time, so its ending markers and its
    // annotation wait here until the element is created (flushed or merged into
    // REPEAT_LEFT_RIGHT). Both are released together by attachHeldRepeatRight.
    private List<EndingResolver.EndingMarker> pendingRepeatRightEndings = List.of();

    @Nullable
    private Annotation pendingRepeatRightAnnotation = null;

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Starts a new {@code <barline>}: captures its {@code location} attribute and
     * clears the bar-style, repeat direction, and collected ending markers left
     * over from the previous barline.
     */
    void beginBarline(@Nullable String location) {
        barlineLocation = location;
        barStyle = null;
        repeatDirection = null;
        currentBarlineEndings.clear();
    }

    /**
     * Captures the current barline's {@code <bar-style>} text.
     */
    void setBarStyle(String style) {
        barStyle = style;
    }

    /**
     * Captures the current barline's {@code <repeat>} {@code direction} attribute.
     */
    void setRepeatDirection(@Nullable String direction) {
        repeatDirection = direction;
    }

    /**
     * Collects one {@code <ending>} marker parsed from the current barline.
     */
    void addEndingMarker(EndingResolver.EndingMarker marker) {
        currentBarlineEndings.add(marker);
    }

    /**
     * Resolves the completed {@code <barline>} and appends the appropriate
     * {@link ElementType} to the current line, or nothing for an invisible barline.
     *
     * <p>The REPEAT_LEFT_RIGHT straddling pair is handled here: a completed
     * REPEAT_RIGHT is held as {@link #pendingRepeatRight}. If the very next
     * barline is a REPEAT_LEFT on the left side, both are merged into a single
     * REPEAT_LEFT_RIGHT element. If not, the pending REPEAT_RIGHT is flushed
     * first, then the new barline is processed normally.
     */
    void processBarline() throws SAXException {
        if (barStyle == null || BarlineStyleMapping.BAR_STYLE_NONE.equals(barStyle)) {
            // Invisible barline — line-break marker only; insert nothing.
            // A pending REPEAT_RIGHT is flushed because an invisible barline
            // can never be the forward half of a REPEAT_LEFT_RIGHT pair.
            flushPendingRepeatRight();
            // An invisible LEFT barline can still host a volta-2 <ending number="2"
            // type="start"> after a REPEAT_RIGHT split; that marker carries no
            // stored value (the split is recomputed live), so no element is needed.
            endings.attachBarlineEndings(null, currentBarlineEndings);
            return;
        }

        var elementType = BarlineStyleMapping.forBarStyle(barStyle, repeatDirection);

        if (elementType == null) {
            // Unknown combination — silently skip rather than corrupt the model.
            flushPendingRepeatRight();
            endings.attachBarlineEndings(null, currentBarlineEndings);
            return;
        }

        if (pendingRepeatRight) {
            if (elementType == ElementType.REPEAT_LEFT
                    && BarlineStyleMapping.LOCATION_LEFT.equals(barlineLocation)) {
                // This left-forward barline is the second half of a straddling
                // REPEAT_LEFT_RIGHT pair — merge and emit the combined element.
                // Both halves' ending markers attach to that single element: the
                // held backward-right markers (volta-1 stop / ending end) first,
                // then this forward-left barline's (volta-2 start / ending anchor).
                var element = reader.appendToCurrentLine(ElementType.REPEAT_LEFT_RIGHT);
                attachHeldRepeatRight(element);
                endings.attachBarlineEndings(element, currentBarlineEndings);
            } else {
                // The pending REPEAT_RIGHT was not followed by a REPEAT_LEFT —
                // flush it as a standalone element, then process the current one.
                flushPendingRepeatRight();
                appendOrHold(elementType, currentBarlineEndings);
            }
        } else {
            appendOrHold(elementType, currentBarlineEndings);
        }
    }

    /**
     * Either holds {@code elementType} as {@link #pendingRepeatRight} (for
     * deferred REPEAT_LEFT_RIGHT pair detection) or appends it immediately,
     * attaching {@code endingMarkers} to the resulting barline element. A held
     * REPEAT_RIGHT carries its ending markers and its annotation with it until the
     * element is created (flushed or merged).
     */
    private void appendOrHold(ElementType elementType, List<EndingResolver.EndingMarker> endingMarkers) throws SAXException {
        if (elementType == ElementType.REPEAT_RIGHT) {
            // Defer: the next barline may be the forward half of a pair. The
            // annotation must come out of the pending slot now — the next element
            // to be appended is not this barline, and would otherwise steal it.
            pendingRepeatRight = true;
            pendingRepeatRightEndings = new ArrayList<>(endingMarkers);
            pendingRepeatRightAnnotation = annotations.takePendingAnnotation();
        } else {
            var element = reader.appendToCurrentLine(elementType);
            endings.attachBarlineEndings(element, endingMarkers);
            annotations.resolveAnnotation(element);
        }
    }

    /**
     * Flushes a held {@link #pendingRepeatRight} as a standalone element,
     * releasing the state held with it.
     */
    void flushPendingRepeatRight() throws SAXException {
        if (pendingRepeatRight) {
            var element = reader.appendToCurrentLine(ElementType.REPEAT_RIGHT);
            attachHeldRepeatRight(element);
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    /**
     * Releases the whole deferred-REPEAT_RIGHT hold onto {@code element}: its held
     * ending markers and its held annotation, clearing the hold.
     *
     * <p>Only the <i>held</i> annotation is attached — never the resolver's
     * pending one. An annotation still pending at this point belongs to an element
     * that has not been appended yet (the REPEAT_LEFT whose forward-left barline is
     * still ahead), so binding it here would steal it.
     *
     * <p>Held endings are attached first, ahead of any the caller goes on to attach
     * from the current barline: they belong to the backward half of the pair.
     */
    private void attachHeldRepeatRight(StaffElement element) {
        var heldEndings = pendingRepeatRightEndings;
        var heldAnnotation = pendingRepeatRightAnnotation;
        pendingRepeatRightEndings = List.of();
        pendingRepeatRightAnnotation = null;
        pendingRepeatRight = false;
        endings.attachBarlineEndings(element, heldEndings);
        annotations.attachAnnotation(element, heldAnnotation);
    }
}
