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

package songscribe.ui;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.plaf.ComponentUI;

import com.formdev.flatlaf.ui.FlatToolTipUI;
import com.formdev.flatlaf.ui.FlatUIUtils;

/**
 * The UI of every tooltip, installed through the {@code ToolTipUI} key in
 * {@code FlatLaf.properties}: FlatLaf's tooltip, measured at the scale of the screen it appears
 * on.
 * <p>
 * Swing measures a component's text under the transform of the component's own graphics
 * configuration. The tooltip manager asks a new tooltip for its size before the tooltip is in any
 * window, so the tooltip has no graphics configuration and its text is measured at 1x. On a 2x
 * screen the text is then painted with glyph widths rounded at 2x. The two widths differ a little
 * on each glyph, so a long line paints wider or narrower than it measured, and its right padding
 * shrinks or grows.
 * <p>
 * Until a tooltip is in a window, this UI therefore measures it under the graphics configuration
 * of the component it belongs to, which is on the screen the tooltip appears on.
 */
public final class ScreenScaleToolTipUI extends FlatToolTipUI {

    /**
     * Returns the UI shared by every tooltip. {@code UIManager} finds this method by reflection.
     *
     * @param component the tooltip the UI is for
     * @return the shared UI
     */
    public static ComponentUI createUI(JComponent component) {
        return FlatUIUtils.createSharedUI(ScreenScaleToolTipUI.class, ScreenScaleToolTipUI::new);
    }

    /**
     * Returns the tooltip's size as it paints on the screen of its component.
     *
     * @return the size FlatLaf gives the tooltip, measured under the graphics configuration of its
     *     component while the tooltip itself has none
     */
    @Override
    public Dimension getPreferredSize(JComponent component) {
        var toolTip = (JToolTip) component;
        var owner = toolTip.getComponent();

        if (toolTip.getGraphicsConfiguration() != null || owner == null) {
            return super.getPreferredSize(component);
        }

        var ownerConfiguration = owner.getGraphicsConfiguration();

        if (ownerConfiguration == null) {
            return super.getPreferredSize(component);
        }

        // The probe has a graphics configuration, so asking it for its size comes back to this
        // method and takes the first branch.
        var probe = new MeasuringToolTip(ownerConfiguration);
        probe.setFont(toolTip.getFont());
        probe.setTipText(toolTip.getTipText());
        return probe.getPreferredSize();
    }

    /**
     * A tooltip that is never shown, only measured, under a graphics configuration it is given
     * rather than one it gets from a window.
     */
    private static final class MeasuringToolTip extends JToolTip {

        private final GraphicsConfiguration measuringConfiguration;

        MeasuringToolTip(GraphicsConfiguration measuringConfiguration) {
            this.measuringConfiguration = measuringConfiguration;
        }

        @Override
        public GraphicsConfiguration getGraphicsConfiguration() {
            return measuringConfiguration;
        }
    }
}
