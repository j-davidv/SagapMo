package com.sagapmo.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import com.sagapmo.app.AppUiState
import com.sagapmo.app.reading.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class AppSymbol(val data: String) {
    WIFI("M3,8.5 A14,14 0,0 1,21 8.5 M6,12 A9,9 0,0 1,18 12 M9,15.5 A4,4 0,0 1,15 15.5 M11,19 L13,19"),
    MOBILE("M4,20 L4,17 M9,20 L9,13 M14,20 L14,9 M19,20 L19,4"),
    MAP("M3,5 L9,3 L15,5 L21,3 L21,19 L15,21 L9,19 L3,21 Z M9,3 L9,19 M15,5 L15,21"),
    LIST("M8,6 L21,6 M8,12 L21,12 M8,18 L21,18 M3,6 L4,6 M3,12 L4,12 M3,18 L4,18"),
    PIN("M19,10 C19,15 12,21 12,21 C12,21 5,15 5,10 A7,7 0,1 1,19 10 Z M14.5,10 A2.5,2.5 0,1 1,9.5 10 A2.5,2.5 0,1 1,14.5 10"),
    NO_PIN("M17,16 C15,19 12,21 12,21 C12,21 5,15 5,10 A7,7 0,0 1,7 5 M11,3 A7,7 0,0 1,19 10 L19,11 M3,3 L21,21"),
    RECORD("M20.5,12 A8.5,8.5 0,1 1,3.5 12 A8.5,8.5 0,1 1,20.5 12 M15.5,12 A3.5,3.5 0,1 1,8.5 12 A3.5,3.5 0,1 1,15.5 12"),
    INFO("M21,12 A9,9 0,1 1,3 12 A9,9 0,1 1,21 12 M12,11 L12,17 M12,7 L12,7.5"),
    CLOSE("M6,6 L18,18 M6,18 L18,6"),
    TARGET("M12,2 L12,5 M12,19 L12,22 M2,12 L5,12 M19,12 L22,12 M19,12 A7,7 0,1 1,5 12 A7,7 0,1 1,19 12 M14.5,12 A2.5,2.5 0,1 1,9.5 12 A2.5,2.5 0,1 1,14.5 12"),
    CHECK("M5,12 L9,16 L19,6"),
}
@Composable fun AppIcon(symbol: AppSymbol, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val vector = remember(symbol) {
        ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(symbol.data).toNodes(), fill = null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    Icon(vector, description, modifier.size(24.dp), tint)
}
fun ConnectionType.label() = if (this == ConnectionType.WIFI) "Wi-Fi" else "Mobile"
fun ConnectionType.symbol() = if (this == ConnectionType.WIFI) AppSymbol.WIFI else AppSymbol.MOBILE
fun Rating.label() = name.lowercase().replaceFirstChar { it.uppercase() }
fun Reading.rawDisplay() = if (connectionType == ConnectionType.WIFI) "$rawSignalValue dBm" else "Level $rawSignalValue/4"
fun Reading.fullTime(): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.MEDIUM)
    .format(Instant.ofEpochMilli(capturedAtEpochMillis).atZone(ZoneId.systemDefault()))
fun Reading.shortTime(): String {
    val time = Instant.ofEpochMilli(capturedAtEpochMillis).atZone(ZoneId.systemDefault())
    val date = if (time.toLocalDate() == LocalDate.now()) "Today" else time.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    return "$date · ${time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
}
fun NoLocationReason.explanation() = when (this) {
    NoLocationReason.PERMISSION_DENIED -> "Location permission unavailable. Saved without a pin."
    NoLocationReason.APPROXIMATE_ONLY -> "Precise location is needed for a campus pin. Saved without a pin."
    NoLocationReason.LOCATION_DISABLED -> "Location services are off. Saved without a pin."
    NoLocationReason.LOCATION_UNAVAILABLE -> "Location unavailable. Saved without a pin."
    NoLocationReason.LOCATION_STALE -> "Location was too old. Saved without a pin."
    NoLocationReason.LOCATION_INACCURATE -> "Location was too imprecise. Saved without a pin."
    NoLocationReason.LOCATION_TIMEOUT -> "Location timed out. Saved without a pin."
}
@Composable fun ratingPair(rating: Rating): Pair<Color, Color> = LocalRatingColors.current.let {
    when (rating) { Rating.GOOD -> it.good to it.goodBg; Rating.FAIR -> it.fair to it.fairBg; Rating.POOR -> it.poor to it.poorBg }
}
@Composable fun RatingLabel(rating: Rating) {
    val (ink, background) = ratingPair(rating)
    Surface(color = background, contentColor = ink, shape = RoundedCornerShape(5.dp)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).background(ink, RoundedCornerShape(3.dp)))
            Text(rating.label(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun Filters(state: AppUiState, onFilter: (ConnectionType?) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val chipWidth = (maxWidth - 16.dp) / 3
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, ConnectionType.WIFI, ConnectionType.MOBILE).forEach { type ->
                FilterChip(selected = state.filter == type, onClick = { onFilter(type) },
                    modifier = Modifier.widthIn(min = chipWidth).heightIn(min = 48.dp).testTag("filter-${type?.name ?: "ALL"}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        containerColor = MaterialTheme.colorScheme.surface, labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary, selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary),
                    border = BorderStroke(1.dp, if (state.filter == type) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline),
                    leadingIcon = type?.let { { AppIcon(it.symbol(), modifier = Modifier.size(18.dp)) } },
                    label = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(type?.label() ?: "All", style = MaterialTheme.typography.labelMedium)
                            Text(state.allReadings.count { type == null || it.connectionType == type }.toString(), style = MaterialTheme.typography.bodySmall)
                        }
                    })
            }
        }
    }
}
@Composable fun SignalValue(reading: Reading) {
    Text(buildAnnotatedString {
        if (reading.connectionType == ConnectionType.WIFI) {
            append(reading.rawSignalValue.toString())
            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)) { append(" dBm") }
        } else append(reading.rawDisplay())
    }, style = MaterialTheme.typography.headlineLarge)
}
@Composable fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true, symbol: AppSymbol? = null) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp, 12.dp)) {
        if (symbol != null) { AppIcon(symbol); Spacer(Modifier.width(10.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
@Composable fun EmptyState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) OutlinedButton(onAction, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) { Text(action) }
    }
}
