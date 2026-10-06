package br.com.redesurftank.havalshisuku.models

enum class TargetDisplay(val id: Int, val label: String) {
    CLUSTER(1, "Cluster (Display 1)"),
    INSTRUMENT(3, "Projetor Instrumento (Display 3)"),
    HUD(4096, "HUD (Display 4096)");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id }
    }
}
