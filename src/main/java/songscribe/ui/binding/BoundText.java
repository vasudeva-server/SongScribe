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
 * <p>The association is a client property of the control, not a field:
 * {@code JTextField}'s own constructor calls {@code setText}, which reaches the override before
 * any subclass field initializer has run, and an absent client property is exactly the "not
 * bound yet" answer the override needs.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class BoundText {

    /** Its value is an {@link Association}; nothing outside this class may write it. */
    private static final String ASSOCIATION_KEY = "songscribe.ui.binding.BoundText.association";

    private static final class Association {

        private final Property<String> property;

        private boolean applying = false;

        private Association(Property<String> property) {
            this.property = property;
        }
    }

    private BoundText() {}

    /**
     * Called by the factory that creates the property, once, immediately after creating it. A
     * second call replaces the association: the last property created is the one a stray write
     * belongs to.
     */
    public static void associate(JTextComponent field, Property<String> property) {
        field.putClientProperty(ASSOCIATION_KEY, new Association(property));
    }

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
     * Called from the {@code setText} override of a control this repository owns. The answer is
     * {@code false} when the control has no property view, when the write is the one
     * {@link #write} is making, and when {@code text} is {@code null}.
     *
     * @return {@code true} when the write was routed into the property, which has written the
     *     document itself and notified its observers; {@code false} when the caller must write
     *     the document
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
