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

package songscribe.util;

import javax.swing.Timer;

/**
 * A deadline that every {@link #trigger()} pushes further out, running its action on the EDT once
 * the deadline finally passes with no further trigger — the "settle for N milliseconds, then act"
 * shape shared by a repaint debounce, an activation gate and a pointer-dwell.
 * <p>
 * Two implementations, differing only in what a trigger costs. Pick by how often the trigger is
 * called, not by taste:
 * <ul>
 *   <li>{@link #rescheduling} for the ordinary case — a trigger arriving occasionally, from an
 *       event the user causes deliberately.</li>
 *   <li>{@link #polling} when the trigger sits on a path that runs at input-device frequency; see
 *       that factory for why {@link #rescheduling} is the wrong tool there.</li>
 * </ul>
 * Not thread-safe: construct, trigger and cancel on the EDT, like the {@link Timer} underneath.
 */
public abstract class Debounce {

    private final Runnable action;

    /**
     * Private so the only subclasses are the two nested here — the choice of mechanism is a
     * cost trade-off between the two, not an extension point.
     */
    private Debounce(Runnable action) {
        this.action = action;
    }

    /**
     * A debounce whose trigger reschedules a one-shot {@link Timer}.
     * <p>
     * The deadline is exact: the action runs {@code delayMs} after the final trigger, with no
     * quantization. Costs a {@code Timer.restart()} per trigger.
     */
    public static Debounce rescheduling(int delayMs, Runnable action) {
        return new ReschedulingDebounce(delayMs, action);
    }

    /**
     * A debounce whose trigger only writes a deadline field, leaving a repeating timer to notice
     * when that deadline passes.
     * <p>
     * For triggers on a hot path. {@link Timer#restart()} is deceptively expensive: Swing keeps
     * every running timer in one process-wide {@code TimerQueue} backed by a {@code DelayQueue},
     * so the {@code stop()} half of a restart is a <b>linear scan</b> of every timer alive
     * anywhere in the application, taken under both the timer's own lock and the queue's — which
     * the {@code TimerQueue} thread is concurrently blocked on. Paying that per mouse-move, to
     * express nothing more than "the deadline moved", is lock traffic for its own sake.
     * <p>
     * Here a trigger is a {@code long} store plus a boolean check, and the timer is touched only
     * when the window opens and when it closes. The cost is quantization: the action runs between
     * {@code delayMs} and {@code delayMs + pollIntervalMs} after the final trigger, so this suits
     * a deadline that is a judgement call ("has the pointer settled?") rather than a promise.
     * Nothing polls while the debounce is idle — the timer stops itself once it fires.
     *
     * @param pollIntervalMs how often to test the deadline; the action's worst-case lateness.
     */
    public static Debounce polling(int delayMs, int pollIntervalMs, Runnable action) {
        return new PollingDebounce(delayMs, pollIntervalMs, action);
    }

    /** Pushes the deadline out to {@code delayMs} from now, arming the debounce if it was idle. */
    public abstract void trigger();

    /** Disarms the debounce, so a pending action never runs. A no-op when already idle. */
    public abstract void cancel();

    /** Whether a trigger is outstanding — armed, with its action still to run. */
    public abstract boolean isArmed();

    /** Protected, not private: the nested subclasses reach it as an inherited member. */
    protected final void runAction() {
        action.run();
    }

    private static final class ReschedulingDebounce extends Debounce {

        private final Timer timer;

        private ReschedulingDebounce(int delayMs, Runnable action) {
            super(action);
            timer = new Timer(delayMs, event -> runAction());
            timer.setRepeats(false);
        }

        @Override
        public void trigger() {
            timer.restart();
        }

        @Override
        public void cancel() {
            timer.stop();
        }

        @Override
        public boolean isArmed() {
            return timer.isRunning();
        }
    }

    private static final class PollingDebounce extends Debounce {

        private static final long NANOS_PER_MS = 1_000_000L;

        private final long delayNanos;
        private final Timer pollTimer;
        private long deadlineNanos;

        private PollingDebounce(int delayMs, int pollIntervalMs, Runnable action) {
            super(action);
            delayNanos = delayMs * NANOS_PER_MS;
            pollTimer = new Timer(pollIntervalMs, event -> pollDeadline());
        }

        @Override
        public void trigger() {
            // nanoTime rather than currentTimeMillis: monotonic, so a clock adjustment mid-window
            // cannot fire the action early or strand it.
            deadlineNanos = System.nanoTime() + delayNanos;

            if (!pollTimer.isRunning()) {
                pollTimer.start();
            }
        }

        @Override
        public void cancel() {
            pollTimer.stop();
        }

        @Override
        public boolean isArmed() {
            // The poll timer runs only between a trigger and its action, so its own running flag
            // already answers this — there is no separate armed state to track.
            return pollTimer.isRunning();
        }

        private void pollDeadline() {
            // Subtract rather than compare, so the test stays correct across nanoTime's
            // documented wraparound.
            if (System.nanoTime() - deadlineNanos < 0) {
                return;
            }

            // Stop before running: the action is free to trigger this debounce again, and doing
            // it in the other order would immediately stop the timer it had just restarted.
            pollTimer.stop();
            runAction();
        }
    }
}
