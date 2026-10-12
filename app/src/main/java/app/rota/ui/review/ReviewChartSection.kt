package app.rota.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import app.rota.domain.ChartNumberField
import app.rota.domain.ChartPoint
import app.rota.domain.ChartStyle
import app.rota.domain.ChartTextField
import app.rota.domain.ReviewCharts
import app.rota.domain.ReviewEvent
import app.rota.ui.components.ChipRow
import app.rota.ui.components.SectionLabel

@Composable
fun ReviewChartSection(events: List<ReviewEvent>) {
    var open by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }
    var textField by remember { mutableStateOf(ChartTextField.HABIT) }
    var numberField by remember { mutableStateOf(ChartNumberField.COUNT) }
    var style by remember { mutableStateOf(ChartStyle.BAR) }
    val points = remember(events, textField, numberField, style, requested) {
        if (!requested) emptyList() else ReviewCharts.present(events, textField, numberField, style)
    }
    val caption = if (requested) {
        "${style.label} · ${textField.label} · ${numberField.label}"
    } else {
        "Hidden"
    }

    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Chart")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Text and number", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Collapse chart" else "Expand chart",
            )
        }
        if (!open) return@Column

        Text(
            "Pick one text field and one number field. Nothing is drawn until you show the chart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("Text", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(
            options = ChartTextField.entries.map { it.label to (textField == it) },
            onSelect = { textField = ChartTextField.entries[it] },
        )
        Spacer(Modifier.height(8.dp))
        Text("Number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(
            options = ChartNumberField.entries.map { it.label to (numberField == it) },
            onSelect = { numberField = ChartNumberField.entries[it] },
        )
        Spacer(Modifier.height(8.dp))
        Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(
            options = ChartStyle.entries.map { it.label to (style == it) },
            onSelect = { style = ChartStyle.entries[it] },
        )
        Spacer(Modifier.height(12.dp))
        if (!requested) {
            OutlinedButton(onClick = { requested = true }) { Text("Show chart") }
        } else {
            SimpleChart(points = points, style = style)
            TextButton(onClick = { requested = false }) { Text("Hide chart") }
        }
    }
}

@Composable
private fun SimpleChart(points: List<ChartPoint>, style: ChartStyle) {
    if (points.isEmpty()) {
        Text(
            "Nothing to chart for these filters.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    val description = points.joinToString { "${it.label} ${ReviewCharts.formatNumber(it.value)}" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (style) {
            ChartStyle.BAR -> BarChart(points)
            ChartStyle.LINE -> LineChart(points)
        }
    }
}

@Composable
private fun BarChart(points: List<ChartPoint>) {
    val max = points.maxOf { it.value }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    point.label,
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val fraction = (point.value / max).toFloat()
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction.coerceAtMost(1f))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    ReviewCharts.formatNumber(point.value),
                    modifier = Modifier.width(40.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LineChart(points: List<ChartPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val max = points.maxOf { it.value }.coerceAtLeast(1.0)
        val left = 36.dp.toPx()
        val bottom = 28.dp.toPx()
        val top = 16.dp.toPx()
        val right = 8.dp.toPx()
        val plotWidth = (size.width - left - right).coerceAtLeast(1f)
        val plotHeight = (size.height - top - bottom).coerceAtLeast(1f)
        drawLine(gridColor, Offset(left, top), Offset(left + plotWidth, top), strokeWidth = 1f)
        drawLine(
            gridColor,
            Offset(left, top + plotHeight),
            Offset(left + plotWidth, top + plotHeight),
            strokeWidth = 1f,
        )
        drawText(
            textMeasurer,
            ReviewCharts.formatNumber(max),
            Offset(0f, 0f),
            labelStyle,
            maxLines = 1,
        )
        fun xOf(index: Int): Float = if (points.size == 1) {
            left + plotWidth / 2f
        } else {
            left + plotWidth * index / (points.size - 1)
        }
        fun yOf(value: Double): Float = top + plotHeight * (1f - (value / max).toFloat())
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xOf(index)
            val y = yOf(point.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEachIndexed { index, point ->
            val x = xOf(index)
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, yOf(point.value)))
            if (points.size <= 6 || index == 0 || index == points.lastIndex) {
                val label = if (point.label.length <= 10) point.label else point.label.take(9) + "…"
                val layout = textMeasurer.measure(label, labelStyle)
                val rawX = x - layout.size.width / 2f
                val textX = rawX.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(
                    textMeasurer,
                    label,
                    Offset(textX, size.height - layout.size.height),
                    labelStyle,
                )
            }
        }
    }
}
