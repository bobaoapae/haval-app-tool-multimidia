package br.com.redesurftank.havalshisuku.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousTelemetryPayloadTest {

    @Test
    fun vehicleUuidIsStableForSameVinAndSalt() {
        val a = AnonymousTelemetryPayload.vehicleUuid("lgwffua59ra123456", "salt")
        val b = AnonymousTelemetryPayload.vehicleUuid("LGWFFUA59RA123456", "salt")
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun vehicleUuidChangesWithSalt() {
        val a = AnonymousTelemetryPayload.vehicleUuid("LGWFFUA59RA123456", "salt-a")
        val b = AnonymousTelemetryPayload.vehicleUuid("LGWFFUA59RA123456", "salt-b")
        assertTrue(a != b)
    }

    @Test
    fun vinPrefixTakesFirstEight() {
        assertEquals("LGWFFUA5", AnonymousTelemetryPayload.vinPrefix("LGWFFUA59RA123456"))
        assertEquals("SHORT", AnonymousTelemetryPayload.vinPrefix("short"))
    }

    @Test
    fun odometerBucketsToThousandKm() {
        assertEquals(12_000, AnonymousTelemetryPayload.odometerBucketKm("12345"))
        assertEquals(12_000, AnonymousTelemetryPayload.odometerBucketKm("12999.9"))
        assertEquals(0, AnonymousTelemetryPayload.odometerBucketKm("999"))
        assertNull(AnonymousTelemetryPayload.odometerBucketKm(null))
        assertNull(AnonymousTelemetryPayload.odometerBucketKm("abc"))
    }

    @Test
    fun debounceRespectsFiveMinuteWindow() {
        val t0 = 1_000_000L
        assertFalse(AnonymousTelemetryPayload.shouldDebounce(0L, t0))
        assertTrue(AnonymousTelemetryPayload.shouldDebounce(t0, t0 + 60_000L))
        assertFalse(
                AnonymousTelemetryPayload.shouldDebounce(
                        t0,
                        t0 + AnonymousTelemetryPayload.DEBOUNCE_MS
                )
        )
    }

    @Test
    fun themeChangedOnlyWhenPreviousExistsAndDiffers() {
        assertFalse(AnonymousTelemetryPayload.themeChanged("Minimalist", null))
        assertFalse(AnonymousTelemetryPayload.themeChanged("Minimalist", "Minimalist"))
        assertTrue(AnonymousTelemetryPayload.themeChanged("Minimalist", "Default"))
    }

    @Test
    fun buildPropertiesOmitsBlankOptionalFieldsAndNeverIncludesRawVin() {
        val props =
                AnonymousTelemetryPayload.buildProperties(
                        vehicleUuid = "abc",
                        vinPrefix = "LGWFFUA5",
                        vehicleModel1 = "H6",
                        vehicleModel2 = "  ",
                        configureCode = null,
                        trimLevel = "Premium",
                        carMode1 = "",
                        engineType = "HEV",
                        projectName = "proj",
                        odometerKmBucket = 40_000,
                        theme = "Default",
                        themeChanged = false,
                        powerOnCount = 3L,
                        virtualClusterEnabled = true,
                        settings = mapOf("enable_hot_router" to true),
                        appVersion = "1.2.3",
                        versionCode = 42
                )
        assertEquals(false, props["\$process_person_profile"])
        assertEquals("H6", props["vehicle_model1"])
        assertFalse(props.containsKey("vehicle_model2"))
        assertFalse(props.containsKey("configure_code"))
        assertFalse(props.containsKey("car_mode1"))
        assertEquals("Premium", props["trim_level"])
        assertEquals(40_000, props["odometer_km_bucket"])
        assertEquals(true, props["enable_hot_router"])
        assertFalse(props.values.any { it is String && it.contains("LGWFFUA59") })
    }
}
