package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerPresencePolicyTest {

    // --- api level ---------------------------------------------------------

    @Test
    fun missingMetaDataReadsAsNoApi() {
        assertEquals(ViewerPresencePolicy.API_NONE, ViewerPresencePolicy.parseApiLevel(null))
    }

    @Test
    fun readsBothTypesManifestMetaDataComesBackAs() {
        assertEquals(2, ViewerPresencePolicy.parseApiLevel(2))
        assertEquals(2, ViewerPresencePolicy.parseApiLevel(" 2 "))
    }

    @Test
    fun malformedDeclarationIsNoApiRatherThanACrash() {
        assertEquals(ViewerPresencePolicy.API_NONE, ViewerPresencePolicy.parseApiLevel("dois"))
        assertEquals(ViewerPresencePolicy.API_NONE, ViewerPresencePolicy.parseApiLevel(-3))
        assertEquals(ViewerPresencePolicy.API_NONE, ViewerPresencePolicy.parseApiLevel(Any()))
    }

    // --- signing -----------------------------------------------------------

    @Test
    fun withNothingPinnedAnySignerIsAccepted() {
        assertTrue(ViewerPresencePolicy.isTrusted("aa11", emptySet()))
        assertTrue(ViewerPresencePolicy.isTrusted(null, emptySet()))
    }

    @Test
    fun withAPinOnlyThatSignerIsAccepted() {
        val pinned = setOf("aa11")
        assertTrue(ViewerPresencePolicy.isTrusted("AA11", pinned))
        assertFalse(ViewerPresencePolicy.isTrusted("bb22", pinned))
        assertFalse(ViewerPresencePolicy.isTrusted(null, pinned))
    }

    // --- the gate ----------------------------------------------------------

    @Test
    fun presenceOnlyFeaturesDoNotNeedADeclaredApi() {
        assertTrue(
            ViewerPresencePolicy.supports(
                installed = true,
                enabled = true,
                trusted = true,
                apiLevel = ViewerPresencePolicy.API_NONE,
                minApiLevel = ViewerPresencePolicy.API_PRESENT_ONLY
            )
        )
    }

    @Test
    fun anOlderViewerDoesNotUnlockANewerFeature() {
        assertFalse(
            ViewerPresencePolicy.supports(
                installed = true,
                enabled = true,
                trusted = true,
                apiLevel = ViewerPresencePolicy.API_CLIENT,
                minApiLevel = ViewerPresencePolicy.API_CLIMATE_HANDOFF
            )
        )
    }

    @Test
    fun installedButDisabledOrUntrustedOffersNothing() {
        assertFalse(
            ViewerPresencePolicy.supports(
                installed = true,
                enabled = false,
                trusted = true,
                apiLevel = ViewerPresencePolicy.API_CLIENT,
                minApiLevel = ViewerPresencePolicy.API_PRESENT_ONLY
            )
        )
        assertFalse(
            ViewerPresencePolicy.supports(
                installed = true,
                enabled = true,
                trusted = false,
                apiLevel = ViewerPresencePolicy.API_CLIENT,
                minApiLevel = ViewerPresencePolicy.API_PRESENT_ONLY
            )
        )
    }

    @Test
    fun nothingIsOfferedWhenTheViewerIsAbsent() {
        assertFalse(
            ViewerPresencePolicy.supports(
                installed = false,
                enabled = false,
                trusted = true,
                apiLevel = ViewerPresencePolicy.API_NONE,
                minApiLevel = ViewerPresencePolicy.API_PRESENT_ONLY
            )
        )
    }
}
