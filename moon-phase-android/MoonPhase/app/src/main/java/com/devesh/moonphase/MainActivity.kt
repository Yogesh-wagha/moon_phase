package com.devesh.moonphase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private val Ink = Color(0xFF080B14)
private val Panel = Color(0xFF121828)
private val Hairline = Color(0xFF232B42)
private val Bone = Color(0xFFF2EFE6)
private val Muted = Color(0xFF7C88A6)
private val Amber = Color(0xFFE8B463)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel)) {
                MoonScreen()
            }
        }
    }
}

@Composable
fun MoonScreen() {
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    // Today gets the live sky; other days are evaluated at local noon.
    val instant = if (selected == today) Instant.now() else MoonCalc.noonOf(selected)
    val info = MoonCalc.info(instant)
    val events = MoonCalc.events(instant)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(),
                    color = Muted,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())),
                    color = Bone,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (selected != today || month != YearMonth.from(today)) {
                Text(
                    "Today",
                    color = Amber,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Amber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable {
                            selected = today
                            month = YearMonth.from(today)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Hero disc
        MoonDisc(
            illumination = info.illumination,
            waxing = info.waxing,
            detail = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
        )

        Text(
            "${info.illuminationPercent.roundToInt()}%",
            color = Bone,
            fontSize = 52.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            info.phaseName.uppercase(),
            color = Amber,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Readout("Illuminated", String.format(Locale.getDefault(), "%.1f %%", info.illuminationPercent))
            Readout("Age", String.format(Locale.getDefault(), "%.1f d", events.ageDays))
            Readout("Distance", String.format(Locale.getDefault(), "%,.0f km", info.distanceKm))
            Readout("Apparent size", String.format(Locale.getDefault(), "%.1f ′", info.angularDiameterDeg * 60))
            Readout("Elongation", String.format(Locale.getDefault(), "%.1f °", info.elongationDeg))
            Readout("Next new", formatEvent(events.nextNewMoon))
            Readout("Next full", formatEvent(events.nextFullMoon), last = true)
        }

        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Chevron("‹") { month = month.minusMonths(1) }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                color = Bone,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Chevron("›") { month = month.plusMonths(1) }
        }

        Spacer(Modifier.height(10.dp))
        MonthGrid(month = month, selected = selected, today = today) { selected = it }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Chevron(glyph: String, onClick: () -> Unit) {
    Text(
        glyph,
        color = Muted,
        fontSize = 26.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@Composable
private fun Readout(label: String, value: String, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Bone, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
    if (!last) HorizontalDivider(color = Hairline, thickness = 1.dp)
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7 // week starts Monday
    val length = month.lengthOfMonth()
    val cells = ((lead + length + 6) / 7) * 7

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    color = Muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        for (row in 0 until cells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val dayNumber = index - lead + 1
                    if (dayNumber < 1 || dayNumber > length) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isSelected = date == selected,
                            isToday = date == today,
                            modifier = Modifier.weight(1f),
                            onSelect = onSelect
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onSelect: (LocalDate) -> Unit
) {
    val info = remember(date) { MoonCalc.info(date) }
    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Amber.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onSelect(date) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MoonDisc(
            illumination = info.illumination,
            waxing = info.waxing,
            detail = false,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            date.dayOfMonth.toString(),
            color = if (isToday) Amber else if (isSelected) Bone else Muted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun MoonDisc(
    illumination: Double,
    waxing: Boolean,
    detail: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            val shorter = min(size.width, size.height)
            val radius = shorter / 2f * if (detail) 0.74f else 0.98f
            MoonGraphics.draw(
                canvas.nativeCanvas,
                size.width / 2f,
                size.height / 2f,
                radius,
                illumination,
                waxing,
                detail
            )
        }
    }
}

private fun formatEvent(instant: Instant): String =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
