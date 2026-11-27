package com.sagapmo.app.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CellularSignalTest {
    @Test fun activeSubscriptionProvidesNativeSignalAndDisplayWithoutPhonePermission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val subscription = SubscriptionManager.getActiveDataSubscriptionId()
        assumeTrue(SubscriptionManager.isValidSubscriptionId(subscription))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE))
        val telephony = context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(subscription)
        val (signal, display) = withTimeout(3000) { telephony.readMobileSignal() }
        assertNotNull(signal)
        assertNotNull(display)
        val details = requireNotNull(signal).cellularDetails("Test network", requireNotNull(display).networkType)
        assertEquals("Test network", details?.carrierName)
        assertEquals(display.networkType, details?.networkType)
        assertTrue(signal.timestampMillis >= 0)
    }
}
