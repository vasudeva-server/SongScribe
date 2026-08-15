/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.renderer;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.GlyphVector;
import java.awt.geom.Line2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import songscribe.layout.LayoutResult;
import songscribe.layout.LyricConnectorLayout;
import songscribe.layout.LyricRenderMetrics;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.STROKE;

/**
 * Renders line-level lyric connectors (syllable hyphens and melisma extenders).
 * <p>
 * Iterates the {@link LyricConnectorLayout} entries on the current
 * {@link LayoutResult}, drawing them on that line's own lyric
 * baseline Y. Stateless.
 * <p>
 * Following Gould/Ross engraving rules: hyphens mark syllable division only;
 * extenders mark duration only.
 */
public final class LyricConnectorRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Extender stroke width in staff spaces (~0.836 px at default 8 px/ss scale). */
    private static final double EXTENDER_STROKE_WIDTH_SS = 0.1045;

    private static final BasicStroke EXTENDER_STROKE = new BasicStroke(
        (float) EXTENDER_STROKE_WIDTH_SS,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER);

    private static final LyricConnectorRenderer INSTANCE = new LyricConnectorRenderer();

    // Cache of pre-shaped hyphen glyphs keyed by scaled lyrics font. The font changes
    // only when the user changes the lyrics font or zoom, so the cache is small in
    // practice and trades a few-byte map for repeated drawString shaping.
    private final Map<Font, GlyphVector> hyphenGlyphVectors = new ConcurrentHashMap<>();

    // ==========================================================================
    // Construction
    // ==========================================================================

    private LyricConnectorRenderer() {
    }

    public static LyricConnectorRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders every connector on the current line.
     *
     * @param g2  graphics context (in staff-space coordinates)
     * @param ctx render context with a populated layout result and song metrics
     */
    public void render(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var layoutResult = invariants.getLayoutResult();
        var connectors = layoutResult.getLyricConnectors();

        if (connectors.isEmpty()) {
            return;
        }

        var lyricRenderMetrics = invariants.getLyricRenderMetrics();
        var hyphenGv = getHyphenGlyphVector(lyricRenderMetrics.scaledLyricsFont());
        var chainPreviewEndXSs = hyphenChainPreviewEndXSs(invariants);
        var editedVerse = invariants.getActivelyEditedVerse();
        var ySs = layoutResult.lyricBaselineYSsInLine(lyricRenderMetrics);

        try (var _ = GraphicsState.save(g2, COLOR, STROKE, FONT)) {
            g2.setFont(lyricRenderMetrics.scaledLyricsFont());
            g2.setStroke(EXTENDER_STROKE);

            for (var connector : connectors) {
                g2.setColor(invariants.getLyricConnectorColor(connector.sourceElementIndex(), connector.verseIndex()));

                switch (connector.kind()) {
                    case HYPHEN -> drawHyphens(
                        g2, connector.startXSs(), connector.endXSs(), ySs, lyricRenderMetrics, hyphenGv);

                    case DANGLING_HYPHEN -> {
                        // While the lyric editor is open past the syllable that opened this chain, engrave
                        // the chain up to the edited element instead of parking a single hyphen in the first
                        // gap, so the chain visibly grows as the user hyphenates their way along the line.
                        var previewsChain = connector.verseIndex() == editedVerse
                            && !Double.isNaN(chainPreviewEndXSs)
                            && chainPreviewEndXSs > connector.startXSs();

                        if (previewsChain) {
                            drawHyphens(
                                g2, connector.startXSs(), chainPreviewEndXSs, ySs, lyricRenderMetrics, hyphenGv);
                        } else {
                            drawSingleCenteredHyphen(
                                g2, connector.startXSs(), connector.endXSs(), ySs, lyricRenderMetrics, hyphenGv);
                        }
                    }

                    case EXTENDER, DANGLING_EXTENDER -> drawExtender(g2, connector, ySs);
                }
            }
        }
    }

    /**
     * Returns the X (staff spaces) at which an unclosed hyphen chain should end while the lyric
     * editor is open, or {@link Double#NaN} when no editor is open or the edited element is not on
     * this line.
     * <p>
     * The chain ends at the edited element's column left edge — the same end the layout pass gives
     * a dangling hyphen — rather than at the editor's own left edge, so the preview keeps the
     * geometry the connector already has and the editor simply overlays whatever it covers. The
     * final {@link LyricConnectorLayout.Kind#HYPHEN} ends at the closing syllable's lyric box
     * instead, so the hyphens shift slightly when that syllable is committed; its width cannot be
     * known before it is typed.
     */
    private static double hyphenChainPreviewEndXSs(LineInvariants invariants) {
        var editedElement = invariants.getActivelyEditedElement();

        if (editedElement == null) {
            return Double.NaN;
        }

        var editedColumn = invariants.getLayoutResult().getElementColumn(editedElement);

        if (editedColumn == null) {
            return Double.NaN;
        }

        return editedColumn.getLeftEdgeXSs();
    }

    private GlyphVector getHyphenGlyphVector(Font scaledLyricsFont) {
        return hyphenGlyphVectors.computeIfAbsent(
            scaledLyricsFont,
            font -> font.createGlyphVector(GraphicUtils.SCREEN_FRC, "-"));
    }

    private static void drawHyphens(
        Graphics2D g2,
        double startXSs,
        double endXSs,
        double ySs,
        LyricRenderMetrics metrics,
        GlyphVector hyphenGv
    ) {
        var gapSs = endXSs - startXSs;
        var preferredCellWidthSs = metrics.preferredHyphenCellWidthSs();
        var count = (int) Math.floor(gapSs / preferredCellWidthSs);

        if (count <= 1) {
            drawSingleCenteredHyphen(g2, startXSs, endXSs, ySs, metrics, hyphenGv);
            return;
        }

        var hyphenWidthSs = metrics.hyphenWidthSs();
        var offsetSs = (gapSs - count * preferredCellWidthSs) / 2.0;

        for (var i = 0; i < count; i++) {
            var cellCenterXSs = startXSs + offsetSs + (i + 0.5) * preferredCellWidthSs;
            g2.drawGlyphVector(hyphenGv, (float) (cellCenterXSs - hyphenWidthSs / 2.0), (float) ySs);
        }
    }

    private static void drawSingleCenteredHyphen(
        Graphics2D g2,
        double startXSs,
        double endXSs,
        double ySs,
        LyricRenderMetrics metrics,
        GlyphVector hyphenGv
    ) {
        var centerXSs = (startXSs + endXSs) / 2.0;
        g2.drawGlyphVector(hyphenGv, (float) (centerXSs - metrics.hyphenWidthSs() / 2.0), (float) ySs);
    }

    private static void drawExtender(Graphics2D g2, LyricConnectorLayout connector, double ySs) {
        g2.draw(new Line2D.Double(connector.startXSs(), ySs, connector.endXSs(), ySs));
    }
}
