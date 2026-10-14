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

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;

import com.formdev.flatlaf.FlatClientProperties;

import songscribe.Strings;
import songscribe.binding.Bindings;
import songscribe.ui.ZoomController;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.util.UIUtils;

/**
 * Status-bar cluster of zoom controls. The slider changes the zoom continuously as it drags,
 * unlike the buttons and menu, which step between the discrete stops.
 */
public final class ZoomStatusBarPanel extends JPanel {

    private static final int BUTTON_SIZE_PX = 24;
    private static final Dimension BUTTON_DIMENSION = new Dimension(BUTTON_SIZE_PX, BUTTON_SIZE_PX);

    private static final int CONTROL_GAP_PX = 2;

    private static final String DROP_DOWN_ARROW = " ▾";

    private final JButton percentButton = new JButton();
    private final JPopupMenu percentMenu = new JPopupMenu();

    private final Map<Integer, JCheckBoxMenuItem> levelItems = new LinkedHashMap<>();

    private final JSlider zoomSlider =
        new JSlider(ZoomController.MIN_ZOOM_PERCENT, ZoomController.MAX_ZOOM_PERCENT, ZoomController.DEFAULT_ZOOM_PERCENT);

    // The effect that keeps this panel current. Never disposed: the status bar lives for
    // the process, so there is no retirement to release it at.
    private final Bindings bindings = new Bindings();

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

        var zoomPercent = MainFrame.getInstance().requireScoreView().zoomPercent();
        updateState(zoomPercent.get());
        bindings.onNotify(zoomPercent, () -> updateState(zoomPercent.get()));
    }

    private void buildPercentMenu() {
        for (var levelPercent : ZoomController.ZOOM_LEVEL_PERCENTS) {
            var item = new JCheckBoxMenuItem(Strings.get(Strings.STATUS_ZOOM_PERCENT, levelPercent));
            item.addActionListener(event -> ZoomController.setZoomPercent(levelPercent));

            // Advertise the stop's shortcut where the stop itself is chosen. The keystroke
            // is bound on the root pane by the action, not by this item.
            Actions.ZOOM_LEVEL_ACTIONS.stream()
                .filter(action -> action.getZoomPercent() == levelPercent)
                .findFirst()
                .ifPresent(action -> item.setAccelerator(action.getAccelerator()));

            levelItems.put(levelPercent, item);
            percentMenu.add(item);
        }
    }

    private void updateState(int currentPercent) {
        percentButton.setText(Strings.get(Strings.STATUS_ZOOM_PERCENT, currentPercent) + DROP_DOWN_ARROW);
        zoomSlider.setValue(currentPercent);
        levelItems.forEach((levelPercent, item) -> item.setSelected(levelPercent == currentPercent));
    }

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
