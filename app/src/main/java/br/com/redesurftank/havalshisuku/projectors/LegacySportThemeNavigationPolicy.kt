package br.com.redesurftank.havalshisuku.projectors

/**
 * Private host adapter for the immutable SportRed 0.16.44 bundles.
 *
 * Contract v1 themes own their navigation through `window.onKeyEvent`. The two pinned Sport
 * packages predate that handler and expose only `showScreen`, `focus` and `control`, so the host
 * must translate the already-processed native menu events for those themes only.
 */
object LegacySportThemeNavigationPolicy {
    fun buildMenuFocusJavascript(targetItem: String): String {
        val target = quoteJavascriptString(targetItem)
        return "(function(){try{" +
            "if(typeof window.control==='function'){window.control('menuNav',$target);}" +
            "if(typeof window.focus==='function'){window.focus($target);return 'ok:focus';}" +
            "return 'missing:focus';" +
            "}catch(e){return 'threw:'+e;}})()"
    }

    fun buildFocusJavascript(target: String): String {
        val quotedTarget = quoteJavascriptString(target)
        return "(function(){try{" +
            "if(typeof window.focus==='function'){window.focus($quotedTarget);return 'ok:focus';}" +
            "return 'missing:focus';" +
            "}catch(e){return 'threw:'+e;}})()"
    }

    fun buildShowScreenJavascript(screen: String): String {
        val quotedScreen = quoteJavascriptString(screen)
        return "(function(){try{" +
            "if(typeof window.showScreen==='function'){window.showScreen($quotedScreen);return 'ok:screen';}" +
            "return 'missing:showScreen';" +
            "}catch(e){return 'threw:'+e;}})()"
    }

    fun buildControlJavascript(key: String, value: String): String {
        val quotedKey = quoteJavascriptString(key)
        val quotedValue = quoteJavascriptString(value)
        return "(function(){try{" +
            "if(typeof window.control==='function'){window.control($quotedKey,$quotedValue);return 'ok:control';}" +
            "return 'missing:control';" +
            "}catch(e){return 'threw:'+e;}})()"
    }

    private fun quoteJavascriptString(value: String): String {
        return buildString(value.length + 2) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
    }
}
