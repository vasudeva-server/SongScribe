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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;

/**
 * The confirmation asked before a deletion carries away the other half of a barline /
 * key-signature pair — the elements the user did not select and would otherwise lose without
 * being told.
 *
 * <p>A key signature is confirmed rather than taken silently, as a breath mark is, because both
 * halves of the pair are costly to lose by accident: a key change re-spells every note after it,
 * and the barline the backward pairing takes may be one the user placed themselves, so removing
 * it merges two measures. The weight is the first-second ending's, and so is the shape — see
 * {@link EndingConfirms}.
 *
 * <p>Which elements the deletion carries is {@link Line#keyPairDeletion}'s answer, asked of the
 * pre-deletion line; this class only turns that answer into a prompt.
 */
public final class KeySignatureConfirms {

    private KeySignatureConfirms() {}

    /**
     * Asks the user whether a deletion of {@code [begin, end]} may proceed when it would carry
     * away a barline or key signature outside that range. Shows nothing and answers {@code true}
     * when the deletion carries neither.
     *
     * <p>Call before mutating anything: declining must leave the score untouched. The indices are
     * read against the pre-deletion line.
     *
     * @param parent the component the dialog is positioned over, or null for none
     * @param line   the line the deletion is on
     * @param begin  the first element the user selected
     * @param end    the last element the user selected
     * @return {@code true} when the caller may delete — nothing paired is at stake, or the user
     *     agreed to lose it
     */
    public static boolean confirmPairedDeletion(@Nullable Component parent, Line line, int begin, int end) {
        var pairing = line.keyPairDeletion(begin, end);

        if (pairing == Line.KeyPairDeletion.NONE) {
            return true;
        }

        // The barline the prompt names: the selection's own last element when the key signature
        // behind it is what gets carried along, otherwise the one in front of the selection.
        var barlineIndex = pairing == Line.KeyPairDeletion.KEY_SIGNATURE_AFTER ? end : begin - 1;

        var result = OptionDialogs.showOptionDialog(
            parent,
            Strings.CONFIRM_TITLE_KEY_SIGNATURE,
            messageKeyFor(pairing),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new Object[]{Strings.get(Strings.BUTTON_YES), Strings.get(Strings.BUTTON_NO)},
            Strings.get(Strings.BUTTON_YES),
            EndingConfirms.typeNameFor(line.getElement(barlineIndex).getType())
        );

        return result == 0;
    }

    /**
     * The message naming what {@code pairing} carries away. Each direction has its own message:
     * one prompt covering both would leave the user of the direction it does not describe with
     * exactly the surprise this confirmation exists to prevent.
     *
     * @param pairing which paired elements the deletion carries; never
     *     {@link Line.KeyPairDeletion#NONE}, which names nothing to prompt about
     * @return the {@link Strings} key of the prompt for that direction
     * @throws IllegalArgumentException if {@code pairing} is {@link Line.KeyPairDeletion#NONE}
     */
    static String messageKeyFor(Line.KeyPairDeletion pairing) {
        return switch (pairing) {
            case KEY_SIGNATURE_AFTER -> Strings.CONFIRM_DELETE_BARLINE_TAKES_KEY_SIGNATURE;
            case BARLINE_BEFORE -> Strings.CONFIRM_DELETE_KEY_SIGNATURE_TAKES_BARLINE;
            case BOTH -> Strings.CONFIRM_DELETE_TAKES_BARLINE_AND_KEY_SIGNATURE;
            case NONE -> throw new IllegalArgumentException("A deletion carrying nothing paired has nothing to confirm");
        };
    }
}
