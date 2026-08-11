/**
 * The undo/redo engine: it stores the edits the model records, replays them in either
 * direction, and names them for the Edit menu. What it guarantees the rest of the
 * application — the round trip, element identity, a live selection, a truthful modified
 * flag — is stated in {@code docs/undo.md}.
 *
 * <p>Three package invariants hold across everything here.
 *
 * <p><b>It never decides what a mutation is.</b> Mutations are produced by the model as it
 * changes, under the rules in {@code docs/mutations.md}; this package only stores, orders,
 * replays and names them. A change the model did not record is a change undo cannot
 * restore, and the fix belongs there rather than here.
 *
 * <p><b>It changes the document only through the model's own recorded helpers</b>, inside a
 * modification bracket it opened and with the model in replay mode. Undo is therefore an
 * edit like any other as far as subscribers are concerned — they repaint from its
 * notification — and the engine gains no privileged write path into the document.
 *
 * <p><b>EDT only</b>, in every class here; no synchronization is performed anywhere in the
 * package.
 */
@NullMarked
package songscribe.undo;

import org.jspecify.annotations.NullMarked;
