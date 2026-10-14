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
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;

import com.formdev.flatlaf.ui.FlatToolTipUI;
import com.formdev.flatlaf.ui.FlatUIUtils;

/**
 * The UI of every tooltip, installed through the {@code ToolTipUI} key in
 * {@code FlatLaf.properties}: FlatLaf's tooltip, measured at the scale of the screen it appears
 * on.
 * <p>
 * A new tooltip is asked for its size before it is in any window, so it has no graphics
 * configuration and its text is measured at 1x, then painted at 2x on a 2x screen with
 * different glyph widths: a long line paints wider or narrower than it measured. Until a tooltip
 * is in a window, this UI measures it under the graphics configuration of the component it
 * belongs to.
 */
public final class ScreenScaleToolTipUI extends FlatToolTipUI {

    /** {@code UIManager} finds this method by reflection. */
    public static ComponentUI createUI(JComponent component) {
        return FlatUIUtils.createSharedUI(ScreenScaleToolTipUI.class, ScreenScaleToolTipUI::new);
    }

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
        // method and takes the first branch. It takes the tooltip's insets rather than its
        // border, because a tooltip can add to its border's insets.
        var probe = new MeasuringToolTip(ownerConfiguration);
        probe.setFont(toolTip.getFont());
        probe.setBorder(new EmptyBorder(toolTip.getInsets()));
        probe.setTipText(toolTip.getTipText());
        return probe.getPreferredSize();
    }

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
