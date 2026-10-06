package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateCheckerTest {
    @Test
    fun comparesPreviewTags() {
        assertTrue(
                ReleaseUpdateChecker.compareVersions(
                        "v1.0.0.71-preview",
                        "1.0.0.70-preview"
                ) > 0
        )
        assertEquals(
                0,
                ReleaseUpdateChecker.compareVersions(
                        "v1.0.0.71-preview",
                        "1.0.0.71-preview"
                )
        )
    }

    @Test
    fun preservesNumericBuildSuffixesInInternalVersions() {
        assertTrue(
                ReleaseUpdateChecker.compareVersions(
                        "1.0.0.263-full-preview-buttons",
                        "v1.0.0.71-preview"
                ) > 0
        )
    }
}
