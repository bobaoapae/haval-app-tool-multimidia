package br.com.redesurftank.havalshisuku.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentApkInstallTest {

    @Test
    fun stagedPathStaysUnderTmp() {
        assertEquals(
                "/data/local/tmp/com.havalh6.viewer.apk",
                SilentApkInstall.stagedPath("com.havalh6.viewer")
        )
        assertEquals("/data/local/tmp/com.evil_pkg.apk", SilentApkInstall.stagedPath("com.evil/pkg"))
    }

    @Test
    fun scriptCopiesChmodsInstallsThenDeletes() {
        val script =
                SilentApkInstall.installScript(
                        "/storage/emulated/0/Android/data/app/files/com.havalh6.viewer.apk",
                        "/data/local/tmp/com.havalh6.viewer.apk"
                )
        assertTrue(script.startsWith("cp "))
        assertTrue(script.contains("chmod 644"))
        assertTrue(script.contains("pm install -r -t"))
        assertTrue(script.contains("/data/local/tmp/com.havalh6.viewer.apk"))
        assertTrue(script.contains("rm -f"))
        assertTrue(script.contains("ec=\$?"))
        assertTrue(script.contains("exit \$ec"))
    }

    @Test
    fun scriptQuotesSpacesAndSingleQuotes() {
        val script = SilentApkInstall.installScript("/tmp/o'brien app.apk", "/data/local/tmp/x.apk")
        assertTrue(script.contains("'/tmp/o'\"'\"'brien app.apk'"))
    }

    @Test
    fun successRequiresThePmSuccessLine() {
        assertTrue(SilentApkInstall.succeeded("Success"))
        assertFalse(SilentApkInstall.succeeded(""))
        assertFalse(SilentApkInstall.succeeded("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertFalse(SilentApkInstall.succeeded("Success\nFailure [INSTALL_FAILED_INTERNAL]"))
    }

    @Test
    fun installRunsTheStagedScriptWhenShizukuIsUp() {
        val apk = File.createTempFile("launcher", ".apk")
        val commands = mutableListOf<String>()
        try {
            val ok =
                    SilentApkInstall.install(
                            apk,
                            "com.havalh6.viewer",
                            shizukuReady = { true },
                            run = { cmd ->
                                commands += cmd.joinToString(" ")
                                "Success"
                            },
                            log = {},
                    )
            assertTrue(ok)
            assertEquals(1, commands.size)
            assertTrue(commands[0].startsWith("sh -c cp "))
            assertTrue(commands[0].contains("pm install -r -t"))
            assertTrue(commands[0].contains("/data/local/tmp/com.havalh6.viewer.apk"))
        } finally {
            apk.delete()
        }
    }

    @Test
    fun installDoesNotRunWhenShizukuIsDown() {
        val apk = File.createTempFile("launcher", ".apk")
        var calls = 0
        try {
            val ok =
                    SilentApkInstall.install(
                            apk,
                            "com.havalh6.viewer",
                            shizukuReady = { false },
                            run = {
                                calls++
                                "Success"
                            },
                            log = {},
                    )
            assertFalse(ok)
            assertEquals(0, calls)
        } finally {
            apk.delete()
        }
    }

    @Test
    fun aPmFailureIsNotTreatedAsInstalled() {
        val apk = File.createTempFile("launcher", ".apk")
        try {
            val ok =
                    SilentApkInstall.install(
                            apk,
                            "com.havalh6.viewer",
                            shizukuReady = { true },
                            run = { "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]" },
                            log = {},
                    )
            assertFalse(ok)
        } finally {
            apk.delete()
        }
    }
}
