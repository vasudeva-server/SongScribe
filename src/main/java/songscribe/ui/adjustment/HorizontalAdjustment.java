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

import songscribe.model.Line;
import songscribe.model.StaffElement;
import songscribe.ui.component.ScoreView;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.Hairpin;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.renderer.GlissandoRenderer;
import songscribe.ui.renderer.GraphicsState;

public class HorizontalAdjustment extends Adjustment {

    private static final int END_SNAP_LIMIT = 30;
    private final ArrayList<AdjustRect> adjustRects = new ArrayList<>();

    @Nullable
    private AdjustRect draggingRect;

    private float @Nullable [] stretchHelper;

    public HorizontalAdjustment(ScoreView score) {
        super(score);
    }

    @Override
    protected void startedDrag() {
        if (startPoint == null) {
            return;
        }

        draggingRect = adjustRects
            .stream()
            .filter(adjustRect -> adjustRect.rect.contains(startPoint))
            .findFirst()
            .orElse(null);

        if (draggingRect == null) {
            startedDrag = false;
            return;
        }

        var song = scoreView.getSong();
        var line = song.getLine(draggingRect.line);
        var lineWidth = song.getLineWidthPx();

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

        var song = scoreView.getSong();
        var line = song.getLine(draggingRect.line);
        var note = line.getElement(draggingRect.xIndex);

        // The terminal's position is owned by layout; skip snap-to-end for it.
        if (
            song.isInteractable(note, line) &&
                note.getType().snapToEnd() &&
                ((song.getLineWidthPx() - endPoint.x) < END_SNAP_LIMIT)
        ) {
            endPoint.x = (int) (song.getLineWidthPx() - note.getContentWidthPx());
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

            line.changeElementSpacingRatio(ratio);
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
            for (var currentLine : song.getLines()) {
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
            var hairpin = findHairpinByAnchor(line, draggingRect);

            if (hairpin != null) {
                hairpin.setX1ShiftSs((hairpin.getX1ShiftSs() + endPoint.x) - diffX);
            }
        } else if (
            (draggingRect.horizontalAdjustmentType ==
                HorizontalAdjustmentType.CRESCENDO_END) ||
                (draggingRect.horizontalAdjustmentType ==
                    HorizontalAdjustmentType.DIMINUENDO_END)
        ) {
            var hairpin = findHairpinByEnd(line, draggingRect);

            if (hairpin != null) {
                hairpin.setX2ShiftSs((hairpin.getX2ShiftSs() + endPoint.x) - diffX);
            }
        }

        song.setModified(true);
        revalidateRects();
        scoreView.repaint();
    }

    @Override
    protected void finishedDrag() {
        draggingRect = null;
    }

    @Override
    public void repaint(Graphics2D g2) {
        for (var ar : adjustRects) {
            try (var ignored = GraphicsState.save(
                g2,
                GraphicsState.Property.COLOR
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
            var song = scoreView.getSong();

            for (
                var lineIndex = 0;
                lineIndex < song.lineCount();
                lineIndex++
            ) {
                var line = song.getLine(lineIndex);

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
                for (var crescendo : line.getCrescendos()) {
                    adjustRects.add(new AdjustRect(
                        lineIndex,
                        crescendo.getAnchorElementIndex(),
                        HorizontalAdjustmentType.CRESCENDO_START
                    ));
                    adjustRects.add(new AdjustRect(
                        lineIndex,
                        crescendo.getEndElementIndex(),
                        HorizontalAdjustmentType.CRESCENDO_END
                    ));
                }

                // Add DIMINUENDO
                for (var diminuendo : line.getDiminuendos()) {
                    adjustRects.add(new AdjustRect(
                        lineIndex,
                        diminuendo.getAnchorElementIndex(),
                        HorizontalAdjustmentType.DIMINUENDO_START
                    ));
                    adjustRects.add(new AdjustRect(
                        lineIndex,
                        diminuendo.getEndElementIndex(),
                        HorizontalAdjustmentType.DIMINUENDO_END
                    ));
                }
            }

            // Add FIRST_NOTE
            if (song.getLine(0).effectiveElementCount() > 0) {
                adjustRects.add(
                    new AdjustRect(0, 0, HorizontalAdjustmentType.START_OF_LINE)
                );
            }
        } else {
            adjustRects.clear();
        }
    }

    private void getAdjustRect(AdjustRect rect) {
        var line = scoreView.getSong().getLine(rect.line);
        var note = line.getElement(rect.xIndex);
        var yPosPx = scoreView.getNoteYPosPx(
            rect.horizontalAdjustmentType.getStaffPosition(),
            rect.line
        );
        rect.rect.y = yPosPx + ScaleContext.ssToRoundedPx(
            note.getType().getTopYOffsetSs(note.isUpper()));
        var lineComponent = scoreView.getLineComponent(rect.line);

        if (lineComponent == null) {
            return;
        }

        var layoutResult = lineComponent.getLayoutResult();

        switch (rect.horizontalAdjustmentType) {
            case GLISSANDO_START -> {
                var glissando = note.getGlissando();

                if (layoutResult != null && glissando != null) {
                    rect.rect.x = (int) Math.round(
                        ScaleContext.ssToPx(
                            GlissandoRenderer.getGlissandoX1Ss(
                                rect.xIndex,
                                glissando,
                                rect.line,
                                scoreView.getSong(),
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
                        ScaleContext.ssToPx(
                            GlissandoRenderer.getGlissandoX2Ss(
                                rect.xIndex,
                                glissando,
                                rect.line,
                                scoreView.getSong(),
                                layoutResult,
                                lineComponent.getMiddleLineYSs()
                            )
                        )
                    ) - 4;
                }
            }
            case CRESCENDO_START, DIMINUENDO_START -> {
                var hairpin = findHairpinByAnchor(line, rect);

                if (hairpin != null) {
                    rect.rect.x = (int) ((line.getElement(rect.xIndex).getXOffsetPx() - 12) +
                        hairpin.getX1ShiftSs());
                    rect.rect.y = (int) ((scoreView.getNoteYPosPx(6, rect.line) - 4) +
                        hairpin.getYShiftSs());
                }
            }
            case CRESCENDO_END, DIMINUENDO_END -> {
                var hairpin = findHairpinByEnd(line, rect);

                if (hairpin != null) {
                    rect.rect.x = (int) (line.getElement(rect.xIndex).getXOffsetPx() +
                        16 +
                        hairpin.getX2ShiftSs());
                    rect.rect.y = (int) ((scoreView.getNoteYPosPx(6, rect.line) - 4) +
                        hairpin.getYShiftSs());
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

    @Nullable
    private static Hairpin findHairpinByAnchor(Line line, AdjustRect rect) {
        var isCrescendo = rect.horizontalAdjustmentType == HorizontalAdjustmentType.CRESCENDO_START;
        var type = isCrescendo ? Crescendo.class : Diminuendo.class;

        for (var hairpin : line.findRangeElements(type)) {
            if (hairpin.getAnchorElementIndex() == rect.xIndex) {
                return hairpin;
            }
        }

        return null;
    }

    @Nullable
    private static Hairpin findHairpinByEnd(Line line, AdjustRect rect) {
        var isCrescendo = rect.horizontalAdjustmentType == HorizontalAdjustmentType.CRESCENDO_END;
        var type = isCrescendo ? Crescendo.class : Diminuendo.class;

        for (var hairpin : line.findRangeElements(type)) {
            if (hairpin.getEndElementIndex() == rect.xIndex) {
                return hairpin;
            }
        }

        return null;
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
