package br.com.redesurftank.displaypilot;

/** A bounded test window, in integer percentages of the accessible display area. */
public final class Viewport {
    public final int left, top, width, height;

    public Viewport(int left, int top, int width, int height) {
        if (left < 0 || top < 0 || width < 10 || height < 10
                || width > 40 || height > 60 || left > 100 - width || top > 100 - height) {
            throw new IllegalArgumentException(
                    "Área inválida: largura 10–40%, altura 10–60%; a janela deve caber na tela.");
        }
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    public Pixels pixels(int displayWidth, int displayHeight) {
        if (displayWidth <= 0 || displayHeight <= 0) throw new IllegalArgumentException("Sem dimensões.");
        int x = (int) ((long) displayWidth * left / 100);
        int y = (int) ((long) displayHeight * top / 100);
        int right = (int) ((long) displayWidth * (left + width) / 100);
        int bottom = (int) ((long) displayHeight * (top + height) / 100);
        if (right <= x || bottom <= y) throw new IllegalArgumentException("Área pequena demais.");
        return new Pixels(x, y, right - x, bottom - y);
    }

    public static final class Pixels {
        public final int x, y, width, height;
        Pixels(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
}
