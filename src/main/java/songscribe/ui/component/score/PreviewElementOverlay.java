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

package songscribe.ui.component.score;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.dom.FermataAttachment;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.DisplayList;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.NoteRenderer;
import songscribe.ui.renderer.RecordingGraphics2D;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * The hover preview element — the ghost note, rest, bar line or breath mark that shows what a
 * click would place, drawn in {@link ScoreView#getPreviewElementColor()}.
 *
 * <h2>Record once, replay many</h2>
 * The preview's ink is not composed by hand. The real renderers run against a
 * {@link RecordingGraphics2D} and their output is kept as a {@link DisplayList}; bounds are the
 * union of that list and painting is a replay of it, so the two can never disagree.
 * <p>
 * The list is recorded with the element's X override set to <b>zero</b>, so everything in it is
 * relative to the preview note's own origin. The actual X is applied as a translate at bounds and
 * paint time. That split is structural rather than an optimization: dragging the mouse along a
 * line changes only the translate, while the glyphs the renderers would emit are unchanged, so a
 * move must not rebuild the list. Nothing between here and the renderers snaps to device pixels,
 * so recording at zero and translating is exactly equivalent to recording at the final X.
 * <p>
 * A rebuild is needed only when the drawn glyphs themselves change — a different staff position
 * (which flips the stem direction and crosses the ledger-line threshold), a different accidental,
 * dot count, articulation set or fermata, or a different preview element altogether.
 * {@link PreviewElementManager} drives the two cases through {@link #previewDidMove} and
 * {@link #previewDidChange}.
 *
 * <h2>Slide placeholders</h2>
 * A slide tool puts a placeholder in the preview slot that has no note head at all — the slide
 * preview components draw for it instead. {@link PreviewElementManager#shouldShowPreviewOn}
 * therefore reports this overlay as hidden for a placeholder.
 */
public final class PreviewElementOverlay extends RecordedInkOverlay {

    /**
     * The preview element's X in the target line's staff spaces. Position rather than ink: it is
     * applied as a translate over {@link #displayList}, never baked into it.
     */
    private double elementXSs;

    PreviewElementOverlay(OverlayHost host) {
        super(host);
    }

    /**
     * Re-anchors the preview to {@code line} at its current X without rebuilding the display
     * list. Use when only the insertion index moved: the glyphs are unchanged, so all that
     * changes is the translate.
     */
    void previewDidMove(@Nullable LineComponent line) {
        // A different line means different invariants (middle-line Y, layout, grace state), so
        // the ink has to be re-recorded even though the caller only saw a move.
        if (line != getTargetLine()) {
            markInkStale();
        }

        setTargetLine(line);
        inkDidChange();
    }

    @Override
    protected @Nullable Rectangle2D getInkBoundsSs() {
        var line = getTargetLine();

        if (line == null || !PreviewElementManager.shouldShowPreviewOn(line)) {
            return null;
        }

        var previewElement = line.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        elementXSs = calculateElementXSs(line, previewElement);

        if (inkIsStale()) {
            setDisplayList(recordPreviewElement(line, previewElement));
        }

        var inkSs = getDisplayListInkSs();

        if (inkSs == null) {
            return null;
        }

        return new Rectangle2D.Double(
            inkSs.getX() + elementXSs, inkSs.getY(), inkSs.getWidth(), inkSs.getHeight());
    }

    @Override
    protected void renderOverlay(Graphics2D g2) {
        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR, GraphicsState.Property.TRANSFORM)) {
            // The renderers deliberately never set a color, so the recorded ink is monochrome
            // and the preview color is applied once, here.
            g2.setColor(ScoreView.getPreviewElementColor());
            g2.translate(elementXSs, 0);
            replayInk(g2);
        }
    }

    /**
     * Computes the preview element's X in line staff spaces.
     * <p>
     * Grace mode uses the locked X directly: it already accounts for grace-note spacing, whereas
     * {@code calculateInsertionXSs} would apply normal inter-element spacing instead. Otherwise
     * the X comes from the insertion calculation, fed by the manager's last tracked mouse X
     * rather than {@code getMousePosition()} — Swing can return null from that during a repaint
     * even while the pointer is over the component, which would break snap-to-terminal.
     */
    private static double calculateElementXSs(LineComponent line, StaffElement previewElement) {
        if (line.isGraceModeInProgress()) {
            return line.getGraceModeLockedXSs();
        }

        var musicLine = line.getLine();
        var layoutResult = line.getLayoutResult();

        if (musicLine == null || layoutResult == null) {
            return 0;
        }

        return layoutResult.calculateInsertionXSs(
            PreviewElementManager.getCurrentXIndex(),
            PreviewElementManager.getCurrentMouseXSs(),
            previewElement,
            musicLine,
            false);
    }

    /**
     * Runs the real renderers against the recorder and returns what they drew.
     * <p>
     * The X override is zero so the ink is relative to the preview element's own origin, but the
     * override is still <em>set</em>: sub-renderers detect preview rendering through
     * {@code hasOverrideElementX()} and would otherwise look the preview element up in the
     * layout, where it does not exist.
     * <p>
     * The rendering hints must match the ones {@code paintComponent} replays under, because a
     * glyph vector is bound to the {@link java.awt.font.FontRenderContext} it was created with.
     */
    private DisplayList recordPreviewElement(LineComponent line, StaffElement previewElement) {
        var invariants = line.previewInvariants();

        if (invariants == null) {
            return DisplayList.EMPTY;
        }

        NoteGeometry.initializeAccidentalWidths();

        previewElement.setStaffPosition(PreviewElementManager.getCurrentStaffPosition());
        previewElement.setDirection(StaffElement.defaultDirection(previewElement));

        var lineFrame = line.gracePreviewLineFrame();
        var frame = lineFrame.withElement(lineFrame.currentElementIndex(), 0);

        recorder.reset();
        GraphicUtils.setRenderingHints(recorder);

        NoteRenderer.getInstance().render(invariants, frame, previewElement, recorder);

        // The override X remains set, so the decoration renderers place themselves against the
        // same origin as the note head.
        if (!previewElement.getArticulations().isEmpty()) {
            ArticulationRenderer.getInstance().render(invariants, frame, previewElement, recorder);
        }

        if (previewElement.findAttachment(FermataAttachment.class) != null) {
            FermataRenderer.getInstance().render(invariants, frame, previewElement, recorder);
        }

        return recorder.displayList();
    }
}
