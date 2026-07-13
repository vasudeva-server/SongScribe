/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe;

import java.awt.GraphicsEnvironment;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Marks a test class, nested class, or method that instantiates a real Swing window
 * (a {@code JFrame}, the {@code MainFrame} singleton, or a visible {@code BaseDialog})
 * and therefore throws {@link java.awt.HeadlessException} when no display is reachable.
 *
 * <p>Such tests are disabled when the JVM is headless — e.g. launched from an SSH /
 * {@code Background} login session, or forked by a Gradle daemon that was born in one —
 * and run normally in a GUI ({@code Aqua}) session. {@code scripts/test.sh} restarts a
 * stale daemon so a GUI session never silently skips these.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresDisplay.NotHeadless.class)
public @interface RequiresDisplay {

    class NotHeadless implements ExecutionCondition {

        private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("a display is available");

        private static final ConditionEvaluationResult DISABLED =
            ConditionEvaluationResult.disabled("requires a display; skipped in headless mode");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (GraphicsEnvironment.isHeadless()) {
                return DISABLED;
            }

            return ENABLED;
        }
    }
}
