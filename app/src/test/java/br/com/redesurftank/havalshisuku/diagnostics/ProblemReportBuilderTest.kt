package br.com.redesurftank.havalshisuku.diagnostics

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportBuilderTest {
    @Test
    fun buildsReportTitleFromDescription() {
        val title =
                ProblemReportBuilder.buildReportTitle(
                        "  CarPlay   no D3 voltou   do Mapa para Normal  "
                )

        assertEquals("[Relato] CarPlay no D3 voltou do Mapa para Normal", title)
    }

    @Test
    fun buildsLogFileNameForCurrentDay() {
        val now =
                Calendar.getInstance(Locale.US)
                        .apply {
                            set(2026, Calendar.JUNE, 15, 8, 30, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        .timeInMillis

        assertEquals("cluster-events-20260615.log", ProblemReportBuilder.buildLogFileName(now))
    }

    @Test
    fun trimsLogToLastChars() {
        val text = "0123456789abcdef"
        val trimmed = ProblemReportBuilder.trimToLastChars(text, 6)

        assertTrue(trimmed.contains("ultimos 6 caracteres"))
        assertTrue(trimmed.endsWith("abcdef"))
    }

    @Test
    fun filtersLogcatSnapshotToRelevantLines() {
        val filtered =
                ProblemReportBuilder.filterRelevantLogcatSnapshot(
                        """
                        06-15 12:00:00.000 I random: unrelated system noise
                        06-15 12:00:01.000 I chromium: [INFO:CONSOLE(10)] "cardId change"
                        06-15 12:00:02.000 E AndroidRuntime: FATAL EXCEPTION: main
                        06-15 12:00:03.000 I other: another unrelated line
                        06-15 12:00:04.000 W InstrumentProjector2: [WEBVIEW_STATE_SYNC] cardId=3 display=Mapa
                        """
                                .trimIndent()
                )

        assertTrue(filtered.contains("chromium"))
        assertTrue(filtered.contains("AndroidRuntime"))
        assertTrue(filtered.contains("InstrumentProjector2"))
        assertFalse(filtered.contains("unrelated system noise"))
        assertFalse(filtered.contains("another unrelated line"))
    }

    @Test
    fun buildsCombinedLogExcerptWithDailyLogAndLogcatSnapshot() {
        val excerpt =
                ProblemReportBuilder.buildCombinedLogExcerpt(
                        logFileName = "cluster-events-20260615.log",
                        persistentLogText = "event=webview_console message=erro_no_front",
                        logcatText = "06-15 12:00:00.000 I chromium: console error"
                )

        assertTrue(excerpt.contains("cluster-events-20260615.log"))
        assertTrue(excerpt.contains("event=webview_console"))
        assertTrue(excerpt.contains("chromium: console error"))
    }
}
