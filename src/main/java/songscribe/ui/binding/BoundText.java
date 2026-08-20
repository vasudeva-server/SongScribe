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

import javax.swing.text.JTextComponent;

import org.jspecify.annotations.Nullable;

import songscribe.binding.Property;

/**
 * The association between a text control and the {@link Property} that views it,
 * and the two operations that association exists for: writing the control from the
 * property, and routing a stray {@code setText} back into the property.
 *
 * <p>A property created with {@code ON_COMMIT} timing observes {@code focusLost},
 * which a programmatic {@code setText} does not reach, so such a write would be lost
 * until focus happened to leave the control. The text controls this repository owns
 * — {@code songscribe.ui.component.MyJTextField} and {@code MyJTextArea} — override
 * {@code setText} and offer the write to {@link #routed} first, which turns a stray
 * write into a correct one without changing any call site. A plain
 * {@link JTextComponent} this repository does not own has no such override, so an
 * {@code ON_COMMIT} property over one still goes stale on a programmatic write.
 *
 * <p>The association is held as a client property of the control rather than a field
 * on either class. {@code JTextField}'s own constructor calls {@code setText}, which
 * reaches the override before any subclass field initializer has run; a field read
 * there would be null, while an absent client property is exactly the "not bound
 * yet" answer the override needs.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class BoundText {

    /**
     * The client-property key under which a control's association is held. Its value
     * is an {@link Association}; nothing outside this class may write it.
     */
    private static final String ASSOCIATION_KEY = "songscribe.ui.binding.BoundText.association";

    /**
     * A control's property view, together with the re-entrancy flag for that one
     * control.
     *
     * <p>The flag lives here rather than in a second client property so that setting
     * it does not fire a {@code PropertyChangeEvent} on every write.
     */
    private static final class Association {

        private final Property<String> property;

        private boolean applying = false;

        private Association(Property<String> property) {
            this.property = property;
        }
    }

    private BoundText() {}

    /**
     * Records {@code property} as the view over {@code field}, so that a
     * programmatic write to a control that overrides {@code setText} is routed into
     * it.
     *
     * <p>Called by the factory that creates the property, once, immediately after
     * creating it. A second call replaces the association: a control has one property
     * view, and the last one created is the one a stray write belongs to.
     *
     * @param field the control the property views
     * @param property the property view over {@code field}
     */
    public static void associate(JTextComponent field, Property<String> property) {
        field.putClientProperty(ASSOCIATION_KEY, new Association(property));
    }

    /**
     * Writes {@code text} into {@code field} on behalf of its property, suppressing
     * the routing in {@link #routed} for the duration of the write.
     *
     * <p>This is how a property view writes its control, and the guard is why the
     * pair does not recurse: the property writes the field, the field's
     * {@code setText} override would route the write straight back into the property,
     * and the flag set here is what makes that override write the document instead.
     * The flag is cleared in a {@code finally}, so a document listener that throws
     * cannot leave the control permanently unrouted.
     *
     * <p>Writes the field directly when it has no association, so this is safe for
     * any control.
     *
     * @param field the control to write
     * @param text the text to write
     */
    public static void write(JTextComponent field, String text) {
        var association = associationOf(field);

        if (association == null) {
            field.setText(text);
            return;
        }

        association.applying = true;

        try {
            field.setText(text);
        } finally {
            association.applying = false;
        }
    }

    /**
     * Offers a programmatic {@code setText} to {@code field}'s property, and reports
     * whether the property took it.
     *
     * <p>Called from the {@code setText} override of a control this repository owns,
     * which writes the document itself when the answer is {@code false}. The answer
     * is {@code false} when the control has no property view, when the write is the
     * one {@link #write} is making, and when {@code text} is {@code null} — a
     * {@code Property<String>} holds no null, and {@code JTextComponent} treats a
     * null argument as "empty the document", which the caller's {@code super} call
     * still does.
     *
     * @param field the control being written
     * @param text the text being written, or {@code null}
     * @return {@code true} when the write was routed into the property, which has
     *     written the document itself and notified its observers; {@code false} when
     *     the caller must write the document
     */
    public static boolean routed(JTextComponent field, @Nullable String text) {
        var association = associationOf(field);

        if (text == null || association == null || association.applying) {
            return false;
        }

        association.property.set(text);
        return true;
    }

    private static @Nullable Association associationOf(JTextComponent field) {
        return (Association) field.getClientProperty(ASSOCIATION_KEY);
    }
}
