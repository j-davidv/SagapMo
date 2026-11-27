package com.sagapmo.app.capture

import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import com.sagapmo.app.reading.CellularDetails
import com.sagapmo.app.reading.normalizeCellularDetails

internal fun SignalStrength.cellularDetails(carrierName: String?, networkType: Int?): CellularDetails? {
    val components = cellSignalStrengths
    val lte = components.filterIsInstance<CellSignalStrengthLte>().firstOrNull()
    val nr = components.filterIsInstance<CellSignalStrengthNr>().firstOrNull()
    return normalizeCellularDetails(CellularDetails(
        carrierName = carrierName, networkType = networkType,
        lteRsrpDbm = lte?.rsrp, lteRsrqDb = lte?.rsrq,
        lteRssnrDb = lte?.rssnr, lteRssiDbm = lte?.rssi,
        nrSsRsrpDbm = nr?.ssRsrp, nrSsRsrqDb = nr?.ssRsrq, nrSsSinrDb = nr?.ssSinr,
    ))
}
