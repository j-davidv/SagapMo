package com.sagapmo.app.capture

import android.telephony.TelephonyDisplayInfo.*
import android.telephony.TelephonyManager.*
import com.sagapmo.app.reading.MobileGeneration
import org.junit.Assert.*
import org.junit.Test

@Suppress("DEPRECATION")
class MobileGenerationTest {
    @Test fun lteAndCarrierAggregationAre4g() {
        listOf(OVERRIDE_NETWORK_TYPE_NONE, OVERRIDE_NETWORK_TYPE_LTE_CA,
            OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO).forEach {
            assertEquals(MobileGeneration.FOUR_G, mobileGeneration(NETWORK_TYPE_LTE, it))
        }
    }

    @Test fun standaloneAndLteBased5gAreRecognized() {
        assertEquals(MobileGeneration.FIVE_G, mobileGeneration(NETWORK_TYPE_NR, OVERRIDE_NETWORK_TYPE_NONE))
        listOf(OVERRIDE_NETWORK_TYPE_NR_NSA, OVERRIDE_NETWORK_TYPE_NR_ADVANCED).forEach {
            assertEquals(MobileGeneration.FIVE_G, mobileGeneration(NETWORK_TYPE_LTE, it))
        }
    }

    @Test fun olderNetworksAreNotMislabeledAs4g() {
        listOf(NETWORK_TYPE_GPRS, NETWORK_TYPE_EDGE, NETWORK_TYPE_CDMA, NETWORK_TYPE_1xRTT,
            NETWORK_TYPE_IDEN, NETWORK_TYPE_GSM).forEach {
            assertEquals(MobileGeneration.TWO_G, mobileGeneration(it, OVERRIDE_NETWORK_TYPE_NONE))
        }
        listOf(NETWORK_TYPE_UMTS, NETWORK_TYPE_EVDO_0, NETWORK_TYPE_EVDO_A, NETWORK_TYPE_EVDO_B,
            NETWORK_TYPE_HSDPA, NETWORK_TYPE_HSUPA, NETWORK_TYPE_HSPA, NETWORK_TYPE_EHRPD,
            NETWORK_TYPE_HSPAP, NETWORK_TYPE_TD_SCDMA).forEach {
            assertEquals(MobileGeneration.THREE_G, mobileGeneration(it, OVERRIDE_NETWORK_TYPE_NONE))
        }
    }

    @Test fun unknownAndNonCellularTypesStayUnknown() {
        listOf(NETWORK_TYPE_UNKNOWN, NETWORK_TYPE_IWLAN, -1, 999).forEach {
            assertNull(mobileGeneration(it, OVERRIDE_NETWORK_TYPE_NONE))
        }
    }
}
