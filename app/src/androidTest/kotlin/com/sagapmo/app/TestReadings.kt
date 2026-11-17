package com.sagapmo.app

import com.sagapmo.app.reading.*

fun sampleReading(id: String = "test-wifi") = Reading(id, 1_794_883_320_000,
    ConnectionType.WIFI, -62, SignalUnit.DBM, Rating.GOOD, null,
    13.78422, 121.07431, 8f, 5_000, null)

fun mobileReading(id: String = "test-mobile") = Reading(id, 1_794_882_900_000,
    ConnectionType.MOBILE, 2, SignalUnit.ANDROID_LEVEL, Rating.FAIR, 12_000,
    null, null, null, null, NoLocationReason.APPROXIMATE_ONLY)
