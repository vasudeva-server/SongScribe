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
package songscribe.ui.dialog.fontchooser.model;

import java.awt.Font;
import java.util.Objects;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

import org.jspecify.annotations.Nullable;

/**
 * @author Christos Bohoris
 */
public class DefaultFontSelectionModel implements FontSelectionModel {

    private static final int DEFAULT_SIZE = UIManager.getFont(
        "Label.font"
    ).getSize();

    private final EventListenerList listenerList = new EventListenerList();

    private transient @Nullable ChangeEvent changeEvent = null;

    private Font selectedFont;

    /**
     * The font is not {@code null}.
     */
    public DefaultFontSelectionModel(Font font) {
        selectedFont = font;
    }

    @Override
    public Font getSelectedFont() {
        return selectedFont;
    }

    @Override
    public String getSelectedFontName() {
        return selectedFont.getName();
    }

    @Override
    public String getSelectedFontFamily() {
        return selectedFont.getFamily();
    }

    @Override
    public int getSelectedFontSize() {
        return selectedFont.getSize();
    }

    /**
     * The font is not {@code null}. Fires a state changed event only if the font differs from
     * the current one.
     */
    @Override
    public void setSelectedFont(Font font) {
        if (!selectedFont.equals(font)) {
            selectedFont = font;
            fireStateChanged();
        }
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        listenerList.add(ChangeListener.class, listener);
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(ChangeListener.class, listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    private void fireStateChanged() {
        var listeners = listenerList.getListenerList();

        for (var i = listeners.length - 2; i >= 0; i -= 2) {
            if (Objects.equals(listeners[i], ChangeListener.class)) {
                if (changeEvent == null) {
                    changeEvent = new ChangeEvent(this);
                }

                ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }
}
