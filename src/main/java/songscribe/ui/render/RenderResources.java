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

package songscribe.ui.render;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import net.engio.mbassy.listener.Handler;

import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.FontDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.MyFontUtils;

/**
 * Owns the {@link Font} and {@link FontMetrics} objects used to render a score.
 * <p>
 * All fonts are sourced from user preferences and rebuilt on
 * {@link FontDidChangeNotification}: title, lyrics, attribution, annotation,
 * bangla, and footnote.
 * <p>
 * <b>Lifetime:</b> application-singleton. The private {@code INSTANCE} field keeps
 * the MBassador subscription reachable for the life of the JVM.
 */
public final class RenderResources {

    private static final RenderResources INSTANCE = new RenderResources();

    private Font titleFont;
    private FontMetrics titleFontMetrics;
    private Font lyricsFont;
    private FontMetrics lyricsFontMetrics;
    private Font attributionFont;
    private FontMetrics attributionFontMetrics;
    private Font annotationFont;
    private FontMetrics annotationFontMetrics;
    private Font banglaFont;
    private FontMetrics banglaFontMetrics;
    private Font footnoteFont;
    private FontMetrics footnoteFontMetrics;

    private RenderResources() {
        var prefs = Prefs.getInstance();
        var g = scratchGraphics();
        try {
            titleFont = MyFontUtils.createFont(
                prefs.getString(PrefsKey.TITLE_FONT),
                prefs.getInt(PrefsKey.TITLE_FONT_SIZE)
            );
            titleFontMetrics = g.getFontMetrics(titleFont);

            lyricsFont = MyFontUtils.createFont(
                prefs.getString(PrefsKey.LYRICS_FONT),
                prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)
            );
            lyricsFontMetrics = g.getFontMetrics(lyricsFont);

            attributionFont = MyFontUtils.createFont(
                prefs.getString(PrefsKey.ATTRIBUTION_FONT),
                prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)
            );
            attributionFontMetrics = g.getFontMetrics(attributionFont);

            annotationFont = MyFontUtils.createFont(
                prefs.getString(PrefsKey.ANNOTATION_FONT),
                prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)
            );
            annotationFontMetrics = g.getFontMetrics(annotationFont);

            banglaFont = MyFontUtils.createFont(
                prefs.getString(PrefsKey.BANGLA_FONT),
                prefs.getInt(PrefsKey.BANGLA_FONT_SIZE)
            );
            banglaFontMetrics = g.getFontMetrics(banglaFont);

            footnoteFont = MyFontUtils.createFont(
                prefs.getString(PrefsKey.FOOTNOTE_FONT),
                prefs.getInt(PrefsKey.FOOTNOTE_FONT_SIZE)
            );
            footnoteFontMetrics = g.getFontMetrics(footnoteFont);
        } finally {
            g.dispose();
        }

        MessageCenter.subscribe(this);
    }

    // Allocating one BufferedImage per metrics lookup is wasteful when several
    // fonts are measured in a single pass; callers share one scratch Graphics
    // and dispose it after the batch.
    private static Graphics scratchGraphics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();
    }

    public static Font getTitleFont() {
        return INSTANCE.titleFont;
    }

    public static FontMetrics getTitleFontMetrics() {
        return INSTANCE.titleFontMetrics;
    }

    public static Font getLyricsFont() {
        return INSTANCE.lyricsFont;
    }

    public static FontMetrics getLyricsFontMetrics() {
        return INSTANCE.lyricsFontMetrics;
    }

    public static Font getAttributionFont() {
        return INSTANCE.attributionFont;
    }

    public static FontMetrics getAttributionFontMetrics() {
        return INSTANCE.attributionFontMetrics;
    }

    public static Font getAnnotationFont() {
        return INSTANCE.annotationFont;
    }

    public static FontMetrics getAnnotationFontMetrics() {
        return INSTANCE.annotationFontMetrics;
    }

    public static Font getBanglaFont() {
        return INSTANCE.banglaFont;
    }

    public static FontMetrics getBanglaFontMetrics() {
        return INSTANCE.banglaFontMetrics;
    }

    public static Font getFootnoteFont() {
        return INSTANCE.footnoteFont;
    }

    public static FontMetrics getFootnoteFontMetrics() {
        return INSTANCE.footnoteFontMetrics;
    }

    @Handler
    public void fontDidChange(FontDidChangeNotification message) {
        var g = scratchGraphics();
        try {
            if (message.getTitleFont() != null) {
                titleFont = message.getTitleFont();
                titleFontMetrics = g.getFontMetrics(titleFont);
            }

            if (message.getLyricsFont() != null) {
                lyricsFont = message.getLyricsFont();
                lyricsFontMetrics = g.getFontMetrics(lyricsFont);
            }

            if (message.getAttributionFont() != null) {
                attributionFont = message.getAttributionFont();
                attributionFontMetrics = g.getFontMetrics(attributionFont);
            }

            if (message.getAnnotationFont() != null) {
                annotationFont = message.getAnnotationFont();
                annotationFontMetrics = g.getFontMetrics(annotationFont);
            }

            if (message.getBanglaFont() != null) {
                banglaFont = message.getBanglaFont();
                banglaFontMetrics = g.getFontMetrics(banglaFont);
            }

            if (message.getFootnoteFont() != null) {
                footnoteFont = message.getFootnoteFont();
                footnoteFontMetrics = g.getFontMetrics(footnoteFont);
            }
        } finally {
            g.dispose();
        }
    }

    // Sync document-specific font overrides from a freshly loaded Song. Called when
    // ScoreView installs a new document — Song.loadFrom sets fonts directly without
    // posting FontDidChangeNotification, so we read from Song here to stay consistent.
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        var song = message.getSong();
        var g = scratchGraphics();
        try {
            titleFont = MyFontUtils.createFont(song.getTitleFontName(), song.getTitleFontSize());
            titleFontMetrics = g.getFontMetrics(titleFont);

            lyricsFont = MyFontUtils.createFont(song.getLyricsFontName(), song.getLyricsFontSize());
            lyricsFontMetrics = g.getFontMetrics(lyricsFont);

            attributionFont = MyFontUtils.createFont(song.getAttributionFontName(), song.getAttributionFontSize());
            attributionFontMetrics = g.getFontMetrics(attributionFont);

            annotationFont = MyFontUtils.createFont(song.getAnnotationFontName(), song.getAnnotationFontSize());
            annotationFontMetrics = g.getFontMetrics(annotationFont);

            banglaFont = MyFontUtils.createFont(song.getBanglaFontName(), song.getBanglaFontSize());
            banglaFontMetrics = g.getFontMetrics(banglaFont);

            footnoteFont = MyFontUtils.createFont(song.getFootnoteFontName(), song.getFootnoteFontSize());
            footnoteFontMetrics = g.getFontMetrics(footnoteFont);
        } finally {
            g.dispose();
        }
    }
}
