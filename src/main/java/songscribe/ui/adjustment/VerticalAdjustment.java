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

import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.dom.Line;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.MainPanel;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.dom.Hairpin;
import songscribe.dom.RangeElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.LayoutResult;
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

    public VerticalAdjustment(ScoreView scoreView) {
        super(scoreView);
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
            case ROW_HEIGHT -> upLeft.y = scoreView.getNoteYPosPx(6, 0);
            case TEMPO_CHANGE, FIRST_SECOND_ENDING, TRILL, BEAT_CHANGE -> {
                upLeft.y = scoreView.getNoteYPosPx(6, dragRect.line - 1);
                downRight.y = scoreView.getNoteYPosPx(-4, dragRect.line);
            }
            case ANNOTATION, TUPLET, CRESCENDO_Y, DIMINUENDO_Y -> {
                upLeft.y = scoreView.getNoteYPosPx(6, dragRect.line - 1);
                downRight.y = scoreView.getNoteYPosPx(-6, dragRect.line + 1);
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
        var line = scoreView.getSong().getLine(dragRect.line);

        switch (dragRect.type) {
            case ATTRIBUTION -> adjustAttribution(diffY);
            case ROW_HEIGHT -> adjustRowHeight(diffY);
            case TEMPO_CHANGE -> adjustTempoChange(line, diffY);
            case BEAT_CHANGE -> adjustBeatChange(line, diffY);
            case FIRST_SECOND_ENDING -> adjustFirstSecondEnding(line, diffY);
            case ANNOTATION -> adjustAnnotation(line, diffY);
            case TRILL -> adjustTrill(line, diffY);
            case CRESCENDO_Y, DIMINUENDO_Y -> adjustDynamics(line, diffY);
            case TUPLET -> adjustTuplet(line, diffY);
        }

        scoreView.viewChanged();
        revalidateRects();
        scoreView.repaint();
    }

    private void adjustAttribution(int diffY) {
        var newY = scoreView.getSong().getAttributionStartYSs() + diffY;
        MessageCenter.post(new LayoutDidChangeNotification(null, null, newY));
    }

    private void adjustRowHeight(int diffY) {
        var newAdjustment = scoreView.getSong().getRowHeightAdjustmentSs() + diffY;
        MessageCenter.post(new LayoutDidChangeNotification(newAdjustment, null, null));
    }

    private void adjustTempoChange(Line line, int diffY) {
        // Update per-instance offset on all tempo attachments in this line
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var note = line.getElement(i);
            var tempoAttachment = note.findAttachment(TempoChangeAttachment.class);

            if (tempoAttachment != null) {
                tempoAttachment.setUserYOffsetSs(tempoAttachment.getUserYOffsetSs() + diffY);
            }
        }
    }

    private void adjustBeatChange(Line line, int diffY) {
        // Update per-instance offset on all beat change attachments in this line
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var note = line.getElement(i);

            for (var attachment : note.getAttachments()) {
                if (attachment instanceof BeatChangeAttachment) {
                    attachment.setUserYOffsetSs(attachment.getUserYOffsetSs() + diffY);
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
            var attachment = line.getElement(dragRect.xIndex)
                .findAttachment(AnnotationAttachment.class);

            if (attachment == null) {
                return;
            }

            var annotation = attachment.getAnnotation();
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

        var hairpin = findHairpinByAnchor(line, dragRect);

        if (hairpin != null) {
            hairpin.setYShiftSs(hairpin.getYShiftSs() + diffY);
        }
    }

    private void adjustTuplet(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var tuplet = line.findTupletAt(dragRect.xIndex);

        if (tuplet != null) {
            tuplet.setVerticalPositionSs(tuplet.getVerticalPositionSs() + diffY);
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
            var c = scoreView.getSong();

            var attribution = c.getAttribution();

            if (!attribution.isEmpty()) {
                adjustRects.add(new AdjustRect(-1, AdjustType.ATTRIBUTION, -1));
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
                    if (line.getElement(n).findAttachment(AnnotationAttachment.class) != null) {
                        adjustRects.add(
                            new AdjustRect(l, AdjustType.ANNOTATION, n)
                        );
                    }
                }

                var endings = LineEndingSupport.findEndings(line);

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

                for (var crescendo : line.getCrescendos()) {
                    adjustRects.add(new AdjustRect(
                        l,
                        AdjustType.CRESCENDO_Y,
                        crescendo.getAnchorElementIndex()
                    ));
                }

                for (var diminuendo : line.getDiminuendos()) {
                    adjustRects.add(new AdjustRect(
                        l,
                        AdjustType.DIMINUENDO_Y,
                        diminuendo.getAnchorElementIndex()
                    ));
                }

                for (var tuplet : line.findRangeElements(Tuplet.class)) {
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.TUPLET,
                            tuplet.getAnchorElementIndex()
                        )
                    );
                }
            }
        } else {
            adjustRects.clear();
        }
    }

    private void getAdjustRect(AdjustRect adjustRect) {
        var line = scoreView.getSong().getLine(adjustRect.line);
        var note = line.getElement(adjustRect.xIndex);

        switch (adjustRect.type) {
            case ATTRIBUTION -> getAttributionAdjustRect(adjustRect);
            case ROW_HEIGHT -> getHeightAdjustRect(adjustRect);
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
                var ending = LineEndingSupport.findEndingAt(line, adjustRect.xIndex);

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
                var hairpin = findHairpinByAnchor(line, adjustRect);

                if (hairpin == null) {
                    return;
                }

                var startNote = hairpin.getAnchorElement();
                var endNote = hairpin.getEndElement();

                if (startNote == null || endNote == null) {
                    return;
                }

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
                var tuplet = line.findTupletAt(adjustRect.xIndex);

                if (tuplet == null) {
                    return;
                }

                var startNote = tuplet.getAnchorElement();
                var endNote = tuplet.getEndElement();

                if (startNote == null || endNote == null) {
                    return;
                }

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
        adjustRect.rect.x = scoreView.getSheetWidthPx() - HANDLE_SIZE_PX;
        adjustRect.rect.y = (int) scoreView.getSong().getAttributionStartYSs();
    }

    private void getHeightAdjustRect(AdjustRect adjustRect) {
        adjustRect.rect.x = 0;
        adjustRect.rect.y = scoreView.getNoteYPosPx(0, adjustRect.line) - HANDLE_SIZE_PX / 2;
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
        var mainPanel = scoreView.getMainPanel();

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

    @Nullable
    private static Hairpin findHairpinByAnchor(Line line, AdjustRect adjustRect) {
        var isCrescendo = adjustRect.type == AdjustType.CRESCENDO_Y;
        var type = isCrescendo ? Crescendo.class : Diminuendo.class;

        for (var hairpin : line.findRangeElements(type)) {
            if (hairpin.getAnchorElementIndex() == adjustRect.xIndex) {
                return hairpin;
            }
        }

        return null;
    }

    private enum AdjustType {
        ATTRIBUTION(Color.blue),
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
