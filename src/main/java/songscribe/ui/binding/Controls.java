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
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JList;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.SpinnerModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.jspecify.annotations.Nullable;

import songscribe.binding.Bindings;
import songscribe.binding.ObservableValue;
import songscribe.binding.Property;
import songscribe.binding.Subscription;
import songscribe.binding.ViewProperty;
import songscribe.binding.ViewProperty.WriteNotification;
import songscribe.binding.WritableValue;
import songscribe.error.RuntimeError;
import songscribe.ui.component.TickSlider;

/**
 * Property views over the Swing controls a user edits — the framework's <i>sources</i>.
 * Presentation state a dialog merely computes lives in {@link Widgets} as a
 * {@link WritableValue} instead.
 *
 * <p>A view is not a copy: the control remains the storage and nothing is cached, so a control
 * changed by code that knows nothing of this package is still read correctly — it just does not
 * <i>notify</i> unless the route carries the write.
 *
 * <p>A control this repository does not own does not route {@code setText} into its property,
 * so an {@code ON_COMMIT} text property over one goes stale on a programmatic write; bind it
 * {@code WHILE_TYPING} or write through the property.
 *
 * <p>Every adapter names, in its own contract, the Swing route it observes and whether that
 * route fires on a programmatic write, because the wrong route loses writes and nothing
 * reports the loss.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Each factory registers a listener on the control and never removes it: the property lives
 * as long as the control, and both die with the dialog that built them. Do not build a property
 * over a control that outlives the dialog holding the bindings.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class Controls {

    /** Converted to and from {@code null} here and nowhere else. */
    private static final int NO_SELECTED_INDEX = -1;

    private Controls() {}

    @Nullable
    private static Integer selectionOf(int swingIndex) {
        return (swingIndex == NO_SELECTED_INDEX) ? null : swingIndex;
    }

    /**
     * <b>{@code ON_COMMIT} is the one route here that a programmatic {@code setText} does not
     * reach.</b> A write made through this property notifies on its own, and a write to a
     * control this repository owns ({@code MyJTextField}, {@code MyJTextArea}) is routed into
     * it. A plain {@code JTextComponent} has no such delegation, so a {@code setText} made
     * straight to one is lost and nothing reports it: bind such a field {@code WHILE_TYPING},
     * or write it through the property.
     */
    public static Property<String> text(JTextComponent field, Timing timing) {
        return textProperty(field, timing, null);
    }

    /**
     * The normalizer always runs on focus loss, whichever {@code timing} was asked for. One
     * focus listener normalizes, writes back, then notifies: two separate adapters would make
     * the notified value depend on registration order. One focus loss notifies at most once.
     *
     * @param normalizer must be idempotent, because it is applied to text it has already
     *     produced, and must not return {@code null}
     */
    public static Property<String> text(JTextComponent field, Timing timing, UnaryOperator<String> normalizer) {
        return textProperty(field, timing, normalizer);
    }

    /**
     * @param combo must be uneditable, because an editable combo's editor text can diverge
     *     from the selection, and must always have a selection
     */
    public static <E> Property<E> item(JComboBox<E> combo) {
        var property = new ViewProperty<E>(
            () -> selectedItem(combo),
            combo::setSelectedItem,
            WriteNotification.FROM_SOURCE
        );
        combo.addActionListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * "Nothing selected" is {@code null}, so a binding over this property tests for null, never
     * for a number.
     *
     * @param combo must be uneditable, for the reason {@link #item} gives
     * @return Its {@code set} clears the selection when given {@code null}, and throws
     *     {@link IllegalArgumentException} when given an index the combo does not hold.
     */
    public static Property<@Nullable Integer> itemIndex(JComboBox<?> combo) {
        var property = new ViewProperty<@Nullable Integer>(
            () -> selectionOf(combo.getSelectedIndex()),
            index -> combo.setSelectedIndex(index == null ? NO_SELECTED_INDEX : index),
            WriteNotification.FROM_SOURCE
        );
        combo.addActionListener(event -> property.notifyObservers());

        return property;
    }

    /** Observes items, not actions: an {@code ActionListener} would lose every programmatic write. */
    public static Property<Boolean> selected(AbstractButton button) {
        var property = new ViewProperty<Boolean>(
            button::isSelected,
            button::setSelected,
            WriteNotification.FROM_SOURCE
        );
        button.addItemListener(event -> property.notifyObservers());

        return property;
    }

    /** @param model its values must be {@link Number}s, and its value never {@code null} */
    public static Property<Number> number(SpinnerModel model) {
        var property = new ViewProperty<Number>(
            () -> (Number) model.getValue(),
            model::setValue,
            WriteNotification.FROM_SOURCE
        );
        model.addChangeListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * Notifies throughout a drag, once per intermediate value. A caller wanting drag-end only
     * reads the slider directly rather than binding it.
     */
    public static Property<Integer> value(JSlider slider) {
        var property = new ViewProperty<Integer>(
            slider::getValue,
            slider::setValue,
            WriteNotification.FROM_SOURCE
        );
        slider.addChangeListener(event -> property.notifyObservers());

        return property;
    }

    /**
     * The value is an index into the list's current data, so the caller has the data in place
     * first; a selection written into an empty list is silently nothing. "Nothing selected" is
     * {@code null}.
     */
    public static Property<@Nullable Integer> selectedIndex(JList<?> list) {
        var property = new ViewProperty<@Nullable Integer>(
            () -> selectionOf(list.getSelectedIndex()),
            index -> {
                if (index == null) {
                    list.clearSelection();
                } else {
                    list.setSelectedIndex(index);
                }
            },
            WriteNotification.FROM_SOURCE
        );
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                property.notifyObservers();
            }
        });

        return property;
    }

    /**
     * Reports a landing on a stop and nothing else, not every pixel of a drag. A value that is
     * not exactly a stop lands on the nearest one.
     *
     * <p><b>The tick route does not fire on a programmatic write</b>, so the write through this
     * property is the only notification; a change to {@code setSnappedValue} that started
     * reporting would announce every such change twice.
     */
    public static Property<Integer> tick(TickSlider slider) {
        var property = new ViewProperty<Integer>(
            slider::getValue,
            slider::setSnappedValue,
            WriteNotification.FROM_WRITE
        );
        slider.addTickListener(property::notifyObservers);

        return property;
    }

    /**
     * <b>The value is read from the group, never by polling the buttons.</b> Swing turns the
     * outgoing button off and announces that before turning the incoming one on, so in between
     * no button reports itself selected while the group already names the incoming one. Do not
     * change this to a scan of {@code isSelected()}.
     *
     * <p>A group with no selection has no value, and a caller binding one must select a button
     * before the first read.
     */
    public static <E extends Enum<E>> Property<E> radioGroup(EnumMap<E, AbstractButton> buttons) {
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

        var byConstant = new EnumMap<>(buttons);
        var group = new ButtonGroup();
        var byModel = new HashMap<ButtonModel, E>();

        for (var entry : byConstant.entrySet()) {
            group.add(entry.getValue());
            byModel.put(entry.getValue().getModel(), entry.getKey());
        }

        var property = new ViewProperty<E>(
            () -> chosenConstant(group, byModel),
            constant -> select(byConstant, constant),
            WriteNotification.FROM_SOURCE
        );

        for (var button : byConstant.values()) {
            button.addItemListener(event -> {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    property.notifyObservers();
                }
            });
        }

        return property;
    }

    /** @param normalizer {@code null} to leave the text exactly as typed */
    private static Property<String> textProperty(JTextComponent field, Timing timing, @Nullable UnaryOperator<String> normalizer) {
        // ON_COMMIT observes focusLost, which no write reaches, so for that timing the
        // write itself is the only notification there is.
        var writeNotification = timing == Timing.ON_COMMIT
            ? WriteNotification.FROM_WRITE
            : WriteNotification.FROM_SOURCE;

        // The write goes through BoundText rather than straight to the field: that is
        // what raises the guard the delegating setText checks, so the property writing
        // its own control does not route straight back into itself.
        var property = new ViewProperty<String>(
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
                            // Through BoundText rather than the control, so a control
                            // this repo owns does not route the write back into the
                            // property and notify a second time for one focus loss.
                            BoundText.write(field, normalized);
                        }
                    }

                    // The write above is its own notification for WHILE_TYPING, whose
                    // document route carries it. ON_COMMIT observes focus loss, which
                    // no write reaches, so this is the only notification there is —
                    // and it is still owed when the normalizer changed nothing.
                    if (timing == Timing.ON_COMMIT) {
                        property.notifyObservers();
                    }
                }
            });
        }

        return property;
    }

    private static <E> E selectedItem(JComboBox<E> combo) {
        var index = combo.getSelectedIndex();

        if (index < 0) {
            throw new IllegalStateException("A combo box viewed as a property must always have a selection, and this one has none");
        }

        return combo.getItemAt(index);
    }

    private static <E extends Enum<E>> E chosenConstant(ButtonGroup group, Map<ButtonModel, E> constants) {
        var selection = group.getSelection();
        var constant = selection == null ? null : constants.get(selection);

        if (constant == null) {
            throw new IllegalStateException("A radio group viewed as a property must always have a selection, and this one has none");
        }

        return constant;
    }

    private static <E extends Enum<E>> void select(EnumMap<E, AbstractButton> buttons, E constant) {
        var button = buttons.get(constant);

        if (button == null) {
            throw RuntimeError.exit("radio group has no button for " + constant + ", which the factory should have refused");
        }

        button.setSelected(true);
    }

}
