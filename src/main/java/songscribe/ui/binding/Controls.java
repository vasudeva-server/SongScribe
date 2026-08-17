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

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.SpinnerModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;

/**
 * Property views over the Swing controls a user edits — the framework's
 * <i>sources</i>.
 *
 * <p>Everything here returns a {@link Property}: a control the user edits is read,
 * observed and written, so all three capabilities are real. Presentation state a
 * dialog merely computes has no notification behind it and lives in {@link Widgets}
 * as a {@link WritableValue} instead. The return types are the whole of that
 * boundary — a caller cannot accidentally use a sink as a source, and cannot
 * accidentally type a source so narrowly that the framework refuses to write it.
 *
 * <p>A view is not a copy. The control remains the storage; the property reads it on
 * every {@link ObservableValue#get} and writes straight through on
 * {@link WritableValue#set}. Nothing here caches, so a control changed by code that
 * knows nothing of this package is still read correctly — it simply does not
 * <i>notify</i> unless the route below carries the write.
 *
 * <h2>Notification routes</h2>
 *
 * <p>Every adapter names, in its own contract, the Swing route it observes and
 * whether that route fires on a programmatic write, because picking the wrong route
 * loses writes and nothing at all reports the loss. The routes this class uses:
 *
 * <table border="1">
 * <caption>Swing notification routes and programmatic writes</caption>
 * <tr><th>Route</th><th>Used by</th><th>Fires on a programmatic write?</th></tr>
 * <tr><td>{@code DocumentListener}</td><td>{@link #text} ({@link Timing#WHILE_TYPING})</td>
 *     <td>yes — {@code setText} goes through the Document</td></tr>
 * <tr><td>{@code FocusAdapter.focusLost}</td><td>{@link #text} ({@link Timing#ON_COMMIT})</td>
 *     <td><b>no</b> — which is why a write through that property notifies by itself,
 *     and why a stray write to a control this repo owns is routed into it</td></tr>
 * <tr><td>{@code ActionListener} on {@code JComboBox}</td><td>{@link #item}, {@link #itemIndex}</td>
 *     <td>yes — {@code setSelectedItem} and {@code setSelectedIndex} both fire it</td></tr>
 * <tr><td>{@code ItemListener} on {@code AbstractButton}</td><td>{@link #selected}, {@link #choice}</td>
 *     <td>yes — {@code setSelected} fires it</td></tr>
 * <tr><td>{@code ActionListener} on {@code AbstractButton}</td><td>nothing here</td>
 *     <td><b>no</b> — which is why the button adapters observe items instead</td></tr>
 * <tr><td>{@code ChangeListener} on a {@code SpinnerModel}</td><td>{@link #number}</td>
 *     <td>yes — {@code setValue} fires it</td></tr>
 * <tr><td>{@code ChangeListener} on a {@code JSlider}</td><td>{@link #value}</td>
 *     <td>yes — {@code setValue} fires it</td></tr>
 * </table>
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Each factory registers a listener on the control and never removes it: the
 * property lives as long as the control does, and both die with the dialog that
 * built them. {@link Bindings#dispose} cancels the observations taken <i>on</i> a
 * property; it does not — and need not — unregister the property's own Swing
 * listener. Do not build a property over a control that outlives the dialog holding
 * the bindings.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class Controls {

    private Controls() {}

    /**
     * Returns a property view of {@code field}'s text, notifying at {@code timing}.
     *
     * <p>{@link Timing#WHILE_TYPING} observes the field's {@code Document} and
     * notifies on every keystroke — insertion, removal and attribute change all
     * count as the same change, because from a value's point of view they are.
     * {@link Timing#ON_COMMIT} observes {@code focusLost} instead and notifies once
     * entry has finished.
     *
     * <p><b>{@code ON_COMMIT} is the one route in this class that a programmatic
     * {@code setText} does not reach.</b> Swing fires no focus event for a write, so
     * the field's own listener cannot report one. Two things close that, and neither
     * is the focus route. A write made <i>through this property</i> notifies on its
     * own for this timing — the write is the notification, since nothing else will
     * announce it — so seeding a field from stored data propagates like a user's
     * entry. And a write made <i>to the control</i> is routed into this property when
     * the control is one this repository owns: the delegating {@code setText} on
     * {@code MyJTextField} and {@code MyJTextArea} hands the text to the property
     * associated with the field, which then writes the document and notifies. A plain
     * {@code JTextComponent} this repo does not own — a {@code JSpinner}'s editor,
     * for instance — has no such delegation, so a {@code setText} made straight to
     * one is lost and nothing reports it. Bind such a field {@code WHILE_TYPING}, or
     * write it through the property rather than through the control.
     *
     * @param field the text component to view; its text is the property's value, and
     *     is never {@code null}
     * @param timing when the property notifies
     * @return the two-way view; {@code set} calls {@code setText}, which for either
     *     timing leaves {@code get} answering the written text
     * @effects registers a listener on {@code field} — a {@code DocumentListener} for
     *     {@code WHILE_TYPING}, a focus listener for {@code ON_COMMIT} — which is
     *     never unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static Property<String> text(JTextComponent field, Timing timing) {
        return textProperty(field, timing, null);
    }

    /**
     * Returns a property view of {@code field}'s text that normalizes the text on
     * focus loss, notifying at {@code timing}.
     *
     * <p>This overload replaces a hand-written focus adapter that tidies what the
     * user typed — trimming it, collapsing runs of whitespace, supplying a default
     * for an empty field.
     *
     * <p><b>The normalizer's timing is not {@code timing}.</b> {@code timing}
     * governs when the property notifies; the normalizer always runs on focus loss,
     * whichever timing was asked for, because focus loss is the only moment at which
     * the user is finished with the text and rewriting it under the caret is not
     * hostile. One focus listener does three things, in this order: normalize the
     * field's text, write the normalized text back into the field, then notify.
     * Registering the normalization and an {@code ON_COMMIT} notification as two
     * separate focus adapters would make the notified value depend on registration
     * order, and the losing order notifies un-normalized text and never notifies
     * again.
     *
     * <p>The programmatic-write limitation of {@link #text(JTextComponent, Timing)}
     * applies here unchanged.
     *
     * @param field the text component to view
     * @param timing when the property notifies
     * @param normalizer the tidying applied to the field's text on focus loss. It
     *     must be idempotent — {@code normalizer.apply(normalizer.apply(s))} must
     *     equal {@code normalizer.apply(s)} — because it is applied to text it has
     *     already produced, every time the field regains and loses focus. It must
     *     not return {@code null}.
     * @return the two-way view; the value it answers after a focus loss is the
     *     normalized text, which is also what the field then shows
     * @effects registers listeners on {@code field}, never unregistered. On every
     *     focus loss it may rewrite the field's text, which for
     *     {@code WHILE_TYPING} fires the document route as well.
     */
    public static Property<String> text(JTextComponent field, Timing timing, UnaryOperator<String> normalizer) {
        return textProperty(field, timing, normalizer);
    }

    /**
     * Returns a property view of {@code combo}'s selected item.
     *
     * <p>Observed with an {@code ActionListener}, which {@code setSelectedItem}
     * fires, so a programmatic selection propagates like a user's and needs no
     * delegation of the kind {@link #text} describes.
     *
     * @param <E> the combo box's item type
     * @param combo the combo box to view. It must be uneditable and must always have
     *     a selection. Uneditable is what makes the selected item the whole of the
     *     control's value: an editable combo has editor text that can diverge from
     *     the selection, and this view would not see it.
     * @return the two-way view over {@code getSelectedItem} / {@code setSelectedItem}.
     *     Its {@code get} throws {@link IllegalStateException} when the combo has no
     *     selection, that being the precondition above rather than a value the view
     *     can answer.
     * @effects registers an {@code ActionListener} on {@code combo}, never
     *     unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static <E> Property<E> item(JComboBox<E> combo) {
        var property = new ControlProperty<E>(
            () -> selectedItem(combo),
            combo::setSelectedItem,
            ControlProperty.WriteNotification.FROM_CONTROL
        );
        combo.addActionListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * Returns a property view of {@code combo}'s selected <i>position</i>.
     *
     * <p>The same control and the same {@code ActionListener} route as {@link #item};
     * the two differ only in what they call the selection. Use this one when the
     * surrounding code speaks positions — a combo whose items are a list the caller
     * built, where the stored form of the choice is its index — and {@link #item} when
     * it speaks values. Viewing an index directly is what lets a derivation read the
     * position without a list lookup standing between the property and the reader.
     *
     * @param combo the combo box to view. It must be uneditable, for the reason
     *     {@link #item} gives.
     * @return the two-way view over {@code getSelectedIndex} / {@code setSelectedIndex}.
     *     Its {@code get} answers {@code -1} when nothing is selected, which is Swing's
     *     own answer rather than a value this view invents, so an empty leading entry
     *     meaning "not chosen" is the caller's convention and not this one's.
     * @invariant a written index is the index read back, for any position the combo
     *     holds; {@code -1} clears the selection
     * @throws IllegalArgumentException from {@code set}, when the index is neither
     *     {@code -1} nor a position the combo holds
     * @effects registers an {@code ActionListener} on {@code combo}, never
     *     unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static Property<Integer> itemIndex(JComboBox<?> combo) {
        var property = new ControlProperty<Integer>(
            combo::getSelectedIndex,
            combo::setSelectedIndex,
            ControlProperty.WriteNotification.FROM_CONTROL
        );
        combo.addActionListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * Returns a property view of {@code button}'s selected state.
     *
     * <p>Observed with an {@code ItemListener}. An {@code ActionListener} here fires
     * only on a user click and not on {@code setSelected}, so it would lose every
     * programmatic write.
     *
     * @param button the checkbox, radio button or toggle to view
     * @return the two-way view over {@code isSelected} / {@code setSelected}
     * @effects registers an {@code ItemListener} on {@code button}, never
     *     unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static Property<Boolean> selected(AbstractButton button) {
        var property = new ControlProperty<Boolean>(
            button::isSelected,
            button::setSelected,
            ControlProperty.WriteNotification.FROM_CONTROL
        );
        button.addItemListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * Returns a property view of a spinner model's value.
     *
     * <p>Observed with a {@code ChangeListener}, which {@code setValue} fires, so a
     * programmatic write propagates.
     *
     * <p>The model rather than the {@code JSpinner} is the subject, because the
     * model is what holds the value and what a caller already has to build to
     * constrain the spinner's range.
     *
     * @param model the spinner model to view. Its values must be {@link Number}s —
     *     a {@code SpinnerNumberModel} or any model over numbers — and its value
     *     must never be {@code null}.
     * @return the two-way view over {@code getValue} / {@code setValue}. Its
     *     {@code get} throws {@link ClassCastException} when the model holds
     *     something that is not a {@code Number}, that being the precondition above.
     *     {@code set} writes whatever {@code Number} it is given, so a caller writing
     *     a type the model rejects gets the model's own complaint.
     * @effects registers a {@code ChangeListener} on {@code model}, never
     *     unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static Property<Number> number(SpinnerModel model) {
        var property = new ControlProperty<Number>(
            () -> (Number) model.getValue(),
            model::setValue,
            ControlProperty.WriteNotification.FROM_CONTROL
        );
        model.addChangeListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * Returns a property view of {@code slider}'s value.
     *
     * <p>Observed with a {@code ChangeListener}, which {@code setValue} fires, so a
     * programmatic write propagates.
     *
     * <p><b>It notifies throughout a drag</b>, once per intermediate value, because
     * {@code getValueIsAdjusting} is not consulted. That is the right default for a
     * live preview and the wrong one for an expensive effect: a caller wanting
     * drag-end only reads the slider directly rather than binding it.
     *
     * @param slider the slider to view
     * @return the two-way view over {@code getValue} / {@code setValue}
     * @effects registers a {@code ChangeListener} on {@code slider}, never
     *     unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static Property<Integer> value(JSlider slider) {
        var property = new ControlProperty<Integer>(
            slider::getValue,
            slider::setValue,
            ControlProperty.WriteNotification.FROM_CONTROL
        );
        slider.addChangeListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * Returns a property view of a radio group, valued as the enum constant whose
     * button is selected.
     *
     * <p>This is what turns a group of buttons back into the one choice it stands
     * for. Each button is observed with an {@code ItemListener}, so both a user's
     * click and a programmatic {@code set} propagate.
     *
     * <p>{@code buttons} is copied, so a later change to the caller's map does not
     * reach the view and cannot break the totality established here.
     *
     * @param <E> the enum whose constants the group offers
     * @param buttons the button standing for each constant. It must contain an entry
     *     for every constant of {@code E}: a partial map is a real thing a caller can
     *     hand over, and rejecting it here is what lets the view's {@code get} be
     *     total. An {@code EnumMap} rather than a plain {@code Map} so that iteration
     *     follows the enum's own declaration order.
     * @return the two-way view. Its {@code get} answers the constant whose button is
     *     selected and throws {@link IllegalStateException} when none is — a group
     *     with no selection has no value, and a caller binding one must select a
     *     button before the first read. Its {@code set} selects the given constant's
     *     button, which the enclosing {@code ButtonGroup} deselects the others for.
     * @throws IllegalArgumentException if {@code buttons} is empty, or does not cover
     *     every constant of {@code E}; the message names the missing constants
     * @effects registers an {@code ItemListener} on every button, never
     *     unregistered. See the class documentation's <i>Lifecycle</i> section.
     */
    public static <E extends Enum<E>> Property<E> choice(EnumMap<E, AbstractButton> buttons) {
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("A radio-group property needs a button for every enum constant, but the map is empty");
        }

        var declaringClass = buttons.keySet().iterator().next().getDeclaringClass();
        var missing = new ArrayList<String>();

        for (var constant : declaringClass.getEnumConstants()) {
            if (!buttons.containsKey(constant)) {
                missing.add(constant.name());
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "A radio-group property needs a button for every " + declaringClass.getSimpleName()
                    + " constant, but these have none: " + String.join(", ", missing));
        }

        var group = new EnumMap<>(buttons);
        var property = new ControlProperty<E>(
            () -> selectedConstant(group),
            constant -> select(group, constant),
            ControlProperty.WriteNotification.FROM_CONTROL
        );

        for (var button : group.values()) {
            button.addItemListener(event -> property.notifyObservers());
        }

        return property;
    }

    /**
     * Builds the text view both {@link #text} overloads return.
     *
     * <p>The two overloads exist so that a caller without a normalizer does not pass
     * a null, not because the two cases differ: this is the single implementation,
     * and it installs at most one focus listener in either case.
     *
     * @param field the text component to view
     * @param timing when the property notifies
     * @param normalizer the tidying to apply on focus loss, or {@code null} to leave
     *     the text exactly as typed
     * @return the two-way view
     */
    private static Property<String> textProperty(JTextComponent field, Timing timing, @Nullable UnaryOperator<String> normalizer) {
        // ON_COMMIT observes focusLost, which no write reaches, so for that timing the
        // write itself is the only notification there is.
        var writeNotification = timing == Timing.ON_COMMIT
            ? ControlProperty.WriteNotification.FROM_WRITE
            : ControlProperty.WriteNotification.FROM_CONTROL;

        // The write goes through BoundText rather than straight to the field: that is
        // what raises the guard the delegating setText checks, so the property writing
        // its own control does not route straight back into itself.
        var property = new ControlProperty<String>(
            field::getText,
            text -> BoundText.write(field, text),
            writeNotification
        );

        // Associating the field is what makes a stray setText on a control this repo
        // owns arrive here as a write rather than being lost.
        BoundText.associate(field, property);

        if (timing == Timing.WHILE_TYPING) {
            field.getDocument().addDocumentListener(new DocumentListener() {

                @Override
                public void insertUpdate(DocumentEvent event) {
                    property.notifyObservers();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    property.notifyObservers();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    property.notifyObservers();
                }
            });
        }

        if (normalizer != null || timing == Timing.ON_COMMIT) {
            field.addFocusListener(new FocusAdapter() {

                @Override
                public void focusLost(FocusEvent event) {
                    if (normalizer != null) {
                        var normalized = normalizer.apply(field.getText());

                        // Writing text the field already holds would churn the
                        // document and notify for nothing.
                        if (!normalized.equals(field.getText())) {
                            field.setText(normalized);
                        }
                    }

                    property.notifyObservers();
                }
            });
        }

        return property;
    }

    /**
     * Returns {@code combo}'s selected item.
     *
     * @param <E> the combo box's item type
     * @param combo the combo box to read
     * @return the selected item
     * @throws IllegalStateException if nothing is selected
     */
    private static <E> E selectedItem(JComboBox<E> combo) {
        var index = combo.getSelectedIndex();

        if (index < 0) {
            throw new IllegalStateException("A combo box viewed as a property must always have a selection, and this one has none");
        }

        return combo.getItemAt(index);
    }

    /**
     * Returns the constant whose button is selected.
     *
     * @param <E> the enum the group offers
     * @param buttons the button for every constant of {@code E}
     * @return the selected constant
     * @throws IllegalStateException if no button in the group is selected
     */
    private static <E extends Enum<E>> E selectedConstant(EnumMap<E, AbstractButton> buttons) {
        for (var entry : buttons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }

        throw new IllegalStateException("A radio group viewed as a property must always have a selection, and this one has none");
    }

    /**
     * Selects {@code constant}'s button.
     *
     * @param <E> the enum the group offers
     * @param buttons the button for every constant of {@code E}
     * @param constant the constant to select
     * @effects selects that constant's button, which the enclosing
     *     {@code ButtonGroup} deselects the others for
     */
    private static <E extends Enum<E>> void select(EnumMap<E, AbstractButton> buttons, E constant) {
        var button = buttons.get(constant);

        if (button == null) {
            throw RuntimeError.exit("radio group has no button for " + constant + ", which the factory should have refused");
        }

        button.setSelected(true);
    }

    /**
     * The one {@link Property} implementation behind every factory in this class: a
     * read function, a write function, and the observer list the control's own
     * listener drives.
     *
     * <p>Every adapter differs only in those two functions and in which Swing
     * listener calls {@link #notifyObservers}, so there is one implementation rather
     * than one per control.
     *
     * @param <T> the viewed value's type
     */
    private static final class ControlProperty<T> implements Property<T> {

        /**
         * Whether a write made through this view notifies on its own.
         *
         * <p>Which one a factory passes is read straight off the route table in the
         * class documentation: a route the control fires on a programmatic write
         * notifies by itself, and a route it does not fire has nothing else to notify
         * with.
         */
        private enum WriteNotification {
            /** The control's own listener fires on a write, so notifying here too would notify twice. */
            FROM_CONTROL,

            /** No listener of the control's fires on a write, so the write is the notification. */
            FROM_WRITE
        }

        private final Observers observers = new Observers();
        private final Supplier<T> reader;
        private final Consumer<T> writer;
        private final WriteNotification writeNotification;

        private ControlProperty(Supplier<T> reader, Consumer<T> writer, WriteNotification writeNotification) {
            this.reader = reader;
            this.writer = writer;
            this.writeNotification = writeNotification;
        }

        @Override
        public T get() {
            DependencyTracker.track(this);

            return reader.get();
        }

        @Override
        public void set(T value) {
            writer.accept(value);

            if (writeNotification == WriteNotification.FROM_WRITE) {
                observers.notifyObservers();
            }
        }

        @Override
        public Subscription observe(Runnable onChange) {
            return observers.add(onChange);
        }

        /**
         * Notifies this property's observers, called by the control's own Swing
         * listener when the control reports a change.
         */
        void notifyObservers() {
            observers.notifyObservers();
        }
    }
}
