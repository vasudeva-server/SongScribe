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

import songscribe.ui.component.score.BaseTitleComponent;

/**
 * Writable views over the presentation state a dialog computes — the framework's
 * <i>sinks</i>.
 *
 * <p>Everything here returns a {@link WritableValue}, and that is the point of the
 * class. <b>Swing offers no notification for any of this state.</b> A component does
 * not report that its enabled flag, its visibility, its font, a label's text or a
 * title component's preview was changed; there is no listener to register and no
 * event to receive. A binding that took one of these as its source would therefore
 * be a binding that never runs, with nothing at runtime reporting it — so the type
 * makes the compiler refuse the edge instead.
 *
 * <p>Nothing here reads or observes. These are the far end of a binding: a dialog
 * derives the state with a {@code computed} over the {@link Controls} properties the
 * user edits, and binds it into one of these. The state a user edits lives in
 * {@link Controls} and is a {@link Property}.
 *
 * <p>Because a sink cannot be read, a binding into one cannot compare what it is
 * about to write against what the component currently holds. It compares against the
 * value it last wrote; see {@link Bindings#bind(WritableValue, ObservableValue)}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>A view holds nothing but the component. It registers no listener, so it needs
 * no teardown of its own — disposing the {@link Bindings} that writes it is the
 * whole of the cleanup.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class Widgets {

    private Widgets() {}

    /**
     * Returns a writable view of {@code component}'s enabled state.
     *
     * @param component the component to write
     * @return the sink; {@code set} calls {@code setEnabled}, which Swing propagates
     *     to the component's children as usual
     */
    public static WritableValue<Boolean> enabled(JComponent component) {
        return component::setEnabled;
    }

    /**
     * Returns a writable view of {@code component}'s visibility.
     *
     * <p>Hiding a component changes what its container lays out, so a binding into
     * this can move everything around it. That is the intended effect for a row that
     * only applies under some setting; a caller wanting the space kept binds
     * {@link #enabled} instead.
     *
     * @param component the component to write
     * @return the sink; {@code set} calls {@code setVisible}
     */
    public static WritableValue<Boolean> visible(JComponent component) {
        return component::setVisible;
    }

    /**
     * Returns a writable view of {@code component}'s font.
     *
     * <p>A font change generally changes the component's preferred size, so the
     * write revalidates and repaints — every caller that sets a font for display has
     * to do that, and doing it here is what removes the hand-written pair from the
     * dialogs.
     *
     * @param component the component to write
     * @return the sink; {@code set} calls {@code setFont}, then revalidates and
     *     repaints the component, so the new font is laid out and drawn without the
     *     caller arranging it
     */
    public static WritableValue<Font> font(JComponent component) {
        return font -> {
            component.setFont(font);
            component.revalidate();
            component.repaint();
        };
    }

    /**
     * Returns a writable view of {@code label}'s text.
     *
     * <p>This is a label the dialog describes something with — the name of a chosen
     * font, a computed summary — not a caption fixed at build time. A caption that
     * never changes is set once and needs no view.
     *
     * @param label the label to write
     * @return the sink; {@code set} calls {@code setText}
     */
    public static WritableValue<String> labelText(JLabel label) {
        return label::setText;
    }

    /**
     * Returns a writable view of {@code component}'s preview.
     *
     * <p>The preview mechanism is how a settings dialog renders unsaved text without
     * mutating the song. {@code songscribe.ui.component.score.TitleComponent} and
     * {@code SubtitleComponent} both extend {@link BaseTitleComponent}, so this one
     * factory serves both.
     *
     * <p>It takes no parameter beyond the component. A dialog that must react to the
     * preview's emptiness — repacking itself when a subtitle appears or disappears,
     * for instance — does so with {@link Bindings#onChange} over its own
     * {@link ValueProperty} of that emptiness, which notifies on the transition
     * rather than on every preview written.
     *
     * @param component the title or subtitle component to write
     * @return the sink; {@code set} calls {@code setPreview}, which revalidates and
     *     repaints the component. It never clears a preview: {@code setPreview}
     *     accepts {@code null} to restore rendering from the song, and a value the
     *     framework writes is never {@code null}, so a caller restoring the song's
     *     own rendering calls {@code setPreview(null)} directly.
     */
    public static WritableValue<BaseTitleComponent.Preview> preview(BaseTitleComponent component) {
        return component::setPreview;
    }
}
