package com.sagapmo.app.reading

data class CellularDetails(
    val carrierName: String? = null,
    val networkType: Int? = null,
    val lteRsrpDbm: Int? = null,
    val lteRsrqDb: Int? = null,
    val lteRssnrDb: Int? = null,
    val lteRssiDbm: Int? = null,
    val nrSsRsrpDbm: Int? = null,
    val nrSsRsrqDb: Int? = null,
    val nrSsSinrDb: Int? = null,
)

fun normalizeCellularDetails(details: CellularDetails): CellularDetails? = details.copy(
    carrierName = details.carrierName?.trim()?.take(100)?.takeIf { it.isNotBlank() },
    networkType = details.networkType?.takeIf { it in 1..20 && it != 18 },
    lteRsrpDbm = details.lteRsrpDbm?.takeIf { it in -140..-43 },
    lteRsrqDb = details.lteRsrqDb?.takeIf { it in -34..3 },
    lteRssnrDb = details.lteRssnrDb?.takeIf { it in -20..30 },
    lteRssiDbm = details.lteRssiDbm?.takeIf { it in -113..-51 },
    nrSsRsrpDbm = details.nrSsRsrpDbm?.takeIf { it in -156..-31 },
    nrSsRsrqDb = details.nrSsRsrqDb?.takeIf { it in -43..20 },
    nrSsSinrDb = details.nrSsSinrDb?.takeIf { it in -23..40 },
).takeUnless { it == CellularDetails() }
