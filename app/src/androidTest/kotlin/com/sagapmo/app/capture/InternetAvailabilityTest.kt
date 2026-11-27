package com.sagapmo.app.capture

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

class InternetAvailabilityTest {
    @Test fun observesInternetLossAndRecoveryOnEmulator() = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("sdk"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() }
        }
        val wifiEnabled = shell("settings get global wifi_on") == "1"
        val dataEnabled = shell("settings get global mobile_data") == "1"
        val events = Channel<Boolean>(Channel.UNLIMITED)
        val observer = launch {
            observeInternetAvailability(instrumentation.targetContext).collect { events.send(it) }
        }
        suspend fun awaitAvailability(expected: Boolean) = withTimeout(30_000) {
            while (events.receive() != expected) { }
        }
        try {
            shell("svc wifi enable")
            shell("svc data enable")
            awaitAvailability(true)
            shell("svc wifi disable")
            shell("svc data disable")
            awaitAvailability(false)
            shell("svc wifi enable")
            awaitAvailability(true)
        } finally {
            observer.cancelAndJoin()
            shell("svc wifi ${if (wifiEnabled) "enable" else "disable"}")
            shell("svc data ${if (dataEnabled) "enable" else "disable"}")
        }
    }
}
