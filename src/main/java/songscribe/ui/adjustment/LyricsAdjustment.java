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

import songscribe.music.StaffElement;
import songscribe.ui.Constants;
import songscribe.ui.component.Score;

public class LyricsAdjustment extends Adjustment {

    @Nullable
    private AdjustRect draggingRect;

    private final ArrayList<AdjustRect> adjustRects = new ArrayList<>();

    public LyricsAdjustment(Score score) {
        super(score);
    }

    @Override
    protected void startedDrag() {
        draggingRect = adjustRects
            .stream()
            .filter(ar -> ar.rectangle.contains(startPoint))
            .findFirst()
            .orElse(null);

        if (draggingRect == null) {
            startedDrag = false;
        } else {
            var line = score.getComposition().getLine(draggingRect.line);

            if (draggingRect.adjustType == AdjustType.SYLLABLE_MOVEMENT) {
                topLeftDragBounds.setLocation(
                    ((draggingRect.xIndex > 0)
                        ? line.getElement(draggingRect.xIndex - 1).getXOffsetPx()
                        : 0) +
                        draggingRect.rectangle.width,
                    draggingRect.rectangle.y
                );
                bottomRightDragBounds.setLocation(
                    (draggingRect.xIndex < (line.effectiveElementCount() - 1))
                        ? line.getElement(draggingRect.xIndex + 1).getXOffsetPx()
                        : score.getComposition().getLineWidthSs(),
                    draggingRect.rectangle.y
                );
            } else if (
                draggingRect.adjustType == AdjustType.SYLLABLE_RELATION_MOVEMENT
            ) {
                topLeftDragBounds.setLocation(
                    line.getElement(draggingRect.xIndex).getXOffsetPx(),
                    draggingRect.rectangle.y
                );
                bottomRightDragBounds.setLocation(
                    score.getComposition().getLineWidthSs(),
                    draggingRect.rectangle.y
                );
            } else if (draggingRect.adjustType == AdjustType.LYRICS_YPOS) {
                topLeftDragBounds.setLocation(
                    draggingRect.rectangle.x,
                    score.getNoteYPosPx(6, draggingRect.line)
                );
                bottomRightDragBounds.setLocation(
                    draggingRect.rectangle.x,
                    score.getNoteYPosPx(-4, draggingRect.line + 1)
                );
            }
        }
    }

    @Override
    protected void drag() {
        if (draggingRect == null) {
            return;
        }

        if (draggingRect.adjustType == AdjustType.SYLLABLE_MOVEMENT) {
            draggingRect.rectangle.x = endPoint.x -
                (draggingRect.rectangle.width / 2);
            var note = score
                .getComposition()
                .getLine(draggingRect.line)
                .getElement(draggingRect.xIndex);
            note.setSyllableMovement(endPoint.x - note.getXOffsetPx());
        } else if (
            draggingRect.adjustType == AdjustType.SYLLABLE_RELATION_MOVEMENT
        ) {
            draggingRect.rectangle.x = endPoint.x -
                (draggingRect.rectangle.width / 2);
            var note = score
                .getComposition()
                .getLine(draggingRect.line)
                .getElement(draggingRect.xIndex);
            note.setSyllableRelationMovement(endPoint.x - note.getXOffsetPx());
        } else if (draggingRect.adjustType == AdjustType.LYRICS_YPOS) {
            var diffY =
                draggingRect.rectangle.y + (draggingRect.rectangle.height / 2);
            draggingRect.rectangle.y = endPoint.y -
                (draggingRect.rectangle.height / 2);
            var line = score.getComposition().getLine(draggingRect.line);
            line.setLyricsYPosSs((line.getLyricsYPosSs() + endPoint.y) - diffY);
        }

        score.viewChanged();
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
                g2.setPaint(ar.adjustType.getColor());
                g2.fill(ar.rectangle);
                g2.setPaint(Color.black);
                g2.draw(ar.rectangle);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (enabled) {
            var c = score.getComposition();

            for (var l = 0; l < c.lineCount(); l++) {
                var line = c.getLine(l);
                var foundLyrics = -1;

                for (var n = 0; n < line.effectiveElementCount(); n++) {
                    var syllable = line.getElement(n).properties.syllable;

                    if (syllable != null && !syllable.isEmpty()) {
                        if (!syllable.equals(Constants.UNDERSCORE)) {
                            adjustRects.add(
                                new AdjustRect(
                                    l,
                                    AdjustType.SYLLABLE_MOVEMENT,
                                    n
                                )
                            );
                        }

                        if (
                            line.getElement(n).properties.syllableRelation ==
                                StaffElement.SyllableRelation.ONE_DASH
                        ) {
                            adjustRects.add(
                                new AdjustRect(
                                    l,
                                    AdjustType.SYLLABLE_RELATION_MOVEMENT,
                                    n
                                )
                            );
                        }

                        if (foundLyrics == -1) {
                            foundLyrics = n;
                        }
                    }
                }

                if (foundLyrics > -1) {
                    adjustRects.add(
                        new AdjustRect(l, AdjustType.LYRICS_YPOS, foundLyrics)
                    );
                }
            }
        } else {
            adjustRects.clear();
        }
    }

    private void getRectangle(AdjustRect rect) {
        var line = score.getComposition().getLine(rect.line);
        var note = line.getElement(rect.xIndex);

        if (rect.adjustType == AdjustType.LYRICS_YPOS) {
            rect.rectangle.x = note.getXOffsetPx() - 20;
            rect.rectangle.y = (int) ((score.getNoteYPosPx(0, rect.line) +
                line.getLyricsYPosSs()) -
                8);
        } else if (rect.adjustType == AdjustType.SYLLABLE_MOVEMENT) {
            rect.rectangle.x = note.getXOffsetPx() + note.getSyllableMovement();
            rect.rectangle.y = (int) (score.getNoteYPosPx(0, rect.line) +
                line.getLyricsYPosSs() +
                5);
        } else if (rect.adjustType == AdjustType.SYLLABLE_RELATION_MOVEMENT) {
            rect.rectangle.x = ((note.getSyllableRelationMovement() == 0)
                ? (int) note.properties.longDashPosition
                : (note.getXOffsetPx() + note.getSyllableRelationMovement())) -
                4;
            rect.rectangle.y = (int) (score.getNoteYPosPx(0, rect.line) +
                line.getLyricsYPosSs() +
                5);
        }

        rect.rectangle.width = 8;
        rect.rectangle.height = 8;
    }

    private void revalidateRects() {
        for (var ar : adjustRects) {
            getRectangle(ar);
        }
    }

    private enum AdjustType {
        SYLLABLE_MOVEMENT(Color.green),
        SYLLABLE_RELATION_MOVEMENT(Color.green),
        LYRICS_YPOS(Color.magenta);

        private final Color color;

        AdjustType(Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    private class AdjustRect {

        final Rectangle rectangle;
        final int line;
        final int xIndex;
        final AdjustType adjustType;

        AdjustRect(int line, AdjustType adjustType, int xIndex) {
            this.line = line;
            this.adjustType = adjustType;
            this.xIndex = xIndex;
            rectangle = new Rectangle();
            getRectangle(this);
        }
    }
}
