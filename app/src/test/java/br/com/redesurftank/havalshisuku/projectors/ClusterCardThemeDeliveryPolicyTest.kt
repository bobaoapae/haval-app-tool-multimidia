package br.com.redesurftank.havalshisuku.projectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCardThemeDeliveryPolicyTest {
    @Test
    fun `contract handler remains the first and canonical delivery path`() {
        val script = ClusterCardThemeDeliveryPolicy.buildJavascript(3, true)

        assertTrue(script.contains("window.onCardChanged(3)"))
        assertTrue(script.contains("window.control('cardId', 3)"))
        assertTrue(
            script.indexOf("window.onCardChanged(3)") <
                script.indexOf("window.control('cardId', 3)")
        )
    }

    @Test
    fun `legacy control adapter is omitted outside trusted Sport themes`() {
        val script = ClusterCardThemeDeliveryPolicy.buildJavascript(1, false)

        assertTrue(script.contains("window.onCardChanged(1)"))
        assertFalse(script.contains("window.control('cardId'"))
    }

    @Test
    fun `classifies WebView callback results`() {
        assertEquals(
            ClusterCardThemeDeliveryPolicy.Result.CONTRACT,
            ClusterCardThemeDeliveryPolicy.classifyResult("\"ok:contract\"")
        )
        assertEquals(
            ClusterCardThemeDeliveryPolicy.Result.LEGACY_CONTROL,
            ClusterCardThemeDeliveryPolicy.classifyResult("\"ok:legacy-control\"")
        )
        assertEquals(
            ClusterCardThemeDeliveryPolicy.Result.FAILED,
            ClusterCardThemeDeliveryPolicy.classifyResult("\"missing\"")
        )
    }
}
