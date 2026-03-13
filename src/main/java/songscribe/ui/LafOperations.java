package songscribe.ui;

import module java.desktop;

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
