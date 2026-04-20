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
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.component.Score;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.renderer.GlissandoRenderer;

public class HorizontalAdjustment extends Adjustment {

    private static final int END_SNAP_LIMIT = 30;
    private final ArrayList<AdjustRect> adjustRects = new ArrayList<>();

    @Nullable
    private AdjustRect draggingRect;

    private float @Nullable [] stretchHelper;

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
        var lineWidth = composition.getLineWidthPx();

        if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.SINGLE_NOTE
        ) {
            topLeftDragBounds.setLocation(
                ((draggingRect.xIndex > 0)
                    ? line.getElement(draggingRect.xIndex - 1).getXOffsetPx()
                    : 20) +
                    draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                (draggingRect.xIndex < (line.effectiveElementCount() - 1))
                    ? (line.getElement(draggingRect.xIndex + 1).getXOffsetPx() -
                    draggingRect.rect.width)
                    : lineWidth,
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.TO_END_OF_LINE
        ) {
            topLeftDragBounds.setLocation(
                line.getElement(draggingRect.xIndex - 1).getXOffsetPx() +
                    draggingRect.rect.width,
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                ((draggingRect.rect.x - draggingRect.rect.width) + lineWidth) -
                    line.getElement(line.elementCount() - 1).getXOffsetPx(),
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.STRETCH_NOTE_SPACING
        ) {
            topLeftDragBounds.setLocation(
                line.getElement(0).getXOffsetPx(),
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                lineWidth + draggingRect.rect.width,
                draggingRect.rect.y
            );

            if (
                (stretchHelper == null) ||
                    (stretchHelper.length < line.effectiveElementCount())
            ) {
                stretchHelper = new float[line.effectiveElementCount()];
            }

            for (var i = 0; i < line.effectiveElementCount(); i++) {
                stretchHelper[i] = line.getElement(i).getXOffsetPx();
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
                    line.getElement(line.elementCount() - 1).getXOffsetPx(),
                draggingRect.rect.y
            );
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.GLISSANDO_START
        ) {
            topLeftDragBounds.setLocation(
                line.getElement(draggingRect.xIndex).getXOffsetPx(),
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
                    : line.getElement(draggingRect.xIndex - 1).getXOffsetPx(),
                draggingRect.rect.y
            );
            bottomRightDragBounds.setLocation(
                (draggingRect.xIndex == (line.effectiveElementCount() - 1))
                    ? lineWidth
                    : line.getElement(draggingRect.xIndex + 1).getXOffsetPx(),
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
        var note = line.getElement(draggingRect.xIndex);

        // The terminal's position is owned by layout; skip snap-to-end for it.
        if (
            composition.isInteractable(note, line) &&
                note.getType().snapToEnd() &&
                ((composition.getLineWidthPx() - endPoint.x) < END_SNAP_LIMIT)
        ) {
            endPoint.x = (int) (composition.getLineWidthPx() - note.getContentWidthPx());
        }

        var diffX = draggingRect.rect.x + (draggingRect.rect.width / 2);
        draggingRect.rect.x = endPoint.x - (draggingRect.rect.width / 2);

        if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.STRETCH_NOTE_SPACING
        ) {
            var ratio = (float) endPoint.x / note.getXOffsetPx();
            var firstX = line.getElement(0).getXOffsetPx();

            if (stretchHelper != null) {
                for (var i = 0; i < line.effectiveElementCount(); i++) {
                    stretchHelper[i] = firstX +
                        ((stretchHelper[i] - firstX) * ratio);
                    line.getElement(i).setXOffsetPx(Math.round(stretchHelper[i]));
                }
            }

            line.mulElementDistChange(ratio);
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.TO_END_OF_LINE
        ) {
            for (var i = draggingRect.xIndex; i < line.effectiveElementCount(); i++) {
                line
                    .getElement(i)
                    .setXOffsetPx((line.getElement(i).getXOffsetPx() + endPoint.x) - diffX);
            }
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.SINGLE_NOTE
        ) {
            note.setXOffsetPx(endPoint.x);
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.START_OF_LINE
        ) {
            for (var currentLine : composition.getLines()) {
                if (currentLine.effectiveElementCount() > 0) {
                    var diff = endPoint.x - currentLine.getElement(0).getXOffsetPx();
                    currentLine.getElement(0).setXOffsetPx(endPoint.x);

                    for (var j = 1; j < currentLine.effectiveElementCount(); j++) {
                        currentLine
                            .getElement(j)
                            .setXOffsetPx(currentLine.getElement(j).getXOffsetPx() + diff);
                    }
                }
            }
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.GLISSANDO_START
        ) {
            var glissando = note.getGlissando();

            if (glissando != null) {
                glissando.x1Translate += endPoint.x - diffX;
            }
        } else if (
            draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.GLISSANDO_END
        ) {
            var glissando = note.getGlissando();

            if (glissando != null) {
                glissando.x2Translate += endPoint.x - diffX;
            }
        } else if (
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_START) ||
                (draggingRect.horizontalAdjustmentType ==
                    HorizontalAdjustmentType.DIMINUENDO_START)
        ) {
            var span = getDynamicSpanSet(
                line,
                draggingRect.horizontalAdjustmentType
            ).findSpan(draggingRect.xIndex);

            if (span != null) {
                span.setX1ShiftSs((span.getX1ShiftSs() + endPoint.x) - diffX);
            }
        } else if (
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_END) ||
                (draggingRect.horizontalAdjustmentType ==
                    HorizontalAdjustmentType.DIMINUENDO_END)
        ) {
            var span = getDynamicSpanSet(
                line,
                draggingRect.horizontalAdjustmentType
            ).findSpan(draggingRect.xIndex);

            if (span != null) {
                span.setX2ShiftSs((span.getX2ShiftSs() + endPoint.x) - diffX);
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
            try (var ignored = songscribe.ui.renderer.GraphicsState.save(
                g2,
                songscribe.ui.renderer.GraphicsState.Property.COLOR
            )) {
                g2.setPaint(ar.horizontalAdjustmentType.getColor());
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
            var composition = score.getComposition();

            for (
                var lineIndex = 0;
                lineIndex < composition.lineCount();
                lineIndex++
            ) {
                var line = composition.getLine(lineIndex);

                // Add ONE_NOTE
                for (var i = 0; i < line.effectiveElementCount(); i++) {
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            i,
                            HorizontalAdjustmentType.SINGLE_NOTE
                        )
                    );
                }

                // Add WHOLE_NOTE
                for (var i = 1; i < (line.effectiveElementCount() - 1); i++) {
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            i,
                            HorizontalAdjustmentType.TO_END_OF_LINE
                        )
                    );
                }

                // Add special adjustment rects for glissandos
                for (var i = 0; i < line.effectiveElementCount(); i++) {
                    var note = line.getElement(i);

                    if (note.getGlissando() != null) {
                        adjustRects.add(
                            new AdjustRect(
                                lineIndex,
                                i,
                                HorizontalAdjustmentType.GLISSANDO_START
                            )
                        );

                        // SLIDE_OUT glissandos have a fixed endpoint; x2Translate has no effect
                        if (note.getGlissando().type == StaffElement.Glissando.Type.CONNECTED) {
                            adjustRects.add(
                                new AdjustRect(
                                    lineIndex,
                                    i,
                                    HorizontalAdjustmentType.GLISSANDO_END
                                )
                            );
                        }
                    }
                }

                // Add STRETCH
                if (line.effectiveElementCount() > 0) {
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            line.effectiveElementCount() - 1,
                            HorizontalAdjustmentType.STRETCH_NOTE_SPACING
                        )
                    );
                }

                // Add CRESCENDO
                for (
                    var iter = line.getCrescendos().listIterator();
                    iter.hasNext();
                ) {
                    var span = iter.next();
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            span.getStart(),
                            HorizontalAdjustmentType.CRESCENDO_START
                        )
                    );
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            span.getEnd(),
                            HorizontalAdjustmentType.CRESCENDO_END
                        )
                    );
                }

                // Add DIMINUENDO
                for (
                    var iter = line.getDiminuendos().listIterator();
                    iter.hasNext();
                ) {
                    var span = iter.next();
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            span.getStart(),
                            HorizontalAdjustmentType.DIMINUENDO_START
                        )
                    );
                    adjustRects.add(
                        new AdjustRect(
                            lineIndex,
                            span.getEnd(),
                            HorizontalAdjustmentType.DIMINUENDO_END
                        )
                    );
                }
            }

            // Add FIRST_NOTE
            if (composition.getLine(0).effectiveElementCount() > 0) {
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
        var note = line.getElement(rect.xIndex);
        var yPosPx = score.getNoteYPosPx(
            rect.horizontalAdjustmentType.getStaffPosition(),
            rect.line
        );
        rect.rect.y = yPosPx + ScaleContext.getInstance().toRoundedPixels(
            note.getType().getTopYOffsetSs(note.isUpper()));
        var lineComponent = score.getLineComponent(rect.line);

        if (lineComponent == null) {
            return;
        }

        var layoutResult = lineComponent.getLayoutResult();

        switch (rect.horizontalAdjustmentType) {
            case GLISSANDO_START -> {
                var glissando = note.getGlissando();

                if (layoutResult != null && glissando != null) {
                    rect.rect.x = (int) Math.round(
                        ScaleContext.getInstance().toPixels(
                            GlissandoRenderer.getGlissandoX1Ss(
                                rect.xIndex,
                                glissando,
                                rect.line,
                                score.getComposition(),
                                layoutResult,
                                lineComponent.getMiddleLineYSs()
                            )
                        )
                    ) - 4;
                }
            }
            case GLISSANDO_END -> {
                var glissando = note.getGlissando();

                if (layoutResult != null && glissando != null) {
                    rect.rect.x = (int) Math.round(
                        ScaleContext.getInstance().toPixels(
                            GlissandoRenderer.getGlissandoX2Ss(
                                rect.xIndex,
                                glissando,
                                rect.line,
                                score.getComposition(),
                                layoutResult,
                                lineComponent.getMiddleLineYSs()
                            )
                        )
                    ) - 4;
                }
            }
            case CRESCENDO_START, DIMINUENDO_START -> {
                var x1Span = getDynamicSpanSet(
                    line,
                    rect.horizontalAdjustmentType
                ).findSpan(rect.xIndex);

                if (x1Span != null) {
                    rect.rect.x = (int) ((line.getElement(rect.xIndex).getXOffsetPx() - 12) +
                        x1Span.getX1ShiftSs());
                    rect.rect.y = (int) ((score.getNoteYPosPx(6, rect.line) - 4) +
                        x1Span.getYShiftSs());
                }
            }
            case CRESCENDO_END, DIMINUENDO_END -> {
                var x2Span = getDynamicSpanSet(
                    line,
                    rect.horizontalAdjustmentType
                ).findSpan(rect.xIndex);

                if (x2Span != null) {
                    rect.rect.x = (int) (line.getElement(rect.xIndex).getXOffsetPx() +
                        16 +
                        x2Span.getX2ShiftSs());
                    rect.rect.y = (int) ((score.getNoteYPosPx(6, rect.line) - 4) +
                        x2Span.getYShiftSs());
                }
            }
            default -> rect.rect.x = note.getXOffsetPx() + 1;
        }

        rect.rect.width = 8;
        rect.rect.height = 8;
    }

    private void revalidateRects() {
        for (var ar : adjustRects) {
            getAdjustRect(ar);
        }
    }

    private static SpanSet<DynamicsSpan> getDynamicSpanSet(
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

        // The start/end of a crescendo/diminuendo is being shifted
        CRESCENDO_START(Color.orange, 6),
        CRESCENDO_END(Color.orange, 6),
        DIMINUENDO_START(Color.orange, 6),
        DIMINUENDO_END(Color.orange, 6);

        private final Color color;
        private final int staffPosition;

        HorizontalAdjustmentType(Color color, int staffPosition) {
            this.color = color;
            this.staffPosition = staffPosition;
        }

        public Color getColor() {
            return color;
        }

        public int getStaffPosition() {
            return staffPosition;
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
