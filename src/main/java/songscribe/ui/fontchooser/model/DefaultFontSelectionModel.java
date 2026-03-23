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
package songscribe.ui.fontchooser.model;

import module java.desktop;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A generic implementation of {@code FontSelectionModel}.
 *
 * @author Christos Bohoris
 * @see Font
 */
public class DefaultFontSelectionModel implements FontSelectionModel {

    private static final int DEFAULT_SIZE = UIManager.getFont(
        "Label.font"
    ).getSize();

    /**
     * A list of registered event listeners.
     */
    private final EventListenerList listenerList = new EventListenerList();

    /**
     * Only one {@code ChangeEvent} is needed per model instance
     * since the event's only (read-only) state is the source property.
     * The source of events generated here is always "this".
     */
    private transient @Nullable ChangeEvent changeEvent = null;

    private Font selectedFont;

    /**
     * Creates a {@code DefaultFontSelectionModel} with the
     * current font set to {@code font}, which should be
     * non-{@code null}. Note that setting the font to
     * {@code null} is undefined and may have unpredictable
     * results.
     *
     * @param font the new {@code Font}
     */
    public DefaultFontSelectionModel(Font font) {
        selectedFont = font;
    }

    /**
     * Returns the selected {@code Font} which should be
     * non-{@code null}.
     *
     * @return the selected {@code Font}
     */
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
     * Sets the selected font to {@code font}.
     * Note that setting the font to {@code null}
     * is undefined and may have unpredictable results.
     * This method fires a state changed event if it sets the
     * current font to a new non-{@code null} font;
     * if the new font is the same as the current font,
     * no event is fired.
     *
     * @param font the new {@code Font}
     */
    @Override
    public void setSelectedFont(Font font) {
        if (!selectedFont.equals(font)) {
            selectedFont = font;
            fireStateChanged();
        }
    }

    /**
     * Adds a {@code ChangeListener} to the model.
     *
     * @param listener the {@code ChangeListener} to be added
     */
    @Override
    public void addChangeListener(ChangeListener listener) {
        listenerList.add(ChangeListener.class, listener);
    }

    /**
     * Removes a {@code ChangeListener} from the model.
     *
     * @param listener the {@code ChangeListener} to be removed
     */
    @Override
    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(ChangeListener.class, listener);
    }

    /**
     * Returns an array of all the {@code ChangeListener}s added
     * to this {@code DefaultFontSelectionModel} with
     * {@code addChangeListener}.
     *
     * @return all of the {@code ChangeListener}s added, or an empty
     * array if no listeners have been added
     */
    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    /**
     * Runs each {@code ChangeListener}'s
     * {@code stateChanged} method.
     */
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
