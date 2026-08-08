package br.com.redesurftank.havalshisuku.models.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SportThemeScreenOptionsTest {
    @Test
    public void sportMenusAreOnlyEnabledForSportThemeFolders() {
        assertTrue(DisplaySelectionScreen.isSportTheme("SportRed"));
        assertTrue(DisplaySelectionScreen.isSportTheme("sportredlite"));
        assertFalse(DisplaySelectionScreen.isSportTheme("Default"));
        assertFalse(DisplaySelectionScreen.isSportTheme("Basic"));
        assertFalse(DisplaySelectionScreen.isSportTheme(null));
    }

    @Test
    public void allSevenColorsAreAllowlistedIncludingRedGt() {
        assertEquals("Red GT", ColorSelectionScreen.normalizeColor("Red GT"));
        assertEquals("Purple GT", ColorSelectionScreen.normalizeColor("Purple GT"));
        assertEquals("Red Sport", ColorSelectionScreen.normalizeColor("invalid"));
        assertEquals("Red Sport", ColorSelectionScreen.normalizeColor(null));
    }
}
