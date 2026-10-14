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

import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.NoteElement;
import songscribe.ui.renderer.SlideRenderer;
import songscribe.util.GraphicUtils;

/**
 * The connecting glissando between a grace note awaiting its host and the host-note insertion
 * preview the user is positioning.
 * <p>
 * The pair is connected only once the host note commits; until then the grace note carries no
 * slide (see {@code GraceModeManager.mouseDragged}) and the host is not on the line, so the line
 * has nothing to draw from. The target is the preview element, so it tracks the mouse vertically
 * while its X stays pinned to grace mode's locked host slot.
 *
 * <p>The ink is already in the target line's own coordinate space, so no translate is applied at
 * paint time.
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

        if (graceIndex == null) {
            return null;
        }

        var domLine = line.getLine();

        // getLayoutResult() rather than previewInvariants(): the latter is null under exactly this
        // condition but builds a full LineInvariants otherwise, which is wasted whenever the
        // recorded ink is still current. The preview a grace note awaits is its host note, so a
        // preview element of any other class means there is nothing to connect to.
        if (domLine == null
            || !(line.getPreviewElement() instanceof NoteElement previewElement)
            || line.getLayoutResult() == null) {
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
