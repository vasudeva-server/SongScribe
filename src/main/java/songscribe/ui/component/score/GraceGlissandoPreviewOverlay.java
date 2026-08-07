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

import songscribe.ui.renderer.SlideRenderer;
import songscribe.util.GraphicUtils;

/**
 * The connecting glissando between a grace note awaiting its host and the host-note insertion
 * preview the user is positioning (refs #650).
 * <p>
 * The pair is only connected once the host note is committed, so until then the line has nothing
 * to draw from: the grace note carries no slide (see {@code GraceModeManager.mouseDragged} for why
 * it must not be given one mid-gesture) and the host is not on the line at all. Drawing it here,
 * in the preview color, is what shows the user that the ghost they are moving is going to be
 * attached to the grace note behind it.
 * <p>
 * Unlike a glissando between two already-committed notes, whose endpoints are both resolved note
 * positions, this one's target is the preview element — so it tracks the mouse vertically,
 * continuously, while its X stays pinned to grace mode's locked host slot.
 *
 * <h2>Record once, replay many</h2>
 * Like the other preview overlays the ink comes from running the real {@code SlideRenderer} against
 * a recorder. It is already in the target line's own coordinate space (the source endpoint is a
 * laid-out note and the target's X is the locked slot, neither of them mouse-relative), so no
 * translate is applied at paint time.
 */
public final class GraceGlissandoPreviewOverlay extends RecordedInkOverlay {

    GraceGlissandoPreviewOverlay(OverlayHost host) {
        super(host);
    }

    @Override
    protected @Nullable Rectangle2D getInkBoundsSs() {
        var line = getTargetLine();

        if (line == null) {
            return null;
        }

        var graceIndex = PreviewElementManager.graceHostPreviewSourceIndexOn(line);

        if (graceIndex < 0) {
            return null;
        }

        var domLine = line.getLine();
        var previewElement = line.getPreviewElement();

        // getLayoutResult() rather than previewInvariants(): the latter is null under exactly this
        // condition but builds a full LineInvariants otherwise, which is wasted whenever the
        // recorded ink is still current.
        if (domLine == null || previewElement == null || line.getLayoutResult() == null) {
            return null;
        }

        if (inkIsStale()) {
            var invariants = line.previewInvariants();

            if (invariants == null) {
                return null;
            }

            recorder.reset();
            GraphicUtils.setRenderingHints(recorder);
            SlideRenderer.getInstance().renderPreviewGlissandoToPreviewElement(
                recorder, graceIndex, domLine, previewElement,
                line.getGraceModeLockedXSs(), invariants
            );
            setDisplayList(recorder.displayList());
        }

        return getDisplayListInkSs();
    }
}
