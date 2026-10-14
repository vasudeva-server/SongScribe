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
package songscribe.ui.binding;

import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JLabel;

import songscribe.binding.Bindings;
import songscribe.binding.ObservableValue;
import songscribe.binding.Property;
import songscribe.binding.ValueProperty;
import songscribe.binding.WritableValue;
import songscribe.ui.component.score.BaseTitleComponent;

/**
 * Writable views over the presentation state a dialog computes — the framework's
 * <i>sinks</i>.
 *
 * <p>Everything here returns a {@link WritableValue}. <b>Swing offers no notification for any
 * of this state</b>, so a binding that took one of these as its source would never run, with
 * nothing at runtime reporting it; the type makes the compiler refuse the binding instead.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>A view registers no listener; disposing the {@link Bindings} that writes it is the whole
 * of the cleanup.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class Widgets {

    private Widgets() {}

    public static WritableValue<Boolean> enabled(JComponent component) {
        return component::setEnabled;
    }

    /** Hiding changes what the container lays out; a caller wanting the space kept binds {@link #enabled}. */
    public static WritableValue<Boolean> visible(JComponent component) {
        return component::setVisible;
    }

    public static WritableValue<Font> font(JComponent component) {
        return font -> {
            component.setFont(font);
            component.revalidate();
            component.repaint();
        };
    }

    public static WritableValue<String> labelText(JLabel label) {
        return label::setText;
    }

    /**
     * The sink never clears a preview: a caller restoring the song's own rendering calls
     * {@code setPreview(null)} directly.
     */
    public static WritableValue<BaseTitleComponent.Preview> preview(BaseTitleComponent component) {
        return component::setPreview;
    }
}
