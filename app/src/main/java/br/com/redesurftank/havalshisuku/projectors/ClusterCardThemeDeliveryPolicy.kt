package br.com.redesurftank.havalshisuku.projectors

/** Builds the one-way host -> theme card notification with a scoped legacy adapter. */
object ClusterCardThemeDeliveryPolicy {
    enum class Result {
        CONTRACT,
        LEGACY_CONTROL,
        FAILED
    }

    fun buildJavascript(cardId: Int, allowLegacyControlFallback: Boolean): String {
        val legacyFallback =
            if (allowLegacyControlFallback) {
                " if (typeof window.control === 'function') {" +
                    " window.control('cardId', $cardId); return 'ok:legacy-control';" +
                    " }"
            } else {
                ""
            }

        return "(function(){ try {" +
            " if (typeof window.onCardChanged === 'function') {" +
            " window.onCardChanged($cardId); return 'ok:contract';" +
            " }" +
            legacyFallback +
            " return 'missing';" +
            " } catch (e) { return 'threw:' + e; } })()"
    }

    fun classifyResult(result: String?): Result {
        return when {
            result?.contains("ok:contract") == true -> Result.CONTRACT
            result?.contains("ok:legacy-control") == true -> Result.LEGACY_CONTROL
            else -> Result.FAILED
        }
    }
}
