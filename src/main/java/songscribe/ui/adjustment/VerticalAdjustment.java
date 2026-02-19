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

import java.awt.*;
import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.data.DynamicsInterval;
import songscribe.data.IntervalSet;
import songscribe.data.TupletInterval;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.component.Score;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.TempoAttachment;
import songscribe.ui.layout.Trill;
import songscribe.ui.layout.Tuplet;
import songscribe.ui.layout2.LayoutResult;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;

public class VerticalAdjustment extends Adjustment {

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
            case ROW_HEIGHT -> upLeft.y = score.getNoteYPos(6, 0);
            case TEMPO_CHANGE, FIRST_SECOND_ENDING, TRILL, BEAT_CHANGE -> {
                upLeft.y = score.getNoteYPos(6, dragRect.line - 1);
                downRight.y = score.getNoteYPos(-4, dragRect.line);
            }
            case ANNOTATION, TUPLET, CRESCENDO_Y, DIMINUENDO_Y -> {
                upLeft.y = score.getNoteYPos(6, dragRect.line - 1);
                downRight.y = score.getNoteYPos(-6, dragRect.line + 1);
            }
            case null, default -> {
                upLeft.x = 0;
                downRight.x = Integer.MAX_VALUE;
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
        var line = score.getComposition().getLine(dragRect.line);

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
        score.getComposition().setModified(true);
        revalidateRects();
        score.repaint();
    }

    private void adjustAttribution(int diffY) {
        score
            .getComposition()
            .setAttributionStartY(score.getComposition().getAttributionStartY() + diffY);

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.ATTRIBUTION,
            LayoutChangeMessage.ChangeType.SIZE,
            true
        ));
    }

    private void adjustTopSpace(int diffY) {
        score
            .getComposition()
            .setTopPadding(
                score.getComposition().getTopPadding() + diffY,
                true
            );

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.TITLE,
            LayoutChangeMessage.ChangeType.SIZE,
            true
        ));
    }

    private void adjustRowHeight(int diffY) {
        score
            .getComposition()
            .setRowHeightAdjustment(
                score.getComposition().getRowHeightAdjustment() + diffY
            );

        MessageCenter.post(new LayoutChangeMessage(
            LayoutChangeMessage.Section.SCORE,
            LayoutChangeMessage.ChangeType.SIZE,
            true
        ));
    }

    private void adjustTempoChange(Line line, int diffY) {
        // Update per-instance offset on all tempo attachments in this line
        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            if (note.getTempoChange() != null) {
                for (var attachment : note.getAttachments()) {
                    if (attachment instanceof songscribe.ui.layout.TempoAttachment) {
                        attachment.setUserYOffset(attachment.getUserYOffset() + diffY);
                    }
                }
            }
        }
    }

    private void adjustBeatChange(Line line, int diffY) {
        // Update per-instance offset on all beat change attachments in this line
        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            if (note.getBeatChange() != null) {
                for (var attachment : note.getAttachments()) {
                    if (attachment instanceof songscribe.ui.layout.BeatChangeAttachment) {
                        attachment.setUserYOffset(attachment.getUserYOffset() + diffY);
                    }
                }
            }
        }
    }

    private void adjustFirstSecondEnding(Line line, int diffY) {
        // Update per-instance offset on all ending objects in this line
        for (var element : line.getRangeElements()) {
            if (element instanceof songscribe.ui.layout.Ending ending) {
                ending.setYPosition(ending.getYPosition() + diffY);
            }
        }
    }

    private void adjustAnnotation(Line line, int diffY) {
        if (dragRect != null) {
            var annotation = line.getNote(dragRect.xIndex).getAnnotation();
            // Update user offset (delta from calculated position)
            annotation.setUserYOffset(annotation.getUserYOffset() + diffY);
            // Also update legacy yPos for backward compatibility
            annotation.setYPos(annotation.getYPos() + diffY);
        }
    }

    private void adjustTrill(Line line, int diffY) {
        // Update per-instance offset on all trill objects in this line
        for (var element : line.getRangeElements()) {
            if (element instanceof songscribe.ui.layout.Trill trill) {
                trill.setYPosition(trill.getYPosition() + diffY);
            }
        }
    }

    private void adjustDynamics(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var interval = getCresDecrIntervalSet(line, dragRect.type).findInterval(
            dragRect.xIndex
        );

        if (interval != null) {
            interval.setYShift(interval.getYShift() + diffY);
        }
    }

    private void adjustTuplet(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var interval = line.getTuplets().findInterval(dragRect.xIndex);

        if (interval != null) {
            interval.setVerticalPosition(interval.getVerticalPosition() + diffY);
        }
    }

    @Override
    protected void finishedDrag() {
        dragRect = null;
    }

    @Override
    public void repaint(Graphics2D g2) {
        for (var ar : adjustRects) {
            try (var ignored = songscribe.ui.renderer.GraphicsState.save(
                g2,
                songscribe.ui.renderer.GraphicsState.Property.COLOR
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
            var c = score.getComposition();

            if (!c.getAttribution().isEmpty()) {
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

                for (var n = 0; n < line.noteCount(); n++) {
                    if (line.getNote(n).getAnnotation() != null) {
                        adjustRects.add(
                            new AdjustRect(l, AdjustType.ANNOTATION, n)
                        );
                    }
                }

                if (!line.getFirstSecondEndings().isEmpty()) {
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.FIRST_SECOND_ENDING,
                            line
                                .getFirstSecondEndings()
                                .listIterator()
                                .next()
                                .getStart()
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
                    var interval = li.next();
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.CRESCENDO_Y,
                            interval.getStart()
                        )
                    );
                }

                for (
                    var li = line.getDiminuendos().listIterator();
                    li.hasNext();
                ) {
                    var interval = li.next();
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.DIMINUENDO_Y,
                            interval.getStart()
                        )
                    );
                }

                for (var li = line.getTuplets().listIterator(); li.hasNext();) {
                    var interval = li.next();
                    adjustRects.add(
                        new AdjustRect(
                            l,
                            AdjustType.TUPLET,
                            interval.getStart()
                        )
                    );
                }
            }
        } else {
            adjustRects.clear();
        }
    }

    private void getAdjustRect(AdjustRect adjustRect) {
        var line = score.getComposition().getLine(adjustRect.line);
        var note = line.getNote(adjustRect.xIndex);

        switch (adjustRect.type) {
            case ATTRIBUTION -> getAttributionAdjustRect(adjustRect);
            case TOP_SPACE, ROW_HEIGHT -> getHeightAdjustRect(adjustRect);
            case TEMPO_CHANGE -> {
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findAttachmentBounds(note, TempoAttachment.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for TempoAttachment");
                }

                adjustRect.rect.x = note.getXPos() - 8;
                adjustRect.rect.y = (int) bounds.getTop() - 8;
            }
            case BEAT_CHANGE -> {
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findAttachmentBounds(note, BeatChangeAttachment.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for BeatChangeAttachment");
                }

                adjustRect.rect.x = note.getXPos() - 8;
                adjustRect.rect.y = (int) bounds.getTop() - 8;
            }
            case FIRST_SECOND_ENDING -> {
                var interval = line.getFirstSecondEndings().findInterval(adjustRect.xIndex);

                if (interval == null) {
                    return;
                }

                var startNote = line.getNote(interval.getStart());
                var endNote = line.getNote(interval.getEnd());
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findRangeElementBounds(startNote, endNote, Ending.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for Ending");
                }

                adjustRect.rect.x = startNote.getXPos() - 8;
                adjustRect.rect.y = (int) bounds.getTop() - 8;
            }
            case ANNOTATION -> {
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findAttachmentBounds(note, AnnotationAttachment.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for AnnotationAttachment");
                }

                adjustRect.rect.x = (int) bounds.getLeft() - 8;
                adjustRect.rect.y = (int) bounds.getTop() - 8;
            }
            case TRILL -> {
                // Find the Trill range element containing this note
                var trill = line.getRangeElements().stream()
                    .filter(e -> e instanceof Trill)
                    .map(e -> (Trill) e)
                    .filter(t -> {
                        var anchorIdx = t.getAnchorNoteIndex();
                        var endIdx = t.getEndNoteIndex();
                        return anchorIdx >= 0 && endIdx >= 0 &&
                               adjustRect.xIndex >= anchorIdx &&
                               adjustRect.xIndex <= endIdx;
                    })
                    .findFirst()
                    .orElse(null);

                if (trill == null) {
                    return;
                }

                var startNote = trill.getAnchorNote();
                var endNote = trill.getEndNote();

                if (startNote == null || endNote == null) {
                    return;
                }

                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findRangeElementBounds(startNote, endNote, Trill.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for Trill");
                }

                adjustRect.rect.x = startNote.getXPos() - 12;
                adjustRect.rect.y = (int) bounds.getTop() - 8;
            }
            case CRESCENDO_Y, DIMINUENDO_Y -> {
                var interval = getCresDecrIntervalSet(line, adjustRect.type)
                    .findInterval(adjustRect.xIndex);

                if (interval == null) {
                    return;
                }

                var startNote = line.getNote(interval.getStart());
                var endNote = line.getNote(interval.getEnd());
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var rangeClass = adjustRect.type == AdjustType.CRESCENDO_Y
                    ? Crescendo.class
                    : Diminuendo.class;
                var bounds = layoutResult.findRangeElementBounds(startNote, endNote, rangeClass);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for dynamics element");
                }

                // Position handle at center of dynamics hairpin
                var startX = startNote.getXPos();
                var endX = endNote.getXPos();
                adjustRect.rect.x = (startX + endX + 12) / 2;
                adjustRect.rect.y = (int) bounds.getTop();
            }
            case TUPLET -> {
                var interval = line.getTuplets().findInterval(adjustRect.xIndex);

                if (interval == null) {
                    return;
                }

                var startNote = line.getNote(interval.getStart());
                var endNote = line.getNote(interval.getEnd());
                var layoutResult = getLayoutResultForLine(adjustRect.line);
                var bounds = layoutResult.findRangeElementBounds(startNote, endNote, Tuplet.class);

                if (bounds == null) {
                    throw new IllegalStateException("No bounds found for Tuplet");
                }

                adjustRect.rect.x = startNote.getXPos() + (startNote.isUpper() ? 0 : -10);
                adjustRect.rect.y = (int) bounds.getTop();
            }
        }

        adjustRect.rect.width = 8;
        adjustRect.rect.height = 8;
    }

    private void getAttributionAdjustRect(AdjustRect adjustRect) {
        adjustRect.rect.x = score.getSheetWidth() - 8;
        adjustRect.rect.y = score.getComposition().getAttributionStartY();
    }

    private void getHeightAdjustRect(AdjustRect adjustRect) {
        adjustRect.rect.x = 0;
        adjustRect.rect.y = score.getNoteYPos(0, adjustRect.line) - 4;
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
    @NotNull
    private LayoutResult getLayoutResultForLine(int lineIndex) {
        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            throw new IllegalStateException("MainPanel not available");
        }

        var staffPanel = mainPanel.getStaffPanel();

        if (staffPanel == null) {
            throw new IllegalStateException("StaffPanel not available");
        }

        var linePanels = staffPanel.getLinePanels();

        if (lineIndex < 0 || lineIndex >= linePanels.size()) {
            throw new IllegalStateException("Invalid line index: " + lineIndex);
        }

        var lineComponent = linePanels.get(lineIndex).getLineComponent();
        var layoutResult = lineComponent.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result not available for line " + lineIndex);
        }

        return layoutResult;
    }

    private static IntervalSet<DynamicsInterval> getCresDecrIntervalSet(
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
