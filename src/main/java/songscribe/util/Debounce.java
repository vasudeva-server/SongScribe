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
 * no trigger arrives before it passes.
 *
 * <p>Use {@link #rescheduling} for occasional triggers and {@link #polling} for a trigger at
 * input-device frequency.
 *
 * <p>Not thread-safe: construct, trigger and cancel on the EDT.
 */
public abstract class Debounce {

    private final Runnable action;

    private Debounce(Runnable action) {
        this.action = action;
    }

    /** The action runs exactly {@code delayMs} after the final trigger. */
    public static Debounce rescheduling(int delayMs, Runnable action) {
        return new ReschedulingDebounce(delayMs, action);
    }

    /**
     * For triggers on a hot path, where {@link Timer#restart()} is too expensive per trigger.
     * The action runs between {@code delayMs} and {@code delayMs + pollIntervalMs} after the
     * final trigger.
     */
    public static Debounce polling(int delayMs, int pollIntervalMs, Runnable action) {
        return new PollingDebounce(delayMs, pollIntervalMs, action);
    }

    public abstract void trigger();

    public abstract void cancel();

    public abstract boolean isArmed();

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
