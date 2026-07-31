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

package songscribe.ui.component;

import module java.desktop;

import com.formdev.flatlaf.FlatClientProperties;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.ZoomController;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * Status-bar cluster of zoom controls: a zoom-out button, a continuous slider
 * spanning {@link ZoomController#MIN_ZOOM_PERCENT} to
 * {@link ZoomController#MAX_ZOOM_PERCENT}, a zoom-in button, and a percent button
 * that opens a menu of the discrete {@link ZoomController} levels. The slider
 * changes the zoom continuously as it drags — analogous to wheel and pinch zoom —
 * rather than snapping to the discrete stops; the buttons and menu step between
 * the discrete stops. The two zoom buttons are driven by the shared
 * {@code ZOOM_OUT}/{@code ZOOM_IN} actions, so their icon, tooltip, and enabled
 * state track the actions automatically; this panel only keeps the slider,
 * percent label, and menu check marks in sync with the current zoom.
 */
public final class ZoomStatusBarPanel extends JPanel {

    /** Compact, borderless side length (px) for the status-bar zoom buttons (toolbar buttons are larger). */
    private static final int BUTTON_SIZE_PX = 24;
    private static final Dimension BUTTON_DIMENSION = new Dimension(BUTTON_SIZE_PX, BUTTON_SIZE_PX);

    /** Horizontal gap between the three controls, in pixels. */
    private static final int CONTROL_GAP_PX = 2;

    /** Downward-pointing triangle appended to the percent label as a drop-down affordance. */
    private static final String DROP_DOWN_ARROW = " ▾";

    private final JButton percentButton = new JButton();
    private final JPopupMenu percentMenu = new JPopupMenu();
    private final JCheckBoxMenuItem[] levelItems = new JCheckBoxMenuItem[ZoomController.ZOOM_LEVEL_PERCENTS.length];
    private final JSlider zoomSlider =
        new JSlider(ZoomController.MIN_ZOOM_PERCENT, ZoomController.MAX_ZOOM_PERCENT, ZoomController.DEFAULT_ZOOM_PERCENT);

    public ZoomStatusBarPanel() {
        super(new FlowLayout(FlowLayout.LEADING, CONTROL_GAP_PX, 0));
        setOpaque(false);

        add(new ZoomButton(Actions.ZOOM_OUT_ACTION));

        zoomSlider.setFocusable(false);
        zoomSlider.setOpaque(false);
        zoomSlider.addChangeListener(_ -> ZoomController.setZoomPercent(zoomSlider.getValue()));
        add(zoomSlider);

        add(new ZoomButton(Actions.ZOOM_IN_ACTION));

        buildPercentMenu();
        percentButton.setFocusable(false);
        percentButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        percentButton.addActionListener(event -> percentMenu.show(percentButton, 0, percentButton.getHeight()));
        add(percentButton);

        updateState(ZoomController.getZoomPercent());

        MessageCenter.subscribe(this);
    }

    @Handler
    public void zoomDidChange(ZoomDidChangeNotification message) {
        updateState(message.getNewZoomPercent());
    }

    private void buildPercentMenu() {
        var levelPercents = ZoomController.ZOOM_LEVEL_PERCENTS;

        for (var i = 0; i < levelPercents.length; i++) {
            var levelPercent = levelPercents[i];
            var item = new JCheckBoxMenuItem(Strings.get(Strings.STATUS_ZOOM_PERCENT, levelPercent));
            item.addActionListener(event -> ZoomController.setZoomPercent(levelPercent));
            levelItems[i] = item;
            percentMenu.add(item);
        }
    }

    private void updateState(int currentPercent) {
        percentButton.setText(Strings.get(Strings.STATUS_ZOOM_PERCENT, currentPercent) + DROP_DOWN_ARROW);
        zoomSlider.setValue(currentPercent);

        var levelPercents = ZoomController.ZOOM_LEVEL_PERCENTS;

        for (var i = 0; i < levelPercents.length; i++) {
            levelItems[i].setSelected(levelPercents[i] == currentPercent);
        }
    }

    /**
     * Compact borderless button that renders a {@link UIAction}'s icon-font glyph and stays
     * in sync with the action's icon, tooltip, and enabled state — the status-bar-sized
     * counterpart to {@link ToolbarButton}.
     */
    private static final class ZoomButton extends JButton implements PropertyChangeListener {

        ZoomButton(UIAction action) {
            super(action);
            UIUtils.initToolbarButton(this, BUTTON_DIMENSION);
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            action.addPropertyChangeListener(this);
        }

        @Override
        protected void configurePropertiesFromAction(Action action) {
            if (action instanceof UIAction uiAction) {
                UIUtils.configureButtonFromAction(this, uiAction);
            } else {
                super.configurePropertiesFromAction(action);
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            UIUtils.handleActionPropertyChange(this, (UIAction) getAction(), event);
        }
    }
}
