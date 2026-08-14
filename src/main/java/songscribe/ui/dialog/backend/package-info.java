/**
 * The domain half of the dialogs in {@code songscribe.ui.dialog}: the code that reads and writes
 * the document on their behalf, so that they do not.
 *
 * <p><strong>No type in this package's public signatures is a Swing type.</strong> That is the
 * mechanical test the dialog seam is held to, and it can be applied without judgment: a signature
 * naming a {@code JComponent}, a {@code JTextField} or any other widget means logic that is still
 * entangled with the UI and has not finished moving here.
 *
 * <p>The reverse direction has its own line, drawn at state rather than at packages: a dialog may
 * name the domain's static knowledge — durations, tempo types, alignments, the value records
 * built from them — but never a {@code Song}, {@code Line}, {@code StaffElement} or
 * {@code ScoreView}. Those live here, bound at construction by whoever opens the dialog. See
 * {@link songscribe.ui.dialog.DialogBackEnd}.
 */
@NullMarked
package songscribe.ui.dialog.backend;

import org.jspecify.annotations.NullMarked;
