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
import songscribe.music.GraceSemiQuaver;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.component.Score;
import songscribe.ui.layout.LayoutManager;
import songscribe.ui.renderer.GlissandoRenderer;

public class HorizontalAdjustment extends Adjustment {

    private static final int END_SNAP_LIMIT = 30;
    private final ArrayList<AdjustRect> adjustRects = new ArrayList<>();

    @Nullable
    private AdjustRect draggingRect;

    private float[] stretchHelper = null;

    public HorizontalAdjustment(Score score) {
        super(score);
    }

    @Override
    protected void startedDrag() {
        draggingRect = adjustRects
            .stream()
            .filter(adjustRect -> adjustRect.rect.contains(startPoint))
            .findFirst()
            .orElse(null);

        if (draggingRect == null) {
            startedDrag = false;
            return;
        }

        var composition = score.getComposition();
        var line = composition.getLine(draggingRect.line);
        var lineWidth = composition.getLineWidth();

        if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.SINGLE_NOTE
        ) {
            topLeftDragBounds.setLocation(
                ((draggingRect.xIndex > 0)
                        ? line.getNote(draggingRect.xIndex - 1).getXPos()
                        : 20) +
                draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                (draggingRect.xIndex < (line.noteCount() - 1))
                    ? (line.getNote(draggingRect.xIndex + 1).getXPos() -
                        draggingRect.rect.width)
                    : lineWidth,
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.TO_END_OF_LINE
        ) {
            topLeftDragBounds.setLocation(
                line.getNote(draggingRect.xIndex - 1).getXPos() +
                draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                ((draggingRect.rect.x - draggingRect.rect.width) + lineWidth) -
                line.getNote(line.noteCount() - 1).getXPos(),
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.STRETCH_NOTE_SPACING
        ) {
            topLeftDragBounds.setLocation(
                line.getNote(0).getXPos(),
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                lineWidth + draggingRect.rect.width,
                draggingRect.rect.y
            );

            if (
                (stretchHelper == null) ||
                (stretchHelper.length < line.noteCount())
            ) {
                stretchHelper = new float[line.noteCount()];
            }

            for (var i = 0; i < line.noteCount(); i++) {
                stretchHelper[i] = line.getNote(i).getXPos();
            }
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.START_OF_LINE
        ) {
            topLeftDragBounds.setLocation(
                draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                ((draggingRect.rect.x - draggingRect.rect.width) + lineWidth) -
                line.getNote(line.noteCount() - 1).getXPos(),
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.GLISSANDO_START
        ) {
            topLeftDragBounds.setLocation(
                line.getNote(draggingRect.xIndex).getXPos(),
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                adjustRects.get(adjustRects.indexOf(draggingRect) + 1).rect.x,
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.GLISSANDO_END
        ) {
            topLeftDragBounds.setLocation(
                adjustRects.get(adjustRects.indexOf(draggingRect) - 1).rect.x +
                draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(lineWidth, draggingRect.rect.y);
        } else if (
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_START) ||
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_END) ||
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.DIMINUENDO_START) ||
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.DIMINUENDO_END)
        ) {
            topLeftDragBounds.setLocation(
                (draggingRect.xIndex == 0)
                    ? 0
                    : line.getNote(draggingRect.xIndex - 1).getXPos(),
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                (draggingRect.xIndex == (line.noteCount() - 1))
                    ? lineWidth
                    : line.getNote(draggingRect.xIndex + 1).getXPos(),
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.GRACE_SEMIQUAVER_2ND_PART
        ) {
            topLeftDragBounds.setLocation(
                line.getNote(draggingRect.xIndex).getXPos() +
                draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                (draggingRect.xIndex < (line.noteCount() - 1))
                    ? (line.getNote(draggingRect.xIndex + 1).getXPos() -
                        draggingRect.rect.width)
                    : lineWidth,
                draggingRect.rect.y
            );
        }
    }

    @Override
    protected void drag() {
        if (draggingRect == null) {
            return;
        }

        var composition = score.getComposition();
        var line = composition.getLine(draggingRect.line);
        var note = line.getNote(draggingRect.xIndex);

        if (
            note.getNoteType().snapToEnd() &&
            ((composition.getLineWidth() - endPoint.x) < END_SNAP_LIMIT)
        ) {
            endPoint.x = composition.getLineWidth() - Note.NORMAL_IMAGE_WIDTH;
        }

        var diffX = draggingRect.rect.x + (draggingRect.rect.width / 2);
        draggingRect.rect.x = endPoint.x - (draggingRect.rect.width / 2);

        if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.STRETCH_NOTE_SPACING
        ) {
            var ratio = (float) endPoint.x / note.getXPos();
            var firstX = line.getNote(0).getXPos();

            for (var i = 0; i < line.noteCount(); i++) {
                stretchHelper[i] = firstX +
                ((stretchHelper[i] - firstX) * ratio);
                line.getNote(i).setXPos(Math.round(stretchHelper[i]));
            }

            line.mulNoteDistChange(ratio);
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.TO_END_OF_LINE
        ) {
            for (var i = draggingRect.xIndex; i < line.noteCount(); i++) {
                line
                    .getNote(i)
                    .setXPos((line.getNote(i).getXPos() + endPoint.x) - diffX);
            }
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.SINGLE_NOTE
        ) {
            note.setXPos(endPoint.x);
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.START_OF_LINE
        ) {
            LayoutManager.setFirstNoteX(endPoint.x);

            for (var currentLine : composition.getLines()) {
                if (currentLine.noteCount() > 0) {
                    var diff = endPoint.x - currentLine.getNote(0).getXPos();
                    currentLine.getNote(0).setXPos(endPoint.x);

                    for (var j = 1; j < currentLine.noteCount(); j++) {
                        currentLine
                            .getNote(j)
                            .setXPos(currentLine.getNote(j).getXPos() + diff);
                    }
                }
            }
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.GLISSANDO_START
        ) {
            note.getGlissando().x1Translate += endPoint.x - diffX;
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.GLISSANDO_END
        ) {
            note.getGlissando().x2Translate -= endPoint.x - diffX;
        } else if (
            draggingRect.horizontalAdjustmentType ==
            HorizontalAdjustmentType.GRACE_SEMIQUAVER_2ND_PART
        ) {
            ((GraceSemiQuaver) note).setX2DiffPos(endPoint.x - note.getXPos());
        } else if (
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_START) ||
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.DIMINUENDO_START)
        ) {
            var interval = getDynamicIntervalSet(
                line,
                draggingRect.horizontalAdjustmentType
            ).findInterval(draggingRect.xIndex);

            if (interval != null) {
                DynamicsIntervalData.setX1Shift(
                    interval,
                    (DynamicsIntervalData.getX1Shift(interval) + endPoint.x) -
                    diffX
                );
            }
        } else if (
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_END) ||
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.DIMINUENDO_END)
        ) {
            var interval = getDynamicIntervalSet(
                line,
                draggingRect.horizontalAdjustmentType
            ).findInterval(draggingRect.xIndex);

            if (interval != null) {
                DynamicsIntervalData.setX2Shift(
                    interval,
                    (DynamicsIntervalData.getX2Shift(interval) + endPoint.x) -
                    diffX
                );
            }
        }

        composition.setModified(true);
        revalidateRects();
        score.repaint();
    }

    @Override
    protected void finishedDrag() {
        draggingRect = null;
    }

    @Override
    public void repaint(Graphics2D g2) {
        for (var ar : adjustRects) {
            g2.setPaint(ar.horizontalAdjustmentType.getColor());
            g2.fill(ar.rect);
            g2.setPaint(Color.black);
            g2.draw(ar.rect);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (enabled) {
            var composition = score.getComposition();

            for (
                var lineIndex = 0;
                lineIndex < composition.lineCount();
                lineIndex++
            ) {
                var line = composition.getLine(lineIndex);

                // Add ONE_NOTE
                for (var i = 0; i < line.noteCount(); i++) {
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            i,
                            HorizontalAdjustmentType.SINGLE_NOTE
                        )
                    );
                }

                // Add WHOLE_NOTE
                for (var i = 1; i < (line.noteCount() - 1); i++) {
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            i,
                            HorizontalAdjustmentType.TO_END_OF_LINE
                        )
                    );
                }

                // Add special this: GLISSANDO, GRACE_SEMIQUAVER
                for (var i = 0; i < line.noteCount(); i++) {
                    var note = line.getNote(i);

                    //noinspection ObjectEquality
                    if (note.getGlissando() != Note.NO_GLISSANDO) {
                        adjustRects.add(
                            new AdjustRect(
                                lineIndex,
                                i,
                                HorizontalAdjustmentType.GLISSANDO_START
                            )
                        );
                        adjustRects.add(
                            new AdjustRect(
                                lineIndex,
                                i,
                                HorizontalAdjustmentType.GLISSANDO_END
                            )
                        );
                    }

                    if (note.getNoteType() == NoteType.GRACE_SEMIQUAVER) {
                        adjustRects.add(
                            new AdjustRect(
                                lineIndex,
                                i,
                                HorizontalAdjustmentType.GRACE_SEMIQUAVER_2ND_PART
                            )
                        );
                    }
                }

                // Add STRETCH
                if (line.noteCount() > 0) {
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            line.noteCount() - 1,
                            HorizontalAdjustmentType.STRETCH_NOTE_SPACING
                        )
                    );
                }

                // Add CRESCENDO
                for (
                    var iter = line.getCrescendos().listIterator();
                    iter.hasNext();
                ) {
                    var interval = iter.next();
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            interval.getStart(),
                            HorizontalAdjustmentType.CRESCENDO_START
                        )
                    );
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            interval.getEnd(),
                            HorizontalAdjustmentType.CRESCENDO_END
                        )
                    );
                }

                // Add DIMINUENDO
                for (
                    var iter = line.getDiminuendos().listIterator();
                    iter.hasNext();
                ) {
                    var interval = iter.next();
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            interval.getStart(),
                            HorizontalAdjustmentType.DIMINUENDO_START
                        )
                    );
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            interval.getEnd(),
                            HorizontalAdjustmentType.DIMINUENDO_END
                        )
                    );
                }
            }

            // Add FIRST_NOTE
            if (composition.getLine(0).noteCount() > 0) {
                adjustRects.add(
                    new AdjustRect(0, 0, HorizontalAdjustmentType.START_OF_LINE)
                );
            }
        } else {
            adjustRects.clear();
        }
    }

    private void getAdjustRect(AdjustRect rect) {
        var line = score.getComposition().getLine(rect.line);
        var note = line.getNote(rect.xIndex);
        var yPos = score.getNoteYPos(
            rect.horizontalAdjustmentType.getYPos(),
            rect.line
        );
        rect.rect.y = yPos - Note.HOT_SPOT.y;

        switch (rect.horizontalAdjustmentType) {
            case GLISSANDO_START -> rect.rect.x = GlissandoRenderer.getGlissandoX1Pos(
                rect.xIndex,
                note.getGlissando(),
                rect.line,
                score.getComposition()
            ) - 4;
            case GLISSANDO_END -> rect.rect.x = GlissandoRenderer.getGlissandoX2Pos(
                rect.xIndex,
                note.getGlissando(),
                rect.line,
                score.getComposition()
            ) - 4;
            case GRACE_SEMIQUAVER_2ND_PART -> rect.rect.x = note.getXPos() +
            ((GraceSemiQuaver) note).getX2DiffPos() +
            1;
            case CRESCENDO_START, DIMINUENDO_START -> {
                var x1Interval = getDynamicIntervalSet(
                    line,
                    rect.horizontalAdjustmentType
                ).findInterval(rect.xIndex);

                if (x1Interval != null) {
                    rect.rect.x = (line.getNote(rect.xIndex).getXPos() - 12) +
                    DynamicsIntervalData.getX1Shift(x1Interval);
                    rect.rect.y = (score.getNoteYPos(6, rect.line) - 4) +
                    DynamicsIntervalData.getYShift(x1Interval);
                }
            }
            case CRESCENDO_END, DIMINUENDO_END -> {
                var x2Interval = getDynamicIntervalSet(
                    line,
                    rect.horizontalAdjustmentType
                ).findInterval(rect.xIndex);

                if (x2Interval != null) {
                    rect.rect.x = line.getNote(rect.xIndex).getXPos() +
                    16 +
                    DynamicsIntervalData.getX2Shift(x2Interval);
                    rect.rect.y = (score.getNoteYPos(6, rect.line) - 4) +
                    DynamicsIntervalData.getYShift(x2Interval);
                }
            }
            default -> rect.rect.x = note.getXPos() + 1;
        }

        rect.rect.width = 8;
        rect.rect.height = 8;
    }

    private void revalidateRects() {
        for (var ar : adjustRects) {
            getAdjustRect(ar);
        }
    }

    private static IntervalSet getDynamicIntervalSet(
        Line line,
        HorizontalAdjustmentType horizontalAdjustmentType
    ) {
        return switch (horizontalAdjustmentType) {
            case CRESCENDO_START, CRESCENDO_END -> line.getCrescendos();
            case DIMINUENDO_START, DIMINUENDO_END -> line.getDiminuendos();
            default -> throw new IllegalArgumentException(
                String.valueOf(horizontalAdjustmentType)
            );
        };
    }

    private enum HorizontalAdjustmentType {
        // A single note is being shifted
        SINGLE_NOTE(Color.white, -1),

        // A note after the first note on a line, and all notes after it, are being shifted
        TO_END_OF_LINE(Color.blue, -4),

        // The spacing between notes is being stretched
        STRETCH_NOTE_SPACING(Color.yellow, -4),

        // The starting position for the first note on all lines is being shifted
        START_OF_LINE(Color.green, -4),

        // The start/end of a glissando is being shifted
        GLISSANDO_START(Color.magenta, -2),
        GLISSANDO_END(Color.magenta, -2),

        // The second part of a grace semiquaver is being shifted
        GRACE_SEMIQUAVER_2ND_PART(Color.white, -1),

        // The start/end of a crescendo/diminuendo is being shifted
        CRESCENDO_START(Color.orange, 6),
        CRESCENDO_END(Color.orange, 6),
        DIMINUENDO_START(Color.orange, 6),
        DIMINUENDO_END(Color.orange, 6);

        private final Color color;
        private final int yPos;

        HorizontalAdjustmentType(Color color, int yPos) {
            this.color = color;
            this.yPos = yPos;
        }

        public Color getColor() {
            return color;
        }

        public int getYPos() {
            return yPos;
        }
    }

    private class AdjustRect {

        final Rectangle rect;
        final int line;
        final int xIndex;
        final HorizontalAdjustmentType horizontalAdjustmentType;

        AdjustRect(
            int line,
            int xIndex,
            HorizontalAdjustmentType horizontalAdjustmentType
        ) {
            this.line = line;
            this.xIndex = xIndex;
            this.horizontalAdjustmentType = horizontalAdjustmentType;
            rect = new Rectangle();
            getAdjustRect(this);
        }
    }
}
