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

package songscribe.ui.action

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.Action

@Suppress("UNCHECKED_CAST")
open class ActionGroup<T : UIAction>() : PropertyChangeListener {
    val actions: MutableList<T> = mutableListOf()

    constructor(vararg actions: T) : this() {
        for (action in actions) {
            add(action)
        }
    }

    var selected: T? = null
        private set

    private var selectLevel = 0

    fun add(action: T) {
        if (action !in actions) {
            actions.add(action)
        }

        action.addPropertyChangeListener(this)
    }

    fun insert(index: Int, action: T) {
        if (action !in actions) {
            actions.add(index, action)
        }

        action.addPropertyChangeListener(this)
    }

    fun remove(action: T) {
        actions.remove(action)
        action.removePropertyChangeListener(this)
    }

    fun clearSelection() {
        if (selected != null) {
            selected?.putValue(Action.SELECTED_KEY, false)
            selected = null
        }
    }

    fun contains(action: T): Boolean {
        return actions.contains(action)
    }

    fun anySelected(): Boolean {
        // It isn't sufficient to check selected != null, because this may be set *after*
        // an action's actionPerformed() is called, and we need to know within the context
        // of that call. So we check if any of the actions in the group are selected.
        return actions.any { it.isSelected }
    }

    fun setSelected(action: T, value: Boolean) {
        if (action !in actions) {
            println("Action is not part of this group")
            return
        }

        selectLevel += 1
        action.putValue(Action.SELECTED_KEY, value)

        if (selected === action) {
            if (!value) {
                selected = null
            }

            selectLevel -= 1
            return
        }

        selected?.putValue(Action.SELECTED_KEY, false)
        selected = action
        selectLevel -= 1
    }

    // Select the action and perform it
    fun select(action: T, source: Any) {
        setSelected(action, true)
        action.perform(source)
    }

    fun selectNext() {
        if (selected == null) {
            setSelected(actions[0], true)
            return
        }

        var index = actions.indexOf(selected)

        if (index == -1) {
            return
        }

        index = (index + 1) % actions.size
        setSelected(actions[index], true)
    }

    fun isSelected(action: T): Boolean {
        return selected === action
    }

    override fun propertyChange(e: PropertyChangeEvent) {
        if (selectLevel == 0 && e.propertyName == Action.SELECTED_KEY) {
            setSelected(e.source as T, e.newValue as Boolean)
        }
    }
}
