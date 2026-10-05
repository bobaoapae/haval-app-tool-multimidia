package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPanelUpdaterTest {
    private val sha = "9220c32fe046c0cc372f8ce1b30b48f4b2fa348e7cffa055db558df29414462c"
    private val base = "https://github.com/leonardovin/shizuku-bottom-bar-releases/releases/download"

    private fun json(
        tag: String = "v1.18.0",
        assetName: String = "haval-bottom-bar-$tag.apk",
        url: String = "$base/$tag/haval-bottom-bar-$tag.apk",
        digest: String = "sha256:$sha"
    ) =
        """{"tag_name":"$tag","assets":[
        {"name":"haval-bottom-bar-latest.apk","browser_download_url":"$base/$tag/haval-bottom-bar-latest.apk","size":1,"digest":"sha256:${"0".repeat(64)}"},
        {"name":"$assetName","browser_download_url":"$url","size":34757245,"digest":"$digest"}]}"""

    @Test
    fun parsesPublishedRelease() {
        val m = AutoPanelUpdater.parseLatestRelease(json())
        assertNotNull(m)
        assertEquals("v1.18.0", m!!.versionName)
        assertEquals("$base/v1.18.0/haval-bottom-bar-v1.18.0.apk", m.apkUrl)
        assertEquals(sha, m.sha256)
        assertEquals(34757245L, m.bytes)
        assertEquals(AutoPanelUpdater.SIGNER_SHA256, m.signerSha256)
    }

    @Test
    fun signerIsAlwaysThePinnedConstant() {
        val withForgedSigner =
            json().replace("\"tag_name\"", "\"signerSha256\":\"${"a".repeat(64)}\",\"tag_name\"")
        assertEquals(
            AutoPanelUpdater.SIGNER_SHA256,
            AutoPanelUpdater.parseLatestRelease(withForgedSigner)!!.signerSha256
        )
    }

    @Test
    fun rejectsMissingVersionedAsset() =
        assertNull(AutoPanelUpdater.parseLatestRelease(json(assetName = "outro.apk")))

    @Test
    fun rejectsForeignHostOrRepo() {
        assertNull(AutoPanelUpdater.parseLatestRelease(json(url = "https://evil.example/app.apk")))
        assertNull(
            AutoPanelUpdater.parseLatestRelease(
                json(url = "https://github.com/outro/repo/releases/download/v1.18.0/haval-bottom-bar-v1.18.0.apk")
            )
        )
    }

    @Test
    fun rejectsMalformedDigest() {
        assertNull(AutoPanelUpdater.parseLatestRelease(json(digest = "sha256:abc")))
        assertNull(AutoPanelUpdater.parseLatestRelease(json(digest = sha)))
        assertNull(AutoPanelUpdater.parseLatestRelease(json(digest = "sha256:${sha.uppercase()}")))
        assertNull(AutoPanelUpdater.parseLatestRelease(json(digest = "sha1:$sha")))
    }

    @Test
    fun rejectsNonSemverTagAndGarbage() {
        assertNull(AutoPanelUpdater.parseLatestRelease(json(tag = "latest")))
        assertNull(AutoPanelUpdater.parseLatestRelease(json(tag = "1.18.0")))
        assertNull(AutoPanelUpdater.parseLatestRelease("not json"))
        assertNull(AutoPanelUpdater.parseLatestRelease("{}"))
    }

    @Test
    fun updateComparesVersionName() {
        val m = AutoPanelUpdater.parseLatestRelease(json())!!
        assertTrue(AutoPanelUpdater.isUpdateAvailable("v1.17.1", m))
        assertFalse(AutoPanelUpdater.isUpdateAvailable("v1.18.0", m))
        assertFalse(AutoPanelUpdater.isUpdateAvailable("1.18.0", m))
        assertFalse(AutoPanelUpdater.isUpdateAvailable("v1.19.0", m))
        assertFalse(AutoPanelUpdater.isUpdateAvailable(null, m))
    }
}
