package songscribe.ui;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

/**
 * Abstraction over static FlatLaf operations to enable test mocking.
 */
interface LafOperations {
    void installLaf(@NotNull LookAndFeel laf) throws UnsupportedLookAndFeelException;

    void showSnapshot();

    void updateUI();

    void hideSnapshotWithAnimation();
}
