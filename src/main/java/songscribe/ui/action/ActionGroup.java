package songscribe.ui.action;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;

import org.jspecify.annotations.Nullable;

public class ActionGroup<T extends UIAction> implements PropertyChangeListener {

    private final List<T> actions = new ArrayList<>();
    @Nullable private T selected = null;
    @Nullable private T previousSelected = null;
    @Nullable private T defaultAction = null;
    private int selectLevel = 0;

    public ActionGroup() {}

    @SafeVarargs
    public ActionGroup(T... actions) {
        for (var action : actions) {
            add(action);
        }
    }

    public List<T> getActions() {
        return actions;
    }

    @Nullable
    public T getSelected() {
        return selected;
    }

    @Nullable
    public T getPreviousSelected() {
        return previousSelected;
    }

    public void add(T action) {
        if (!actions.contains(action)) {
            actions.add(action);
        }

        action.addPropertyChangeListener(this);
    }

    public void insert(int index, T action) {
        if (!actions.contains(action)) {
            actions.add(index, action);
        }

        action.addPropertyChangeListener(this);
    }

    public void remove(T action) {
        actions.remove(action);
        action.removePropertyChangeListener(this);
    }

    public void setDefaultAction(@Nullable T defaultAction) {
        this.defaultAction = defaultAction;
    }

    public void clearSelection() {
        if (selected != null) {
            selected.putValue(Action.SELECTED_KEY, false);
            selected = null;
        }
    }

    public void reset() {
        if (defaultAction != null) {
            setSelected(defaultAction, true);
        } else {
            clearSelection();
        }
    }

    public boolean contains(T action) {
        return actions.contains(action);
    }

    public boolean anySelected() {
        return actions.stream().anyMatch(
            a -> a instanceof UIAction.Selectable && ((UIAction.Selectable) a).isSelected()
        );
    }

    public void setSelected(T action, boolean value) {
        if (!actions.contains(action)) {
            return;
        }

        selectLevel++;
        action.putValue(Action.SELECTED_KEY, value);

        if (selected == action) {
            if (!value) {
                selected = null;
            }

            selectLevel--;
            return;
        }

        if (value && selected != null) {
            previousSelected = selected;
        }

        if (selected != null) {
            selected.putValue(Action.SELECTED_KEY, false);
        }

        selected = value ? action : null;
        selectLevel--;
    }

    public void select(T action, Object source) {
        var changed = selected != action;
        setSelected(action, true);

        if (changed) {
            action.perform(source);
        }
    }

    public void selectNext() {
        if (selected == null) {
            setSelected(actions.getFirst(), true);
            return;
        }

        var index = actions.indexOf(selected);

        if (index == -1) {
            return;
        }

        index = (index + 1) % actions.size();
        setSelected(actions.get(index), true);
    }

    public boolean isSelected(T action) {
        return selected == action;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void propertyChange(PropertyChangeEvent e) {
        if (selectLevel == 0 && Action.SELECTED_KEY.equals(e.getPropertyName())) {
            setSelected((T) e.getSource(), (Boolean) e.getNewValue());
        }
    }
}
