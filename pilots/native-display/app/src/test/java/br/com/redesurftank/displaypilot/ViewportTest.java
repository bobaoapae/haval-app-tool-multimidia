package br.com.redesurftank.displaypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportTest {
    @Test public void canonicalViewportScales() {
        Viewport.Pixels bounds = new Viewport(40, 25, 20, 40).pixels(1920, 720);
        assertEquals(768, bounds.x); assertEquals(180, bounds.y); assertEquals(384, bounds.width); assertEquals(288, bounds.height);
    }
    @Test public void portraitAndOddDimensionsStayBounded() {
        Viewport.Pixels p = new Viewport(60, 40, 40, 60).pixels(721, 1281);
        assertEquals(721, p.x + p.width); assertEquals(1281, p.y + p.height);
    }
    @Test(expected = IllegalArgumentException.class) public void offscreenRightRefuses() { new Viewport(90, 0, 20, 20); }
    @Test(expected = IllegalArgumentException.class) public void offscreenBottomRefuses() { new Viewport(0, 90, 20, 20); }
    @Test(expected = IllegalArgumentException.class) public void negativeRefuses() { new Viewport(-1, 0, 20, 20); }
    @Test(expected = IllegalArgumentException.class) public void fullWidthRefuses() { new Viewport(0, 0, 100, 20); }
    @Test(expected = IllegalArgumentException.class) public void fullHeightRefuses() { new Viewport(0, 0, 20, 100); }
    @Test(expected = IllegalArgumentException.class) public void zeroAreaRefuses() { new Viewport(0, 0, 0, 20); }
    @Test(expected = IllegalArgumentException.class) public void tinyWindowRefuses() { new Viewport(0, 0, 9, 20); }
    @Test(expected = IllegalArgumentException.class) public void unavailableGeometryRefuses() { new Viewport(0, 0, 20, 20).pixels(0, 720); }
    @Test(expected = IllegalArgumentException.class) public void subpixelAreaRefuses() { new Viewport(0, 0, 20, 20).pixels(1, 1); }
    @Test(expected = IllegalArgumentException.class) public void extremeInputCannotOverflow() { new Viewport(Integer.MAX_VALUE, 0, 20, 20); }
    @Test public void scalingCannotOverflow() {
        Viewport.Pixels p = new Viewport(60, 40, 40, 60).pixels(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, p.x + p.width); assertEquals(Integer.MAX_VALUE, p.y + p.height);
    }
    @Test public void exhaustiveSmallGridStaysBounded() {
        for (int x = 0; x <= 60; x++) for (int y = 0; y <= 40; y++) {
            Viewport.Pixels p = new Viewport(x, y, 40, 60).pixels(1373, 719);
            assertTrue(p.x >= 0 && p.y >= 0 && p.width > 0 && p.height > 0);
            assertTrue(p.x + p.width <= 1373 && p.y + p.height <= 719);
        }
    }
}
