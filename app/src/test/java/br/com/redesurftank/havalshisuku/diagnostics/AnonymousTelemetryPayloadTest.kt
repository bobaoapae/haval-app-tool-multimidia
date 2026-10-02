package br.com.redesurftank.havalshisuku.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousTelemetryPayloadTest {

    @Test
    fun vehicleUuidIsStableForSameDeviceIdAndSalt() {
        val a = AnonymousTelemetryPayload.vehicleUuid("a1b2c3d4e5f60718", "salt")
        val b = AnonymousTelemetryPayload.vehicleUuid("A1B2C3D4E5F60718", "salt")
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun vehicleUuidChangesWithSalt() {
        val a = AnonymousTelemetryPayload.vehicleUuid("a1b2c3d4e5f60718", "salt-a")
        val b = AnonymousTelemetryPayload.vehicleUuid("a1b2c3d4e5f60718", "salt-b")
        assertTrue(a != b)
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
    fun buildPropertiesOmitsBlankOptionalFields() {
        val props =
                AnonymousTelemetryPayload.buildProperties(
                        vehicleUuid = "abc",
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
    }

    @Test
    fun buildPropertiesCarriesNothingVinDerived() {
        val props = fleetProps()
        assertFalse(props.containsKey("vin_prefix"))
        assertFalse(props.keys.any { it.contains("vin", ignoreCase = true) })
        assertFalse(props.values.any { it is String && it.startsWith("LGW") })
    }

    private fun fleetProps(): Map<String, Any?> =
            AnonymousTelemetryPayload.buildProperties(
                    vehicleUuid = "abc",
                    vehicleModel1 = "H6",
                    vehicleModel2 = "B01G-1",
                    configureCode = "CC6470AH25DPHEV",
                    trimLevel = "3",
                    carMode1 = "B01G-1",
                    engineType = "4",
                    projectName = "BUX1.1_B01",
                    odometerKmBucket = 40_000,
                    theme = "Default",
                    themeChanged = false,
                    powerOnCount = 3L,
                    virtualClusterEnabled = true,
                    settings = mapOf("enable_hot_router" to true),
                    appVersion = "1.2.3",
                    versionCode = 42
            )
}
