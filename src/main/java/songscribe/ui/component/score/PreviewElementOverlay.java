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

import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.FermataAttachment;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.ui.component.ScoreView;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.DisplayList;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.StaffElementRenderer;
import songscribe.ui.renderer.RecordingGraphics2D;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * The hover preview element — the ghost note, rest, bar line or breath mark that shows what a
 * click would place, drawn in {@link ScoreView#getPreviewElementColor()}.
 *
 * <h2>Record once, replay many</h2>
 * The list is recorded with the element's X override set to <b>zero</b>; the actual X is applied
 * as a translate at bounds and paint time. A move must not rebuild the list: only a change of
 * the drawn glyphs does. {@link PreviewElementManager} drives the two cases through
 * {@link #previewDidMove} and {@link #previewDidChange}.
 *
 * <h2>Slide placeholders</h2>
 * A slide tool's placeholder has no note head; the slide preview components draw for it, so
 * {@link PreviewElementManager#shouldShowPreviewOn} reports this overlay as hidden.
 */
public final class PreviewElementOverlay extends RecordedInkOverlay {

    /**
     * Applied as a translate over {@link #displayList}, never baked into it.
     */
    private double elementXSs;

    PreviewElementOverlay(OverlayHost host) {
        super(host);
    }

    /**
     * Use when only the insertion index moved.
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
        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR, GraphicsState.Property.TRANSFORM)) {
            // The renderers deliberately never set a color, so the recorded ink is monochrome
            // and the preview color is applied once, here.
            g2.setColor(ScoreView.getPreviewElementColor());
            g2.translate(elementXSs, 0);
            replayInk(g2);
        }
    }

    /**
     * Grace mode uses the locked X, which already accounts for grace-note spacing. Otherwise the
     * insertion calculation is fed the manager's last tracked mouse X, not
     * {@code getMousePosition()}, which Swing can return null from during a repaint and would
     * break snap-to-terminal.
     */
    private static double calculateElementXSs(LineComponent line, StaffElement previewElement) {
        if (line.isGraceModeInProgress()) {
            return line.getGraceModeLockedXSs();
        }

        var musicLine = line.getLine();
        var layoutResult = line.getLayoutResult();
        var hover = PreviewElementManager.hover();

        if (musicLine == null || layoutResult == null || hover == null) {
            return 0;
        }

        return layoutResult.calculateInsertionXSs(
            hover.insertionIndex(),
            PreviewElementManager.getCurrentMouseXSs(),
            previewElement,
            musicLine,
            false);
    }

    /**
     * The X override is zero but still <em>set</em>: sub-renderers detect preview rendering
     * through {@code hasOverrideElementX()} and would otherwise look the preview element up in
     * the layout, where it does not exist.
     * <p>
     * The rendering hints must match those {@code paintComponent} replays under: a glyph vector
     * is bound to the {@link FontRenderContext} it was created with.
     */
    private DisplayList recordPreviewElement(LineComponent line, StaffElement previewElement) {
        var invariants = line.previewInvariants();
        var hover = PreviewElementManager.hover();

        if (invariants == null || hover == null) {
            return DisplayList.EMPTY;
        }

        if (previewElement instanceof NoteElement note) {
            note.setStaffPosition(hover.staffPosition());
        }

        var lineFrame = line.gracePreviewLineFrame();
        var frame = lineFrame.withElement(null, 0);

        recorder.reset();
        GraphicUtils.setRenderingHints(recorder);

        StaffElementRenderer.getInstance().render(invariants, frame, previewElement, recorder);

        // The override X remains set, so the decoration renderers place themselves against the
        // same origin as the note head.
        if (previewElement instanceof NoteElement note && !note.getArticulations().isEmpty()) {
            ArticulationRenderer.getInstance().render(invariants, frame, note, recorder);
        }

        if (previewElement.findAttachment(FermataAttachment.class) != null) {
            FermataRenderer.getInstance().render(invariants, frame, previewElement, recorder);
        }

        return recorder.displayList();
    }
}
