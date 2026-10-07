package br.com.redesurftank.displaypilot;

/** Immutable snapshot; never infer that an OEM's display 3 is the desired display. */
public final class DisplayTarget {
    public final int id, width, height, rotation, flags;
    public final String name;
    public final boolean valid, on, privateDisplay;

    public DisplayTarget(int id, String name, int width, int height, int rotation,
            int flags, boolean valid, boolean on, boolean privateDisplay) {
        this.id = id;
        this.name = name;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.flags = flags;
        this.valid = valid;
        this.on = on;
        this.privateDisplay = privateDisplay;
    }

    public String rejection() {
        if (id == 0) return "A tela principal não é um destino do piloto.";
        if (id < 0 || !valid) return "Display inválido ou removido.";
        if (privateDisplay) return "Display privado: acesso recusado pelo piloto.";
        if (!on) return "Display apagado ou estado desconhecido.";
        if (width <= 0 || height <= 0) return "Dimensões do display indisponíveis.";
        return null;
    }

    public boolean sameConfiguration(DisplayTarget other) {
        return other != null && id == other.id && width == other.width && height == other.height
                && rotation == other.rotation && flags == other.flags && valid == other.valid
                && on == other.on && privateDisplay == other.privateDisplay;
    }

    @Override public String toString() {
        return "ID " + id + " · " + name + " · " + width + "×" + height
                + (rejection() == null ? " · candidato" : " · indisponível");
    }
}
