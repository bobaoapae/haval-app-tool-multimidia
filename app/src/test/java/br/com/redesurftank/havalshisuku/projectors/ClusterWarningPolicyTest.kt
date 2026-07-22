package br.com.redesurftank.havalshisuku.projectors

import br.com.redesurftank.havalshisuku.models.CarConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterWarningPolicyTest {
    @Test
    fun activeValuesAreRecognized() {
        assertTrue(ClusterWarningPolicy.isWarningValueActive("1121"))
        assertTrue(ClusterWarningPolicy.isWarningValueActive("1"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("0"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("{0,0,0,0}"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("{0,0,0,0,0}"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("false"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive(""))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("null"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("undefined"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("unknown"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("--"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive("NaN"))
        assertFalse(ClusterWarningPolicy.isWarningValueActive(null))
    }

    @Test
    fun visualOnlyWarningKeysDoNotTriggerCriticalWarningFlow() {
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value,
                        "1121"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_IPK_LIGHT_FUEL_LOW.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value,
                        "1"
                )
        )
    }

    @Test
    fun criticalWarningKeysTriggerCriticalWarningFlowOnlyWhenActive() {
        assertTrue(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                        "1"
                )
        )
        assertFalse(
                ClusterWarningPolicy.shouldTriggerCriticalWarningFlow(
                        CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                        "0"
                )
        )
    }
}
