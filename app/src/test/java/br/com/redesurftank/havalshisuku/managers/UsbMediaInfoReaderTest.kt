package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbMediaInfoReaderTest {
    @Test
    fun parsesUsbSourceAndEscapedTrackSnapshot() {
        val sourceXml = """<map><int name="media_source" value="2" /></map>"""
        val trackXml =
                """
                <map>
                  <string name="last_play_media_info2">{&quot;title&quot;:&quot;Road &amp; Sky&quot;,&quot;path&quot;:&quot;/storage/usb1/Music/road.mp3&quot;,&quot;duration&quot;:240000}</string>
                  <long name="playDuration2" value="241000" />
                  <long name="playPosition2" value="12000" />
                  <boolean name="isPlaying2" value="true" />
                </map>
                """.trimIndent()

        assertEquals(2, UsbMediaSnapshotParser.parseActiveSource(sourceXml))
        assertEquals(
                UsbMediaSnapshot(
                        title = "Road & Sky",
                        path = "/storage/usb1/Music/road.mp3",
                        durationMs = 241000L,
                        positionMs = 12000L,
                        isPlaying = true
                ),
                UsbMediaSnapshotParser.parseTrack(trackXml)
        )
    }

    @Test
    fun parserClampsPositionAndRejectsMalformedPayload() {
        val xml =
                """
                <map>
                  <string name="last_play_media_info2">{&quot;path&quot;:&quot;/mnt/media_rw/disk/song.flac&quot;}</string>
                  <long name="playDuration2" value="1000" />
                  <long name="playPosition2" value="9000" />
                  <boolean name="isPlaying2" value="false" />
                </map>
                """.trimIndent()

        val snapshot = UsbMediaSnapshotParser.parseTrack(xml)
        assertEquals(1000L, snapshot?.positionMs)
        assertFalse(snapshot?.isPlaying ?: true)
        assertNull(
                UsbMediaSnapshotParser.parseTrack(
                        """<map><string name="last_play_media_info2">not-json</string></map>"""
                )
        )
    }

    @Test
    fun mediaProbePathAllowsOnlyKnownUsbRootsAndAudioFiles() {
        assertTrue(UsbMediaSnapshotParser.isSafeMediaPath("/storage/ABCD-1234/Music/song.mp3"))
        assertTrue(UsbMediaSnapshotParser.isSafeMediaPath("/mnt/media_rw/ABCD/song.flac"))
        assertFalse(UsbMediaSnapshotParser.isSafeMediaPath("/data/local/tmp/song.mp3"))
        assertFalse(UsbMediaSnapshotParser.isSafeMediaPath("/storage/ABCD/../secret.mp3"))
        assertFalse(UsbMediaSnapshotParser.isSafeMediaPath("/storage/ABCD/payload.sh"))
        assertFalse(UsbMediaSnapshotParser.isSafeMediaPath("/storage/ABCD/song.mp3\n--help"))
    }
}
