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

import org.jetbrains.annotations.Nullable;

import songscribe.data.DynamicsIntervalData;
import songscribe.data.IntervalSet;
import songscribe.data.TupletIntervalData;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.component.Score;
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

    private static void adjustTempoChange(Line line, int diffY) {
        line.setTempoChangeYPos(line.getTempoChangeYPos() + diffY);
    }

    private static void adjustBeatChange(Line line, int diffY) {
        line.setBeatChangeYPos(line.getBeatChangeYPos() + diffY);
    }

    private static void adjustFirstSecondEnding(Line line, int diffY) {
        line.setFirstSecondEndingYPos(line.getFirstSecondEndingYPos() + diffY);
    }

    private void adjustAnnotation(Line line, int diffY) {
        if (dragRect != null) {
            var annotation = line.getNote(dragRect.xIndex).getAnnotation();
            annotation.setYPos(annotation.getYPos() + diffY);
        }
    }

    private static void adjustTrill(Line line, int diffY) {
        line.setTrillYPos(line.getTrillYPos() + diffY);
    }

    private void adjustDynamics(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var interval = getCresDecrIntervalSet(line, dragRect.type).findInterval(
            dragRect.xIndex
        );

        if (interval != null) {
            DynamicsIntervalData.setYShift(
                interval,
                DynamicsIntervalData.getYShift(interval) + diffY
            );
        }
    }

    private void adjustTuplet(Line line, int diffY) {
        if (dragRect == null) {
            return;
        }

        var interval = line.getTuplets().findInterval(dragRect.xIndex);

        if (interval != null) {
            TupletIntervalData.setVerticalPosition(
                interval,
                TupletIntervalData.getVerticalPosition(interval) + diffY
            );
        }
    }

    @Override
    protected void finishedDrag() {
        dragRect = null;
    }

    @Override
    public void repaint(Graphics2D g2) {
        for (var ar : adjustRects) {
            g2.setPaint(ar.type.getColor());
            g2.fill(ar.rect);
            g2.setPaint(Color.black);
            g2.draw(ar.rect);
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
            case TEMPO_CHANGE -> getChangeAdjustRect(
                adjustRect,
                line,
                note,
                8,
                score.getMeasurementService().getEffectiveTempoChangeYPos(
                    (Graphics2D) score.getGraphics(),
                    line,
                    adjustRect.line
                )
            );
            case BEAT_CHANGE -> getChangeAdjustRect(
                adjustRect,
                line,
                note,
                8,
                line.getBeatChangeYPos()
            );
            case FIRST_SECOND_ENDING -> getChangeAdjustRect(
                adjustRect,
                line,
                note,
                8,
                line.getFirstSecondEndingYPos()
            );
            case ANNOTATION -> {
                var measurementService = score.getMeasurementService();
                var x = measurementService.getAnnotationXPos(
                    (Graphics2D) score.getGraphics(),
                    note
                );
                adjustRect.rect.x = (int) Math.round(x) - 8;

                var y = measurementService.getAnnotationYPos(adjustRect.line, note);
                adjustRect.rect.y = y - 8;
            }
            case TRILL -> getChangeAdjustRect(
                adjustRect,
                line,
                note,
                12,
                line.getTrillYPos()
            );
            case CRESCENDO_Y, DIMINUENDO_Y -> {
                var interval = getCresDecrIntervalSet(
                    line,
                    adjustRect.type
                ).findInterval(adjustRect.xIndex);

                if (interval == null) {
                    return;
                }

                var startX = line.getNote(interval.getStart()).getXPos();
                var endX = line.getNote(interval.getEnd()).getXPos();
                adjustRect.rect.x = (startX + endX + 12) / 2;

                var y = score.getNoteYPos(6, adjustRect.line) - 4;
                var yShift = DynamicsIntervalData.getYShift(interval);
                adjustRect.rect.y = y + yShift;
            }
            case TUPLET -> {
                var interval = line
                    .getTuplets()
                    .findInterval(adjustRect.xIndex);

                if (interval == null) {
                    return;
                }

                note = line.getNote(interval.getStart());
                adjustRect.rect.x = note.getXPos() + (note.isUpper() ? 0 : -10);
                adjustRect.rect.y = score.getNoteYPos(
                    note.getYPos() + (note.isUpper() ? -10 : -3),
                    adjustRect.line
                ) +
                TupletIntervalData.getVerticalPosition(interval);
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

    private void getChangeAdjustRect(
        AdjustRect adjustRect,
        Line line,
        Note note,
        int xOffset,
        int yPos
    ) {
        adjustRect.rect.x = note.getXPos() - xOffset;
        var y = score.getNoteYPos(0, adjustRect.line);
        adjustRect.rect.y = (y + yPos) - 8;
    }

    private void revalidateRects() {
        for (var ar : adjustRects) {
            getAdjustRect(ar);
        }
    }

    private static IntervalSet getCresDecrIntervalSet(
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
