package com.rady.worktracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rady.worktracker.data.Bucket
import com.rady.worktracker.data.formatAxisHours
import com.rady.worktracker.data.niceAxisMaxHours

/** Ring chart: one slice per project, with small gaps between slices. */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Long>>,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = {},
) {
    val track = P.slabRaised
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.13f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            val total = slices.sumOf { it.second }
            if (total <= 0L) {
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (slices.size > 1) 2f else 0f
            var angle = -90f
            for ((color, value) in slices) {
                val sweep = 360f * value.toFloat() / total.toFloat()
                if (sweep > gap) drawArc(color, angle + gap / 2f, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke))
                angle += sweep
            }
        }
        center()
    }
}

/**
 * Bars stacked by project. [order] decides which project sits at the bottom
 * so colors line up across bars. A dashed line marks the average bar.
 */
@Composable
fun StackedBarChart(
    buckets: List<Bucket>,
    order: List<Long>,
    colorOf: (Long) -> Color,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
    averageMs: Long? = null,
) {
    val maxMs = buckets.maxOfOrNull { it.total } ?: 0L
    val axisHours = niceAxisMaxHours(maxMs.coerceAtLeast(1L))
    val axisMs = (axisHours * 3_600_000.0).toFloat()
    val grid = P.rule
    val avgColor = P.info
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = P.inkMuted
    val axisWidth = 36.dp

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            Column(Modifier.width(axisWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(formatAxisHours(axisHours), style = labelStyle, color = labelColor)
                Text(formatAxisHours(axisHours / 2.0), style = labelStyle, color = labelColor)
                Text("0", style = labelStyle, color = labelColor)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val h = size.height
                val line = 1.dp.toPx()
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = (h * f).coerceIn(line / 2f, h - line / 2f)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = line)
                }
                val n = buckets.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = (slot * 0.62f).coerceAtMost(26.dp.toPx())
                val radius = CornerRadius(minOf(barW / 2f, 4.dp.toPx()))
                buckets.forEachIndexed { i, b ->
                    val x = i * slot + (slot - barW) / 2f
                    var top = h
                    val ids = order.filter { b.perProject.containsKey(it) } + b.perProject.keys.filter { it !in order }
                    ids.forEachIndexed { j, pid ->
                        val bh = h * ((b.perProject[pid] ?: 0L).toFloat() / axisMs)
                        if (bh > 0f) {
                            if (j == ids.lastIndex) {
                                drawRoundRect(colorOf(pid), Offset(x, top - bh), Size(barW, bh), radius)
                                if (bh > radius.y) drawRect(colorOf(pid), Offset(x, top - bh / 2f), Size(barW, bh / 2f))
                            } else {
                                drawRect(colorOf(pid), Offset(x, top - bh), Size(barW, bh))
                            }
                            top -= bh
                        }
                    }
                }
                if (averageMs != null && averageMs > 0) {
                    val y = h - h * (averageMs.toFloat() / axisMs)
                    drawLine(avgColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = axisWidth, top = 4.dp)) {
            buckets.forEachIndexed { i, b ->
                Text(
                    if (i % labelEvery == 0) b.label else "",
                    style = labelStyle, color = labelColor, textAlign = TextAlign.Center,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
