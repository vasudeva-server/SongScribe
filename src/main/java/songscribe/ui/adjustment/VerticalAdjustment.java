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
package songscribe.ui.adjustment;

import module java.desktop;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import songscribe.music.DynamicsSpan;
import songscribe.music.SpanSet;
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.music.Line;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.TempoChangeAttachment;
import songscribe.ui.layout.Trill;
import songscribe.ui.layout.Tuplet;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.renderer.GraphicsState;

public class VerticalAdjustment extends Adjustment {

    /** Width and height of a drag handle, in device pixels. */
    private static final int HANDLE_SIZE_PX = 8;

    /** X offset from the anchor element's left edge for a trill drag handle. */
    private static final int TRILL_HANDLE_X_OFFSET_PX = 12;

    /** X offset applied when a tuplet bracket sits below (non-upper stems). */
    private static final int TUPLET_LOWER_HANDLE_X_OFFSET_PX = -10;

    /** Horizontal bias added to the midpoint when centering a dynamics handle. */
    private static final int DYNAMICS_HANDLE_CENTER_BIAS_PX = 12;

    // TODO: Hoist to superclass
    @Nullable
    private AdjustRect dragRect;

    // TODO: Hoist to superclass
    private final ArrayList<AdjustRect> adjustRects = new ArrayList<>();

    public VerticalAdjustment(Score score) {
        super(score);
    }

    @Override
    protected void startedDrag() {
        // TODO: Hoist duplicate code to superclass

        if (startPoint == null) {
            startedDrag = false;
            return;
        }

        dragRect = adjustRects
            .stream()
            .filter(adjustRect -> adjustRect.rect.contains(startPoint))
            .findFirst()
            .orElse(null);

        if (dragRect == null) {
            startedDrag = false;
            return;
        }

        var upLeft = new Point(dragRect.rect.x, 0);
        var downRight = new Point(dragRect.rect.x, Integer.MAX_VALUE);

        switch (dragRect.type) {
            case ROW_HEIGHT -> upLeft.y = score.getNoteYPosPx(6, 0);
            case TEMPO_CHANGE, FIRST_SECOND_ENDING, TRILL, BEAT_CHANGE -> {
                upLeft.y = score.getNoteYPosPx(6, dragRect.line - 1);
                downRight.y = score.getNoteYPosPx(-4, dragRect.line);
            }
            case ANNOTATION, TUPLET, CRESCENDO_Y, DIMINUENDO_Y -> {
                upLeft.y = score.getNoteYPosPx(6, dragRect.line - 1);
                downRight.y = score.getNoteYPosPx(-6, dragRect.line + 1);
            }
        }

        topLeftDragBounds.setLocation(upLeft.x, upLeft.y);
        bottomRightDragBounds.setLocation(downRight.x, downRight.y);
    }

    @Override
    protected void drag() {
        if (dragRect == null) {
            return;
        }

        var midPoint = new Point(
            dragRect.rect.width / 2,
            dragRect.rect.height / 2
        );

        var diffX = (endPoint.x - dragRect.rect.x) + midPoint.x;
        var diffY = (endPoint.y - dragRect.rect.y) + midPoint.y;
        dragRect.rect.y = endPoint.y - midPoint.y;
        var line = score.getSong().getLine(dragRect.line);

        switch (dragRect.type) {
            case ATTRIBUTION -> adjustAttribution(diffY);
            case TOP_SPACE -> adjustTopSpace(diffY);
            case ROW_HEIGHT -> adjustRowHeight(diffY);
            case TEMPO_CHANGE -> adjustTempoChange(line, diffY);
            case BEAT_CHANGE -> adjustBeatChange(line, diffY);
            case FIRST_SECOND_ENDING -> adjustFirstSecondEnding(line, diffY);
            case ANNOTATION -> adjustAnnotation(line, diffY);
            case TRILL -> adjustTrill(line, diffY);
            case CRESCENDO_Y, DIMINUENDO_Y -> adjustDynamics(line, diffY);
            case TUPLET -> adjustTuplet(line, diffY);
        }

        score.viewChanged();
        revalidateRects();
        score.repaint();
    }

    private void adjustAttribution(int diffY) {
        var newY = score.getSong().getAttributionStartYSs() + diffY;
        MessageCenter.post(new LayoutDidChangeNotification(null, null, null, null, newY));
    }

    private void adjustTopSpace(int diffY) {
        var newPadding = score.getSong().getTopPaddingSs() + diffY;
        MessageCenter.post(new LayoutDidChangeNotification(newPadding, true, null, null, null));
    }

    private void adjustRowHeight(int diffY) {
        var newAdjustment = score.getSong().getRowHeightAdjustmentSs() + diffY;
        MessageCenter.post(new LayoutDidChangeNotification(null, null, newAdjustment, null, null));
    }

    private void adjustTempoChange(Line line, int diffY) {
        // Update per-instance offset on all tempo attachments in this line
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var note = line.getElement(i);

            if (note.getTempoChange() != null) {
                for (var attachment : note.getAttachments()) {
                    if (attachment instanceof TempoChangeAttachment) {
                        attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() + diffY);
                    }
                }
            }
        }
    }

    private void adjustBeatChange(Line line, int diffY) {
        // Update per-instance offset on all beat change attachments in this line
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var note = line.getElement(i);

            if (note.getBeatChange() != null) {
                for (var attachment : note.getAttachments()) {
                    if (attachment instanceof BeatChangeAttachment) {
                        attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() + diffY);
                    }
                }
            }
        }
    }

    private void adjustFirstSecondEnding(Line line, int diffY) {
        // Update per-instance offset on all ending objects in this line
        for (var element : line.getRangeElements()) {
            if (element instanceof Ending ending) {
                ending.setYPositionSs(ending.getYPositionSs() + diffY);
            }
        }
    }

    private void adjustAnnotation(Line line, int diffY) {
        if (dragRect != null) {
            var annotation = line.getElement(dragRect.xIndex).getAnnotation();

            if (annotation == null) {
                return;
            }

            // Update user offset (delta from calculated position)
            annotation.setUserYOffsetSs(annotation.getUserYOffsetSs() + diffY);
            // Also update legacy yPos for backward compatibility
            annotation.setYPosPx(annotation.getYPosPx() + diffY);
        }
    }

    private void adjustTrill(Line line, int diffY) {
        // Update per-instance offset on all trill objects in this line
        for (var element : line.getRangeElements()) {
            if (element instanceof Trill trill) {
                trill.setYPositionSs(trill.getYPositionSs() + diffY);
            }
        }
    }

    private void adjustDynamics(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var span = getCresDecrSpanSet(line, dragRect.type).findSpan(
            dragRect.xIndex
        );

        if (span != null) {
            span.setYShiftSs(span.getYShiftSs() + diffY);
        }
    }

    private void adjustTuplet(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var span = line.getTuplets().findSpan(dragRect.xIndex);

        if (span != null) {
            span.setVerticalPositionSs(span.getVerticalPositionSs() + diffY);
        }
    }

    @Override
    protected void finishedDrag() {
        dragRect = null;
    }

    @Override
    public void repaint(Graphics2D g2) {
        for (var ar : adjustRects) {
            try (var ignored = GraphicsState.save(
                g2,
                GraphicsState.Property.COLOR
            )) {
                g2.setPaint(ar.type.getColor());
                g2.fill(ar.rect);
                g2.setPaint(Color.black);
                g2.draw(ar.rect);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (enabled) {
            var c = score.getSong();

            var attribution = c.getAttribution();

            if (!attribution.isEmpty()) {
                adjustRects.add(new AdjustRect(-1, AdjustType.ATTRIBUTION, -1));
            }

            if (c.lineCount() > 0) {
                adjustRects.add(new AdjustRect(0, AdjustType.TOP_SPACE, -1));
            }

            if (c.lineCount() > 1) {
                adjustRects.add(new AdjustRect(1, AdjustType.ROW_HEIGHT, -1));
            }

            for (var l = 0; l < c.lineCount(); l++) {
                var line = c.getLine(l);

                var firstTempoChange = line.getFirstTempoChange();

                if (firstTempoChange > -1) {
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.TEMPO_CHANGE,
                            firstTempoChange
                        )
                    );
                }

                for (var n = 0; n < line.effectiveElementCount(); n++) {
                    if (line.getElement(n).getAnnotation() != null) {
                        adjustRects.add(
                            new AdjustRect(l, AdjustType.ANNOTATION, n)
                        );
                    }
                }

                var endings = line.findEndings();

                if (!endings.isEmpty()) {
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.FIRST_SECOND_ENDING,
                            endings.getFirst().getAnchorElementIndex()
                        )
                    );
                }

                var firstTrill = line.getFirstTrill();

                if (firstTrill > -1) {
                    adjustRects.add(
                        new AdjustRect(l, AdjustType.TRILL, firstTrill)
                    );
                }

                var firstBeatChange = line.getFirstBeatChange();

                if (firstBeatChange > -1) {
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.BEAT_CHANGE,
                            firstBeatChange
                        )
                    );
                }

                for (
                    var li = line.getCrescendos().listIterator();
                    li.hasNext();
                ) {
                    var span = li.next();
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.CRESCENDO_Y,
                            span.getStart()
                        )
                    );
                }

                for (
                    var li = line.getDiminuendos().listIterator();
                    li.hasNext();
                ) {
                    var span = li.next();
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.DIMINUENDO_Y,
                            span.getStart()
                        )
                    );
                }

                for (var li = line.getTuplets().listIterator(); li.hasNext(); ) {
                    var span = li.next();
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.TUPLET,
                            span.getStart()
                        )
                    );
                }
            }
        } else {
            adjustRects.clear();
        }
    }

    private void getAdjustRect(AdjustRect adjustRect) {
        var line = score.getSong().getLine(adjustRect.line);
        var note = line.getElement(adjustRect.xIndex);

        switch (adjustRect.type) {
            case ATTRIBUTION -> getAttributionAdjustRect(adjustRect);
            case TOP_SPACE, ROW_HEIGHT -> getHeightAdjustRect(adjustRect);
            case TEMPO_CHANGE -> {
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findAttachmentBounds(note, TempoChangeAttachment.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for TempoChangeAttachment");
                }

                adjustRect.rect.x = note.getXOffsetPx() - HANDLE_SIZE_PX;
                adjustRect.rect.y = (int) bounds.getTopSs() - HANDLE_SIZE_PX;
            }
            case BEAT_CHANGE -> {
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findAttachmentBounds(note, BeatChangeAttachment.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for BeatChangeAttachment");
                }

                adjustRect.rect.x = note.getXOffsetPx() - HANDLE_SIZE_PX;
                adjustRect.rect.y = (int) bounds.getTopSs() - HANDLE_SIZE_PX;
            }
            case FIRST_SECOND_ENDING -> {
                var ending = line.findEndingAt(adjustRect.xIndex);

                if (!getRangeElementAdjustRect(adjustRect, ending, Ending.class, HANDLE_SIZE_PX)) {
                    return;
                }
            }
            case ANNOTATION -> {
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findAttachmentBounds(note, AnnotationAttachment.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for AnnotationAttachment");
                }

                adjustRect.rect.x = (int) bounds.getLeftSs() - HANDLE_SIZE_PX;
                adjustRect.rect.y = (int) bounds.getTopSs() - HANDLE_SIZE_PX;
            }
            case TRILL -> {
                var trill = line.getRangeElements().stream()
                    .filter(e -> e instanceof Trill)
                    .map(e -> (Trill) e)
                    .filter(t -> {
                        var anchorIdx = t.getAnchorElementIndex();
                        var endIdx = t.getEndElementIndex();
                        return anchorIdx >= 0 && endIdx >= 0 &&
                            adjustRect.xIndex >= anchorIdx &&
                            adjustRect.xIndex <= endIdx;
                    })
                    .findFirst()
                    .orElse(null);

                if (!getRangeElementAdjustRect(adjustRect, trill, Trill.class, TRILL_HANDLE_X_OFFSET_PX)) {
                    return;
                }
            }
            case CRESCENDO_Y, DIMINUENDO_Y -> {
                var span = getCresDecrSpanSet(line, adjustRect.type)
                    .findSpan(adjustRect.xIndex);

                if (span == null) {
                    return;
                }

                var startNote = line.getElement(span.getStart());
                var endNote = line.getElement(span.getEnd());
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var rangeClass = adjustRect.type == AdjustType.CRESCENDO_Y
                    ? Crescendo.class
                    : Diminuendo.class;
                var bounds = layoutResult.findRangeElementBounds(startNote, endNote, rangeClass);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for dynamics element");
                }

                // Position handle at center of dynamics hairpin
                var startX = startNote.getXOffsetPx();
                var endX = endNote.getXOffsetPx();
                adjustRect.rect.x = (startX + endX + DYNAMICS_HANDLE_CENTER_BIAS_PX) / 2;
                adjustRect.rect.y = (int) bounds.getTopSs();
            }
            case TUPLET -> {
                var span = line.getTuplets().findSpan(adjustRect.xIndex);

                if (span == null) {
                    return;
                }

                var startNote = line.getElement(span.getStart());
                var endNote = line.getElement(span.getEnd());
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findRangeElementBounds(startNote, endNote, Tuplet.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for Tuplet");
                }

                adjustRect.rect.x = startNote.getXOffsetPx()
                    + (startNote.isUpper() ? 0 : TUPLET_LOWER_HANDLE_X_OFFSET_PX);
                adjustRect.rect.y = (int) bounds.getTopSs();
            }
        }

        adjustRect.rect.width = HANDLE_SIZE_PX;
        adjustRect.rect.height = HANDLE_SIZE_PX;
    }

    private boolean getRangeElementAdjustRect(
        AdjustRect adjustRect,
        @Nullable RangeElement rangeElement,
        Class<? extends RangeElement> elementClass,
        int xOffsetPx
    ) {
        if (rangeElement == null) {
            return false;
        }

        var startNote = rangeElement.getAnchorElement();
        var endNote = rangeElement.getEndElement();

        if (startNote == null || endNote == null) {
            return false;
        }

        var layoutResult = getLayoutResultForLine(adjustRect.line);
        var bounds = layoutResult.findRangeElementBounds(startNote, endNote, elementClass);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for " + elementClass.getSimpleName());
        }

        adjustRect.rect.x = startNote.getXOffsetPx() - xOffsetPx;
        adjustRect.rect.y = (int) bounds.getTopSs() - HANDLE_SIZE_PX;
        return true;
    }

    private void getAttributionAdjustRect(AdjustRect adjustRect) {
        adjustRect.rect.x = score.getSheetWidthPx() - HANDLE_SIZE_PX;
        adjustRect.rect.y = (int) score.getSong().getAttributionStartYSs();
    }

    private void getHeightAdjustRect(AdjustRect adjustRect) {
        adjustRect.rect.x = 0;
        adjustRect.rect.y = score.getNoteYPosPx(0, adjustRect.line) - HANDLE_SIZE_PX / 2;
    }

    private void revalidateRects() {
        for (var ar : adjustRects) {
            getAdjustRect(ar);
        }
    }

    /**
     * Gets the layout result for a specific line.
     *
     * @param lineIndex The line index
     * @return The layout result
     * @throws IllegalStateException if layout result is not available
     */
    private LayoutResult getLayoutResultForLine(int lineIndex) {
        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            throw new IllegalStateException("MainPanel not available");
        }

        var layoutResult = getLayoutResult(lineIndex, mainPanel);

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result not available for line " + lineIndex);
        }

        return layoutResult;
    }

    private static @Nullable LayoutResult getLayoutResult(int lineIndex, MainPanel mainPanel) {
        var staffPanel = mainPanel.getStaffPanel();
        var linePanels = staffPanel.getLinePanels();

        if (lineIndex < 0 || lineIndex >= linePanels.size()) {
            throw new IllegalStateException("Invalid line index: " + lineIndex);
        }

        var lineComponent = linePanels.get(lineIndex).getLineComponent();
        var layoutResult = lineComponent.getLayoutResult();
        return layoutResult;
    }

    private static SpanSet<DynamicsSpan> getCresDecrSpanSet(
        Line line,
        AdjustType adjustType
    ) {
        return switch (adjustType) {
            case CRESCENDO_Y -> line.getCrescendos();
            case DIMINUENDO_Y -> line.getDiminuendos();
            default -> throw new IllegalArgumentException(
                String.valueOf(adjustType)
            );
        };
    }

    private enum AdjustType {
        ATTRIBUTION(Color.blue),
        TOP_SPACE(Color.cyan),
        ROW_HEIGHT(Color.orange),
        TEMPO_CHANGE(Color.red),
        BEAT_CHANGE(Color.pink),
        FIRST_SECOND_ENDING(Color.green),
        ANNOTATION(Color.magenta),
        TRILL(Color.pink),
        CRESCENDO_Y(Color.green),
        DIMINUENDO_Y(Color.green),
        TUPLET(Color.pink);

        private final Color color;

        AdjustType(Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    private class AdjustRect {

        final Rectangle rect;
        final int line;
        final int xIndex;
        final AdjustType type;

        AdjustRect(int line, AdjustType type, int xIndex) {
            this.line = line;
            this.type = type;
            this.xIndex = xIndex;
            rect = new Rectangle();
            getAdjustRect(this);
        }
    }
}
